package com.ops.agent.controller;

import com.ops.agent.filemgr.DownloadTask;
import com.ops.agent.filemgr.DownloadTaskManager;
import com.ops.common.response.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 文件管理接口（FTP 式浏览 / 单文件下载 / 大文件 ZIP 分卷压缩下载 / 上传）。
 * <p>
 * 安全：所有路径必须位于可访问根目录（agent.filemgr.roots，默认 /app/data）内。
 */
@RestController
@RequestMapping("/filemgr")
public class FileMgrController {

    @Autowired
    private DownloadTaskManager taskManager;

    /** 可访问根目录列表。 */
    @GetMapping("/roots")
    public Result<List<String>> roots() {
        return Result.success(taskManager.getRoots());
    }

    /** 列目录（单层）。path 为空则列根目录。 */
    @GetMapping("/list")
    public Result<Map<String, Object>> list(@RequestParam(required = false) String path) {
        File dir = path == null || path.trim().isEmpty()
                ? new File(taskManager.getRoots().get(0))
                : taskManager.resolve(path);
        if (dir == null) {
            return Result.error(400, "路径不在可访问范围内: " + path);
        }
        if (!dir.exists()) {
            return Result.error(400, "目录不存在: " + path);
        }
        if (!dir.isDirectory()) {
            return Result.error(400, "不是目录: " + path);
        }
        File[] children = dir.listFiles();
        List<Map<String, Object>> items = new ArrayList<>();
        if (children != null) {
            for (File c : children) {
                items.add(toItem(c));
            }
            items.sort((a, b) -> {
                boolean ad = (Boolean) a.get("dir");
                boolean bd = (Boolean) b.get("dir");
                if (ad != bd) {
                    return ad ? -1 : 1;
                }
                return a.get("name").toString().compareToIgnoreCase(b.get("name").toString());
            });
        }
        Map<String, Object> data = new HashMap<>();
        data.put("path", dir.getAbsolutePath());
        data.put("name", dir.getName());
        data.put("items", items);
        return Result.success(data);
    }

    /** 文件/目录信息（用于前端判断大小与下载方式）。 */
    @GetMapping("/info")
    public Result<Map<String, Object>> info(@RequestParam String path) {
        File f = taskManager.resolve(path);
        if (f == null) {
            return Result.error(400, "路径不在可访问范围内: " + path);
        }
        if (!f.exists()) {
            return Result.error(400, "文件不存在: " + path);
        }
        return Result.success(toItem(f));
    }

    private Map<String, Object> toItem(File f) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", f.getName());
        m.put("path", f.getAbsolutePath());
        m.put("dir", f.isDirectory());
        m.put("size", f.isDirectory() ? -1 : f.length());
        m.put("mtime", f.lastModified());
        return m;
    }

    /** 小文件直接流式下载（前端仅在单文件 <300MB 时调用）。 */
    @GetMapping("/raw")
    public ResponseEntity<StreamingResponseBody> raw(@RequestParam String path) {
        File f = taskManager.resolve(path);
        if (f == null || !f.exists() || f.isDirectory()) {
            return ResponseEntity.status(400).body(os -> { });
        }
        StreamingResponseBody body = os -> copy(f, os);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, attachment(f.getName()))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(f.length())
                .body(body);
    }

    // ==================== 压缩下载任务 ====================

    /** 创建压缩下载任务（多文件/目录/大文件，ZIP 分卷 ≤300MB）。 */
    @PostMapping("/task/create")
    public Result<Map<String, Object>> createTask(@RequestBody Map<String, Object> body) {
        Object pathsObj = body.get("paths");
        if (!(pathsObj instanceof List)) {
            return Result.paramError("paths 不能为空");
        }
        @SuppressWarnings("unchecked")
        List<String> paths = (List<String>) pathsObj;
        String baseName = body.get("baseName") != null ? body.get("baseName").toString() : null;
        try {
            DownloadTask t = taskManager.create(paths, baseName);
            return Result.success(toMap(t));
        } catch (Exception e) {
            return Result.error(500, "创建下载任务失败: " + e.getMessage());
        }
    }

    /** 任务列表（含状态、进度、分卷）。 */
    @GetMapping("/task/list")
    public Result<List<Map<String, Object>>> listTasks() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (DownloadTask t : taskManager.list()) {
            list.add(toMap(t));
        }
        return Result.success(list);
    }

    /** 任务详情。 */
    @GetMapping("/task/{id}")
    public Result<Map<String, Object>> getTask(@PathVariable String id) {
        DownloadTask t = taskManager.get(id);
        if (t == null) {
            return Result.error(404, "任务不存在");
        }
        return Result.success(toMap(t));
    }

    /** 取消任务（优雅终止：删半成品、释放并发许可）。 */
    @PostMapping("/task/{id}/cancel")
    public Result<Map<String, Object>> cancelTask(@PathVariable String id) {
        if (!taskManager.cancel(id)) {
            return Result.error(404, "任务不存在");
        }
        DownloadTask t = taskManager.get(id);
        return Result.success(toMap(t));
    }

    /** 删除任务（清理产物，立即释放空间）。 */
    @PostMapping("/task/{id}/delete")
    public Result<Boolean> deleteTask(@PathVariable String id) {
        if (!taskManager.delete(id)) {
            return Result.error(404, "任务不存在");
        }
        return Result.success(true);
    }

    /** 下载指定分卷。 */
    @GetMapping("/task/{id}/part/{index}")
    public ResponseEntity<StreamingResponseBody> downloadPart(@PathVariable String id,
                                                              @PathVariable int index) {
        DownloadTask t = taskManager.get(id);
        if (t == null || t.getParts().isEmpty()) {
            return ResponseEntity.status(404).body(os -> { });
        }
        if (index < 1 || index > t.getParts().size()) {
            return ResponseEntity.status(400).body(os -> { });
        }
        File part = t.getParts().get(index - 1);
        if (!part.exists()) {
            return ResponseEntity.status(404).body(os -> { });
        }
        StreamingResponseBody body = os -> copy(part, os);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, attachment(part.getName()))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(part.length())
                .body(body);
    }

    // ==================== 上传 ====================

    /** 上传文件到指定目录（同名覆盖）。 */
    @PostMapping("/upload")
    public Result<Map<String, Object>> upload(@RequestParam String dir,
                                              @RequestParam MultipartFile file) {
        File targetDir = taskManager.resolve(dir);
        if (targetDir == null) {
            return Result.error(400, "目标目录不在可访问范围内: " + dir);
        }
        if (!targetDir.isDirectory()) {
            return Result.error(400, "目标目录不存在: " + dir);
        }
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.trim().isEmpty()) {
            fileName = "upload.bin";
        }
        fileName = new File(fileName).getName(); // 防路径穿越
        File dest = new File(targetDir, fileName);
        try {
            try (InputStream in = file.getInputStream();
                 OutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
            }
            Map<String, Object> data = new HashMap<>();
            data.put("path", dest.getAbsolutePath());
            data.put("size", dest.length());
            return Result.success(data);
        } catch (Exception e) {
            return Result.error(500, "上传失败: " + e.getMessage());
        }
    }

    // ==================== 工具 ====================

    /** 任务序列化为前端展示结构。 */
    public static Map<String, Object> toMap(DownloadTask t) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", t.getId());
        m.put("name", t.getName());
        m.put("status", t.getStatus());
        m.put("totalSize", t.getTotalSize());
        m.put("processedBytes", t.getProcessedBytes());
        m.put("progressPct", t.getTotalSize() > 0
                ? Math.min(100, (int) (t.getProcessedBytes() * 100 / t.getTotalSize())) : 0);
        m.put("createTime", t.getCreateTime());
        m.put("message", t.getMessage());
        List<Map<String, Object>> parts = new ArrayList<>();
        int idx = 1;
        for (File p : t.getParts()) {
            Map<String, Object> pm = new HashMap<>();
            pm.put("index", idx++);
            pm.put("name", p.getName());
            pm.put("size", p.length());
            parts.add(pm);
        }
        m.put("parts", parts);
        return m;
    }

    private void copy(File f, OutputStream os) throws java.io.IOException {
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) != -1) {
                os.write(buf, 0, n);
            }
            os.flush();
        }
    }

    private String attachment(String fileName) {
        try {
            String encoded = URLEncoder.encode(fileName, "UTF-8")
                    .replace("+", "%20");
            return "attachment; filename*=UTF-8''" + encoded;
        } catch (java.io.UnsupportedEncodingException e) {
            return "attachment";
        }
    }
}
