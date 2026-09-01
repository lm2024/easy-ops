package com.ops.agent.filemgr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

/**
 * 压缩下载任务管理器（Agent 侧）。
 * <ul>
 *   <li>并发：压缩任务固定并发=1（Semaphore），防磁盘 IO 争抢；用户 CPU 不设节流。</li>
 *   <li>取消：AtomicBoolean 标志 + 8KB chunk 边界检查，秒级响应，取消即删半成品。</li>
 *   <li>TTL：产物 3 小时自动删除（@Scheduled 每 5 分钟扫描），不占服务器空间。</li>
 * </ul>
 */
@Component
public class DownloadTaskManager {

    private static final Logger log = LoggerFactory.getLogger(DownloadTaskManager.class);
    private static final String CANCEL_MSG = "任务已取消";

    private final Map<String, DownloadTask> tasks = new ConcurrentHashMap<>();
    private final Semaphore permits = new Semaphore(1);

    @Value("${agent.data-path:/app/data}")
    private String dataPath;

    @Value("${agent.filemgr.part-size-mb:300}")
    private long partSizeMb;

    @Value("${agent.filemgr.ttl-hours:3}")
    private int ttlHours;

    @Value("${agent.filemgr.roots:/app/data}")
    private String rootsCfg;

    private long partSize;
    private File downloadsDir;
    private List<File> roots = new ArrayList<>();

    @PostConstruct
    public void init() {
        partSize = Math.max(1, partSizeMb) * 1024L * 1024L;
        downloadsDir = new File(dataPath, "downloads");
        if (!downloadsDir.exists() && !downloadsDir.mkdirs()) {
            log.warn("无法创建下载目录: {}", downloadsDir.getAbsolutePath());
        }
        for (String r : rootsCfg.split(",")) {
            String t = r.trim();
            if (!t.isEmpty()) {
                roots.add(new File(t));
            }
        }
        log.info("下载任务管理器初始化 分卷{}MB TTL{}小时 roots={}", partSizeMb, ttlHours, rootsCfg);
    }

    // ==================== 路径安全 ====================

    /** 路径规范化后必须以某个可浏览根目录为前缀，防路径穿越/任意文件访问。 */
    public boolean isAllowed(String path) {
        try {
            File f = new File(path).getCanonicalFile();
            String p = f.getPath();
            for (File root : roots) {
                String r = root.getCanonicalPath();
                if (p.equals(r) || p.startsWith(r + File.separator)) {
                    return true;
                }
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    /** 校验并返回规范化文件；非法返回 null。 */
    public File resolve(String path) {
        if (path == null || path.trim().isEmpty() || !isAllowed(path)) {
            return null;
        }
        try {
            return new File(path).getCanonicalFile();
        } catch (IOException e) {
            return null;
        }
    }

    public List<String> getRoots() {
        return roots.stream().map(File::getAbsolutePath).collect(Collectors.toList());
    }

    // ==================== 任务创建 ====================

    public DownloadTask create(List<String> paths, String baseName) throws IOException {
        if (paths == null || paths.isEmpty()) {
            throw new IOException("下载路径不能为空");
        }
        List<String> safePaths = new ArrayList<>();
        long total = 0;
        for (String p : paths) {
            File f = resolve(p);
            if (f == null || !f.exists()) {
                throw new IOException("文件不存在或不在可访问范围: " + p);
            }
            safePaths.add(f.getAbsolutePath());
            total += estimateSize(f);
        }
        if (baseName == null || baseName.trim().isEmpty()) {
            baseName = "download";
        }
        baseName = sanitizeBaseName(baseName);
        String id = UUID.randomUUID().toString().replace("-", "");
        DownloadTask t = new DownloadTask(id, baseName, safePaths);
        t.setTotalSize(total);
        tasks.put(id, t);
        log.info("创建下载任务 id={} name={} paths={} 估算大小={}B", id, baseName, safePaths.size(), total);
        startWorker(t);
        return t;
    }

    /** 递归估算大小（目录累加，文件取 length，速度 O(节点数)）。 */
    private long estimateSize(File f) {
        if (f.isFile()) {
            return f.length();
        }
        File[] children = f.listFiles();
        if (children == null) {
            return 0;
        }
        long sum = 0;
        for (File c : children) {
            sum += estimateSize(c);
        }
        return sum;
    }

    /** 移除路径分隔符等非法字符，作为产物基名。 */
    private String sanitizeBaseName(String name) {
        String n = name.replaceAll("[\\\\/:*?\"<>|\\s]+", "_").trim();
        return n.isEmpty() ? "download" : n;
    }

    // ==================== 任务操作 ====================

    public DownloadTask get(String id) {
        return tasks.get(id);
    }

    public List<DownloadTask> list() {
        List<DownloadTask> list = new ArrayList<>(tasks.values());
        list.sort((a, b) -> Long.compare(b.getCreateTime(), a.getCreateTime()));
        return list;
    }

    /** 取消任务：置标志，worker 在 chunk 边界中断并清理。 */
    public boolean cancel(String id) {
        DownloadTask t = tasks.get(id);
        if (t == null) {
            return false;
        }
        if (t.getCancelled().getAndSet(true)) {
            return true; // 幂等
        }
        // PENDING 且未开始：直接置取消，worker 启动时检测后清理
        if (!DownloadTask.COMPRESSING.equals(t.getStatus())) {
            t.setStatus(DownloadTask.CANCELLED);
        }
        log.info("请求取消任务 id={}", id);
        return true;
    }

    /** 删除任务记录与产物（无论状态），立即释放空间。 */
    public boolean delete(String id) {
        DownloadTask t = tasks.remove(id);
        if (t == null) {
            return false;
        }
        if (DownloadTask.COMPRESSING.equals(t.getStatus())) {
            t.getCancelled().set(true); // 让压缩线程退出后自行清理
        }
        deleteTaskDir(t);
        log.info("删除下载任务 id={}", id);
        return true;
    }

    /** 任务产物目录。 */
    public File taskDir(DownloadTask t) {
        return new File(downloadsDir, t.getId());
    }

    private void deleteTaskDir(DownloadTask t) {
        File dir = taskDir(t);
        if (dir.exists()) {
            deleteRecursively(dir);
        }
    }

    private void deleteRecursively(File f) {
        if (f.isDirectory()) {
            File[] ch = f.listFiles();
            if (ch != null) {
                for (File c : ch) {
                    deleteRecursively(c);
                }
            }
        }
        f.delete();
    }

    // ==================== 压缩 worker ====================

    private void startWorker(DownloadTask t) {
        Thread w = new Thread(() -> {
            try {
                permits.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            try {
                if (t.getCancelled().get()) {
                    t.setStatus(DownloadTask.CANCELLED);
                    deleteTaskDir(t);
                    return;
                }
                compress(t);
            } catch (DownloadCancelledException e) {
                t.setStatus(DownloadTask.CANCELLED);
                t.setMessage(CANCEL_MSG);
                deleteTaskDir(t);
            } catch (Exception e) {
                t.setStatus(DownloadTask.FAILED);
                t.setMessage(e.getMessage());
                deleteTaskDir(t);
                log.error("压缩任务失败 id={} {}", t.getId(), e.getMessage());
            } finally {
                permits.release();
                if (!DownloadTask.CANCELLED.equals(t.getStatus())
                        && !DownloadTask.FAILED.equals(t.getStatus())
                        && !DownloadTask.READY.equals(t.getStatus())) {
                    // 兜底：异常未覆盖则清理
                    deleteTaskDir(t);
                }
            }
        }, "download-compress-" + t.getId().substring(0, 8));
        w.setDaemon(true);
        w.start();
    }

    private void compress(DownloadTask t) throws IOException {
        t.setStatus(DownloadTask.COMPRESSING);
        File dir = taskDir(t);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("无法创建任务目录: " + dir.getAbsolutePath());
        }
        String base = new File(dir, t.getName()).getAbsolutePath(); // 不含 .zip，分卷命名由 SplitZipWriter 生成 .z01/.../.zip
        SplitZipWriter w = new SplitZipWriter(base, partSize);
        try {
            for (String p : t.getPaths()) {
                File f = new File(p);
                String entryName = f.getName();
                addToZip(w, f, t, entryName);
            }
        } finally {
            w.close();
        }
        // 必须在 close() 之后取分卷：SplitZipWriter.close() 会把末卷 .z01 重命名为 .zip，
        // 若在 close 前取，parts 记录的 .z01 已被重命名不存在，导致下载 404/0 字节。
        List<File> produced = w.getParts();
        t.getParts().clear();
        t.getParts().addAll(produced);
        t.setStatus(DownloadTask.READY);
        t.setMessage("共" + t.getParts().size() + "个分卷");
        log.info("任务完成 id={} 分卷数={}", t.getId(), t.getParts().size());
    }

    private void addToZip(SplitZipWriter w, File f, DownloadTask t, String entryName) throws IOException {
        if (t.getCancelled().get()) {
            throw new DownloadCancelledException(CANCEL_MSG);
        }
        if (f.isDirectory()) {
            w.beginEntry(entryName + "/", f.lastModified());
            w.finishEntry();
            File[] ch = f.listFiles();
            if (ch != null) {
                for (File c : ch) {
                    addToZip(w, c, t, entryName + "/" + c.getName());
                }
            }
            return;
        }
        w.beginEntry(entryName, f.lastModified());
        byte[] buf = new byte[8192];
        try (InputStream in = new FileInputStream(f)) {
            int n;
            while ((n = in.read(buf)) != -1) {
                if (t.getCancelled().get()) {
                    throw new DownloadCancelledException(CANCEL_MSG);
                }
                w.writeData(buf, n);
                t.setProcessedBytes(t.getProcessedBytes() + n);
            }
        }
        w.finishEntry();
    }

    // ==================== TTL 清理 ====================

    /**
     * 每 5 分钟清理 3 小时前的压缩产物与任务记录，保证服务端不占空间。
     * 正在压缩（COMPRESSING）的任务跳过（产物可能还在写入）。
     */
    @Scheduled(fixedDelay = 300000)
    public void cleanupExpired() {
        long cutoff = System.currentTimeMillis() - Math.max(1, ttlHours) * 3600L * 1000L;
        try {
            List<DownloadTask> expired = tasks.values().stream()
                    .filter(t -> t.getCreateTime() < cutoff
                            && !DownloadTask.COMPRESSING.equals(t.getStatus()))
                    .collect(Collectors.toList());
            for (DownloadTask t : expired) {
                tasks.remove(t.getId());
                deleteTaskDir(t);
                log.info("TTL清理下载任务 id={} name={}", t.getId(), t.getName());
            }
            // 兜底：扫描目录里未被任务表引用的孤儿目录
            File[] dirs = downloadsDir.listFiles(File::isDirectory);
            if (dirs != null) {
                for (File d : dirs) {
                    try {
                        if (Files.getLastModifiedTime(d.toPath()).toMillis() < cutoff) {
                            deleteRecursively(d);
                        }
                    } catch (IOException e) {
                        log.warn("孤儿目录清理失败: {}", d.getAbsolutePath());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("TTL清理异常: {}", e.getMessage());
        }
    }

    /** 取消异常（压缩线程内部使用）。 */
    private static class DownloadCancelledException extends IOException {
        DownloadCancelledException(String msg) {
            super(msg);
        }
    }
}
