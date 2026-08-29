package com.ops.agent.arthas;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * Arthas 会话管理器（核心）
 * 管理所有活跃的 Arthas 会话，负责 attach/detach 生命周期、超时清理、并发控制
 */
@Service
public class ArthasSessionManager {
    private static final Logger log = LoggerFactory.getLogger(ArthasSessionManager.class);

    @Autowired
    private ArthasPortAllocator portAllocator;

    @Autowired
    private ArthasBootstrap bootstrap;

    @Autowired
    private ArthasHttpClient httpClient;

    @Value("${agent.arthas.max-concurrent-sessions:2}")
    private int maxConcurrentSessions;

    @Value("${agent.arthas.session-timeout-minutes:60}")
    private int sessionTimeoutMinutes;

    private final ConcurrentHashMap<Long, ArthasSession> sessions = new ConcurrentHashMap<>();

    /**
     * 按 PID 维度的 attach 互斥锁。
     * attach 涉及"启动进程 + 等待端口就绪"，耗时可达十几秒。若不加锁，
     * 同一 PID 的并发请求会各自启动一个 arthas-boot 进程，后完成的覆盖前者，
     * 被覆盖的那个进程和端口将永远无法回收。
     */
    private final ConcurrentHashMap<Long, Object> attachLocks = new ConcurrentHashMap<>();

    private Semaphore semaphore;

    @PostConstruct
    public void init() {
        semaphore = new Semaphore(maxConcurrentSessions);
        log.info("ArthasSessionManager 初始化: maxConcurrent={}, timeoutMin={}", maxConcurrentSessions, sessionTimeoutMinutes);
        // 清理可能的残留进程
        cleanupResidual();
    }

    @PreDestroy
    public void shutdown() {
        log.info("ArthasSessionManager 关闭，开始 detach 所有会话...");
        for (Long pid : new ArrayList<>(sessions.keySet())) {
            try {
                detach(pid);
            } catch (Exception e) {
                log.error("关闭时 detach 失败: pid={}, error={}", pid, e.getMessage());
            }
        }
        log.info("ArthasSessionManager 关闭完成");
    }

    /**
     * attach 到目标 PID。
     * 外层按 PID 串行化，保证同一进程不会被并发 attach 出多个 arthas-boot 进程。
     */
    public ArthasSession attach(long pid, String projectId, String nodeId) {
        Object lock = attachLocks.computeIfAbsent(pid, k -> new Object());
        synchronized (lock) {
            try {
                return doAttach(pid, projectId, nodeId);
            } finally {
                // 会话已进入 sessions（或已彻底失败并释放资源），释放锁对象
                attachLocks.remove(pid, lock);
            }
        }
    }

    /**
     * 执行实际的 attach 流程。调用方必须持有该 PID 的 attach 锁。
     */
    private ArthasSession doAttach(long pid, String projectId, String nodeId) {
        // 并发控制
        if (!semaphore.tryAcquire()) {
            throw new RuntimeException("Arthas 诊断会话已满（最多 " + maxConcurrentSessions + " 个），请稍后重试");
        }

        try {
            // 检查是否已 attach
            if (sessions.containsKey(pid)) {
                ArthasSession existing = sessions.get(pid);
                existing.setLastActiveTime(System.currentTimeMillis());
                log.info("Arthas 会话已存在，直接返回: pid={}", pid);
                semaphore.release(); // 已存在的不占信号量
                return existing;
            }

            // 检查 PID 是否存活
            if (!isPidAlive(pid)) {
                semaphore.release();
                throw new RuntimeException("目标进程不存在或已退出: pid=" + pid);
            }

            // 分配端口
            int port = portAllocator.allocate();

            // 启动 Arthas
            Process process = null;
            try {
                process = bootstrap.start(pid, port);
            } catch (Exception e) {
                portAllocator.release(port);
                semaphore.release();
                throw new RuntimeException("启动 Arthas 失败: " + e.getMessage(), e);
            }

            // 等待 HTTP API 就绪
            boolean ready = bootstrap.waitUntilReady(httpClient, port);
            if (!ready) {
                bootstrap.stop(process);
                portAllocator.release(port);
                semaphore.release();
                throw new RuntimeException("Arthas HTTP API 就绪超时，请检查目标进程是否为 Java 进程");
            }

            // 获取版本
            String version = "unknown";
            try {
                ArthasHttpClient.ArthasResult result = httpClient.exec(port, "version", 5000);
                if (result.isSuccess() && result.getResults() != null && !result.getResults().isEmpty()) {
                    Object first = result.getResults().get(0);
                    if (first instanceof java.util.Map) {
                        Object ver = ((java.util.Map<?, ?>) first).get("version");
                        if (ver != null) version = ver.toString();
                    }
                }
            } catch (Exception e) {
                log.warn("获取 Arthas 版本失败: {}", e.getMessage());
            }

            // 创建会话
            ArthasSession session = new ArthasSession();
            session.setPid(pid);
            session.setPort(port);
            session.setArthasProcess(process);
            session.setArthasVersion(version);
            session.setAttachTime(System.currentTimeMillis());
            session.setLastActiveTime(System.currentTimeMillis());
            session.setProjectId(projectId);
            session.setNodeId(nodeId);
            session.setAttached(true);

            // 获取目标进程的工作目录（用于火焰图历史文件列表）
            String workingDir = getProcessWorkingDir(pid);
            if (workingDir != null) {
                session.setWorkingDir(workingDir);
                log.info("目标进程工作目录: pid={}, dir={}", pid, workingDir);
            }

            sessions.put(pid, session);
            log.info("Arthas attach 成功: pid={}, port={}, version={}", pid, port, version);
            return session;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            semaphore.release();
            throw new RuntimeException("Arthas attach 失败: " + e.getMessage(), e);
        }
    }

    /**
     * detach 指定 PID 的 Arthas 会话
     */
    public void detach(long pid) {
        ArthasSession session = sessions.remove(pid);
        if (session == null) {
            log.debug("Arthas 会话不存在，无需 detach: pid={}", pid);
            return;
        }

        try {
            // 优雅停止：发送 stop 命令
            try {
                httpClient.exec(session.getPort(), "stop", 3000);
            } catch (Exception e) {
                log.debug("发送 stop 命令失败（可能进程已退出）: {}", e.getMessage());
            }
            // 停止进程
            bootstrap.stop(session.getArthasProcess());
        } finally {
            // 释放端口
            portAllocator.release(session.getPort());
            // 释放信号量
            if (semaphore.availablePermits() < maxConcurrentSessions) {
                semaphore.release();
            }
            log.info("Arthas detach 完成: pid={}, port={}", pid, session.getPort());
        }
    }

    /**
     * 执行命令
     */
    public ArthasHttpClient.ArthasResult exec(long pid, String command, int timeoutMs) {
        ArthasSession session = sessions.get(pid);
        if (session == null || !session.isAttached()) {
            throw new RuntimeException("Arthas 会话不存在或已结束: pid=" + pid);
        }
        // 检查 PID 是否还存活
        if (!isPidAlive(pid)) {
            log.warn("目标进程已退出，自动 detach: pid={}", pid);
            detach(pid);
            throw new RuntimeException("目标进程已退出: pid=" + pid);
        }
        session.setLastActiveTime(System.currentTimeMillis());
        return httpClient.exec(session.getPort(), command, timeoutMs);
    }

    /**
     * 获取会话
     */
    public ArthasSession getSession(long pid) {
        return sessions.get(pid);
    }

    /**
     * 获取所有活跃会话
     */
    public List<ArthasSession> getAllSessions() {
        return new ArrayList<>(sessions.values());
    }

    /**
     * 检查 PID 是否存活
     */
    private boolean isPidAlive(long pid) {
        // Linux: 检查 /proc/{pid} 是否存在
        File procDir = new File("/proc/" + pid);
        if (procDir.exists()) {
            return true;
        }
        // Windows: 用 tasklist 检查
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            try {
                Process p = Runtime.getRuntime().exec(new String[]{"tasklist", "/FI", "PID eq " + pid, "/NH"});
                java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(p.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains(String.valueOf(pid))) {
                        reader.close();
                        return true;
                    }
                }
                reader.close();
                return false;
            } catch (Exception e) {
                return false;
            }
        }
        // 回退：用 ps 命令检查（兼容其他 Unix）
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", "ps -p " + pid + " > /dev/null 2>&1"});
            int exit = p.waitFor();
            return exit == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取目标进程的工作目录
     */
    private String getProcessWorkingDir(long pid) {
        // Linux: 读取 /proc/{pid}/cwd 符号链接
        File cwdLink = new File("/proc/" + pid + "/cwd");
        if (cwdLink.exists()) {
            try {
                return cwdLink.getCanonicalPath();
            } catch (Exception e) {
                log.warn("获取进程工作目录失败: pid={}, error={}", pid, e.getMessage());
            }
        }
        return null;
    }

    /**
     * 定时清理超时会话（每30秒检查一次）
     */
    @Scheduled(fixedDelay = 30000)
    public void cleanupTimeoutSessions() {
        long timeoutMs = sessionTimeoutMinutes * 60 * 1000L;
        long now = System.currentTimeMillis();
        for (Map.Entry<Long, ArthasSession> entry : sessions.entrySet()) {
            ArthasSession session = entry.getValue();
            if (now - session.getLastActiveTime() > timeoutMs) {
                log.info("Arthas 会话超时，自动 detach: pid={}, idle={}s",
                        entry.getKey(), (now - session.getLastActiveTime()) / 1000);
                try {
                    detach(entry.getKey());
                } catch (Exception e) {
                    log.error("超时清理 detach 失败: pid={}, error={}", entry.getKey(), e.getMessage());
                }
            }
        }
    }

    /**
     * 清理残留进程（Agent 启动时调用）。
     *
     * <p>只清理 arthas-boot 启动器进程：它仅负责 attach，attach 成功后即退出，
     * 因此残留的一定是 attach 失败的僵尸进程。目标 JVM 内部的 arthas agent
     * 不在此列（它随目标进程存活，且卸载只能走 HTTP API 的 stop 命令）。
     *
     * <p>这里用 JDK 自带的 jps 而不是 ps aux：容器镜像可能没有 procps，
     * 而 jps 随 JDK 分发，且能精确匹配 Java 进程，不会误伤同名的非 Java 进程。
     */
    private void cleanupResidual() {
        java.io.BufferedReader reader = null;
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"jps", "-l"});
            reader = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
            String line;
            int cleaned = 0;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length < 2 || !parts[1].contains("arthas-boot")) {
                    continue;
                }
                String pidStr = parts[0];
                if (pidStr.isEmpty()) {
                    continue;
                }
                log.info("清理残留 Arthas 启动器进程: pid={}", pidStr);
                try {
                    Runtime.getRuntime().exec(new String[]{"kill", "-9", pidStr});
                    cleaned++;
                } catch (Exception e) {
                    log.debug("清理残留进程失败（可能已退出）: pid={}, error={}", pidStr, e.getMessage());
                }
            }
            if (cleaned > 0) {
                log.info("Arthas 残留进程清理完成，共清理 {} 个", cleaned);
            }
        } catch (Exception e) {
            log.debug("清理残留 Arthas 进程跳过（jps 不可用或无残留）: {}", e.getMessage());
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {
                    // 关闭失败无需处理
                }
            }
        }
    }
}
