package com.ops.agent.controller;

import com.ops.agent.arthas.ArthasCommandType;
import com.ops.agent.arthas.ArthasHttpClient;
import com.ops.agent.arthas.ArthasSession;
import com.ops.agent.arthas.ArthasSessionManager;
import com.ops.common.response.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 侧 Arthas 诊断接口
 * 由 Server 通过 AgentClient 调用
 */
@RestController
@RequestMapping("/arthas")
public class ArthasController {
    private static final Logger log = LoggerFactory.getLogger(ArthasController.class);

    @Autowired
    private ArthasSessionManager sessionManager;

    @Value("${agent.data-path:/app/data}")
    private String agentDataPath;

    /**
     * POST /api/arthas/attach - attach 到目标 PID
     */
    @PostMapping("/attach")
    public Result<Map<String, Object>> attach(@RequestBody Map<String, Object> body) {
        long pid = Long.parseLong(body.get("pid").toString());
        String projectId = body.get("projectId") != null ? body.get("projectId").toString() : null;
        String nodeId = body.get("nodeId") != null ? body.get("nodeId").toString() : null;

        try {
            ArthasSession session = sessionManager.attach(pid, projectId, nodeId);
            Map<String, Object> data = new HashMap<>();
            data.put("pid", session.getPid());
            data.put("port", session.getPort());
            data.put("arthasVersion", session.getArthasVersion());
            data.put("attachTime", session.getAttachTime());
            data.put("status", "ATTACHED");
            log.info("Arthas attach 成功: pid={}, port={}", pid, session.getPort());
            return Result.success(data);
        } catch (Exception e) {
            log.error("Arthas attach 失败: pid={}, error={}", pid, e.getMessage());
            return Result.error(500, "attach 失败: " + e.getMessage());
        }
    }

    /**
     * POST /api/arthas/detach - 卸载
     */
    @PostMapping("/detach")
    public Result<Map<String, Object>> detach(@RequestBody Map<String, Object> body) {
        long pid = Long.parseLong(body.get("pid").toString());
        try {
            sessionManager.detach(pid);
            Map<String, Object> data = new HashMap<>();
            data.put("pid", pid);
            data.put("status", "DETACHED");
            return Result.success(data);
        } catch (Exception e) {
            log.error("Arthas detach 失败: pid={}, error={}", pid, e.getMessage());
            return Result.error(500, "detach 失败: " + e.getMessage());
        }
    }

    /**
     * POST /api/arthas/exec - 同步执行命令
     */
    @PostMapping("/exec")
    public Result<Map<String, Object>> exec(@RequestBody Map<String, Object> body) {
        long pid = Long.parseLong(body.get("pid").toString());
        String command = body.get("command") != null ? body.get("command").toString() : "";
        int timeoutMs = body.get("timeoutMs") != null
                ? Integer.parseInt(body.get("timeoutMs").toString()) : 30000;

        if (command.isEmpty()) {
            return Result.paramError("命令不能为空");
        }

        // 白名单校验
        if (!ArthasCommandType.isAllowed(command)) {
            log.warn("Arthas 命令不在白名单: pid={}, command={}", pid, command);
            return Result.error(403, "命令不在白名单中: " + ArthasCommandType.detect(command));
        }

        try {
            long start = System.currentTimeMillis();
            ArthasHttpClient.ArthasResult result = sessionManager.exec(pid, command, timeoutMs);
            long duration = System.currentTimeMillis() - start;

            Map<String, Object> data = new HashMap<>();
            data.put("success", result.isSuccess());
            data.put("results", result.getResults());
            data.put("commandType", ArthasCommandType.detect(command));
            data.put("durationMs", duration);
            if (!result.isSuccess()) {
                data.put("errorMsg", result.getErrorMsg());
            }

            // profiler stop 命令：自动读取 outputFile 的 HTML 内容返回，避免前端只拿到文件路径
            if (command.trim().startsWith("profiler stop") && result.getResults() != null && !result.getResults().isEmpty()) {
                Object first = result.getResults().get(0);
                if (first instanceof Map) {
                    Object outputFile = ((Map<?, ?>) first).get("outputFile");
                    if (outputFile != null) {
                        String filePath = outputFile.toString();
                        try {
                            File file = new File(filePath);
                            if (file.exists() && file.isFile()) {
                                byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
                                String htmlContent = new String(bytes, StandardCharsets.UTF_8);
                                ((Map<String, Object>) first).put("htmlContent", htmlContent);
                                log.info("profiler 火焰图 HTML 已读取: {} ({} bytes)", filePath, htmlContent.length());
                            } else {
                                log.warn("profiler outputFile 不存在: {}", filePath);
                            }
                        } catch (Exception e) {
                            log.warn("读取 profiler outputFile 失败: {}, error: {}", filePath, e.getMessage());
                        }
                    }
                }
            }

            return Result.success(data);
        } catch (Exception e) {
            log.error("Arthas 命令执行失败: pid={}, command={}, error={}", pid, command, e.getMessage());
            return Result.error(500, "命令执行失败: " + e.getMessage());
        }
    }

    /**
     * GET /api/arthas/status - 会话状态
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> status(@RequestParam(required = false) Long pid) {
        Map<String, Object> data = new HashMap<>();
        if (pid != null) {
            ArthasSession session = sessionManager.getSession(pid);
            if (session != null) {
                data.put("pid", session.getPid());
                data.put("port", session.getPort());
                data.put("arthasVersion", session.getArthasVersion());
                data.put("attachTime", session.getAttachTime());
                data.put("lastActiveTime", session.getLastActiveTime());
                data.put("attached", session.isAttached());
            } else {
                data.put("attached", false);
            }
        } else {
            List<Map<String, Object>> list = new ArrayList<>();
            for (ArthasSession s : sessionManager.getAllSessions()) {
                Map<String, Object> item = new HashMap<>();
                item.put("pid", s.getPid());
                item.put("port", s.getPort());
                item.put("arthasVersion", s.getArthasVersion());
                item.put("attachTime", s.getAttachTime());
                item.put("attached", s.isAttached());
                list.add(item);
            }
            data.put("sessions", list);
            data.put("total", list.size());
        }
        return Result.success(data);
    }

    /**
     * GET /api/arthas/flamegraph-list - 获取火焰图历史文件列表
     */
    @GetMapping("/flamegraph-list")
    public Result<List<Map<String, Object>>> flamegraphList(@RequestParam long pid) {
        try {
            ArthasSession session = sessionManager.getSession(pid);
            if (session == null || session.getWorkingDir() == null) {
                return Result.success(new ArrayList<>());
            }
            File arthasOutputDir = new File(session.getWorkingDir(), "arthas-output");
            if (!arthasOutputDir.exists() || !arthasOutputDir.isDirectory()) {
                return Result.success(new ArrayList<>());
            }
            File[] files = arthasOutputDir.listFiles((dir, name) -> name.endsWith(".html"));
            List<Map<String, Object>> list = new ArrayList<>();
            if (files != null) {
                for (File file : files) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("fileName", file.getName());
                    item.put("filePath", file.getAbsolutePath());
                    item.put("size", file.length());
                    item.put("lastModified", file.lastModified());
                    list.add(item);
                }
                // 按修改时间降序排序
                list.sort((a, b) -> Long.compare((Long) b.get("lastModified"), (Long) a.get("lastModified")));
            }
            return Result.success(list);
        } catch (Exception e) {
            log.error("获取火焰图历史列表失败: pid={}, error={}", pid, e.getMessage());
            return Result.error(500, "获取火焰图历史列表失败: " + e.getMessage());
        }
    }

    /**
     * GET /api/arthas/flamegraph/download - 下载火焰图文件
     */
    @GetMapping("/flamegraph/download")
    public void downloadFlamegraph(@RequestParam long pid,
                                    @RequestParam String fileName,
                                    HttpServletResponse response) {
        // 安全校验：禁止路径穿越
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            response.setStatus(400);
            return;
        }
        ArthasSession session = sessionManager.getSession(pid);
        if (session == null || session.getWorkingDir() == null) {
            response.setStatus(404);
            return;
        }
        File arthasOutputDir = new File(session.getWorkingDir(), "arthas-output");
        File file = new File(arthasOutputDir, fileName);
        // 校验文件在 arthas-output 目录内
        try {
            if (!file.getCanonicalPath().startsWith(arthasOutputDir.getCanonicalPath())) {
                response.setStatus(403);
                return;
            }
        } catch (Exception e) {
            response.setStatus(500);
            return;
        }
        if (!file.exists()) {
            response.setStatus(404);
            return;
        }
        response.setContentType("text/html; charset=UTF-8");
        String encodedName;
        try {
            encodedName = URLEncoder.encode(fileName, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            encodedName = fileName;
        }
        response.setHeader("Content-Disposition",
                "attachment; filename=" + encodedName);
        response.setContentLengthLong(file.length());
        try (FileInputStream fis = new FileInputStream(file);
             OutputStream os = response.getOutputStream()) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = fis.read(buf)) > 0) {
                os.write(buf, 0, len);
            }
        } catch (Exception e) {
            log.error("下载火焰图失败: {}", e.getMessage());
        }
    }

    /**
     * GET /api/arthas/heapdump/download - 下载 heapdump 文件
     */
    @GetMapping("/heapdump/download")
    public void downloadHeapdump(@RequestParam String fileName,
                                  HttpServletResponse response) {
        // 安全校验：禁止路径穿越
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            response.setStatus(400);
            return;
        }
        File heapdumpDir = new File(agentDataPath + "/arthas/heapdump");
        File file = new File(heapdumpDir, fileName);
        // 校验文件在 heapdump 目录内
        try {
            if (!file.getCanonicalPath().startsWith(heapdumpDir.getCanonicalPath())) {
                response.setStatus(403);
                return;
            }
        } catch (Exception e) {
            response.setStatus(500);
            return;
        }
        if (!file.exists()) {
            response.setStatus(404);
            return;
        }
        response.setContentType("application/octet-stream");
        String encodedName;
        try {
            encodedName = URLEncoder.encode(fileName, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            encodedName = fileName;
        }
        response.setHeader("Content-Disposition",
                "attachment; filename=" + encodedName);
        response.setContentLengthLong(file.length());
        try (FileInputStream fis = new FileInputStream(file);
             OutputStream os = response.getOutputStream()) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = fis.read(buf)) > 0) {
                os.write(buf, 0, len);
            }
        } catch (Exception e) {
            log.error("下载 heapdump 失败: {}", e.getMessage());
        }
    }
}
