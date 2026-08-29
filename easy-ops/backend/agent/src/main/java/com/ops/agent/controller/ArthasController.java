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

    /**
     * 内联返回火焰图 HTML 的大小上限（20MB）。
     * 超过则只回传文件路径，避免把 Agent 内存和 HTTP 响应同时撑爆。
     */
    private static final long MAX_FLAMEGRAPH_BYTES = 20L * 1024 * 1024;

    /**
     * 火焰图历史文件最大保留数量。
     * 超过此数量时，自动删除最旧的文件，防止磁盘占满。
     */
    private static final int MAX_FLAMEGRAPH_FILES = 10;

    @Autowired
    private ArthasSessionManager sessionManager;

    @Value("${agent.data-path:/app/data}")
    private String agentDataPath;

    /**
     * POST /api/arthas/diagnose - 执行 JVM 诊断命令（自动解析结果）
     * 支持的命令类型：jmap-histo, thread-print, gc-stats
     */
    @PostMapping("/diagnose")
    public Result<Map<String, Object>> diagnose(@RequestBody Map<String, Object> body) {
        long pid = Long.parseLong(body.get("pid").toString());
        String diagnoseType = body.get("type") != null ? body.get("type").toString() : "jmap-histo";

        try {
            ArthasSession session = sessionManager.getSession(pid);
            if (session == null) {
                return Result.error(404, "未找到 Arthas 会话，请先 attach");
            }

            Map<String, Object> result = new HashMap<>();
            result.put("pid", Long.valueOf(pid));
            result.put("type", diagnoseType);
            result.put("timestamp", System.currentTimeMillis());

            switch (diagnoseType) {
                case "jmap-histo":
                    result.putAll(executeJmapHisto(pid));
                    break;
                case "thread-print":
                    result.putAll(executeThreadPrint(pid));
                    break;
                case "gc-stats":
                    result.putAll(executeGcStats(pid));
                    break;
                case "mem-alloc":
                    // 同时执行 jmap 和 profiler，合并结果
                    Map<String, Object> jmapResult = executeJmapHisto(pid);
                    Map<String, Object> profilerResult = executeMemAllocProfile(pid);
                    result.putAll(jmapResult);
                    result.putAll(profilerResult);
                    // 合并 topClasses 和 topMethods
                    result.put("topClasses", jmapResult.get("topClasses"));
                    result.put("topMethods", profilerResult.get("topMethods"));
                    result.put("totalBytes", jmapResult.get("totalBytes"));
                    result.put("totalBytesFormatted", jmapResult.get("totalBytesFormatted"));
                    result.put("totalSamples", profilerResult.get("totalSamples"));
                    break;
                default:
                    return Result.error(400, "不支持的诊断类型: " + diagnoseType);
            }

            return Result.success(result);
        } catch (Exception e) {
            log.error("JVM 诊断失败: pid={}, type={}, error={}", pid, diagnoseType, e.getMessage());
            return Result.error(500, "诊断失败: " + e.getMessage());
        }
    }

    /**
     * 执行 jmap -histo 并解析结果
     */
    private Map<String, Object> executeJmapHisto(long pid) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 执行 jmap -histo:live 命令
            Process process = Runtime.getRuntime().exec(new String[]{"jmap", "-histo:live", String.valueOf(pid)});
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            reader.close();
            process.waitFor();

            // 解析输出
            List<Map<String, Object>> classList = new ArrayList<>();
            String[] lines = output.toString().split("\n");
            long totalInstances = 0;
            long totalBytes = 0;

            for (String l : lines) {
                // 跳过头部和尾部
                if (l.trim().isEmpty() || l.contains("num") || l.contains("total") || l.contains("---")) {
                    continue;
                }
                // 解析格式: num     #instances         #bytes  class name (markup)
                String[] parts = l.trim().split("\\s+");
                if (parts.length >= 4) {
                    try {
                        Map<String, Object> classInfo = new HashMap<>();
                        String className = parts[3];
                        long instances = Long.parseLong(parts[1]);
                        long bytes = Long.parseLong(parts[2]);

                        classInfo.put("className", className);
                        classInfo.put("instances", Long.valueOf(instances));
                        classInfo.put("bytes", Long.valueOf(bytes));
                        classInfo.put("bytesFormatted", formatBytes(bytes));
                        classList.add(classInfo);

                        totalInstances += instances;
                        totalBytes += bytes;
                    } catch (NumberFormatException e) {
                        // 跳过无法解析的行
                    }
                }
            }

            result.put("classList", classList);
            result.put("totalInstances", Long.valueOf(totalInstances));
            result.put("totalBytes", Long.valueOf(totalBytes));
            result.put("totalBytesFormatted", formatBytes(totalBytes));
            result.put("topClasses", classList.subList(0, Math.min(20, classList.size())));
            result.put("rawOutput", output.toString());
        } catch (Exception e) {
            log.error("jmap -histo 执行失败: {}", e.getMessage());
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * 执行 thread -n 10 获取 TOP 10 线程
     */
    private Map<String, Object> executeThreadPrint(long pid) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 通过 Arthas 执行 thread 命令
            ArthasHttpClient.ArthasResult arthasResult = sessionManager.exec(pid, "thread -n 10", 10000);
            if (arthasResult.isSuccess() && arthasResult.getResults() != null) {
                result.put("threadInfo", arthasResult.getResults());
            } else {
                result.put("error", arthasResult.getErrorMsg());
            }
        } catch (Exception e) {
            log.error("thread 命令执行失败: {}", e.getMessage());
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * 执行 GC 统计
     */
    private Map<String, Object> executeGcStats(long pid) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 通过 Arthas 执行 jvm 命令获取 GC 信息
            ArthasHttpClient.ArthasResult arthasResult = sessionManager.exec(pid, "jvm | grep gc", 10000);
            if (arthasResult.isSuccess() && arthasResult.getResults() != null) {
                result.put("gcInfo", arthasResult.getResults());
            } else {
                result.put("error", arthasResult.getErrorMsg());
            }
        } catch (Exception e) {
            log.error("GC 统计执行失败: {}", e.getMessage());
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * 执行内存分配火焰图分析（自动采样 5 秒）
     * 解析火焰图输出，返回 TOP 方法列表
     */
    private Map<String, Object> executeMemAllocProfile(long pid) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 1. 启动 profiler 采样
            log.info("[内存分配分析] 启动 profiler, pid={}", pid);
            ArthasHttpClient.ArthasResult startResult = sessionManager.exec(pid, "profiler start --event alloc", 10000);
            log.info("[内存分配分析] profiler start 结果: success={}, error={}", startResult.isSuccess(), startResult.getErrorMsg());
            if (!startResult.isSuccess()) {
                result.put("error", "启动 profiler 失败: " + startResult.getErrorMsg());
                return result;
            }
            log.info("[内存分配分析] profiler start 返回: {}", startResult.getResults());

            // 2. 等待采样完成（5秒 + 2秒缓冲）
            log.info("[内存分配分析] 等待 7 秒采样...");
            Thread.sleep(7000);

            // 3. 停止 profiler 并获取结果
            log.info("[内存分配分析] 停止 profiler...");
            ArthasHttpClient.ArthasResult stopResult = sessionManager.exec(pid, "profiler stop --format collapsed", 30000);
            log.info("[内存分配分析] profiler stop 结果: success={}, error={}", stopResult.isSuccess(), stopResult.getErrorMsg());
            if (!stopResult.isSuccess()) {
                result.put("error", "停止 profiler 失败: " + stopResult.getErrorMsg());
                return result;
            }
            log.info("[内存分配分析] profiler stop 返回: {}", stopResult.getResults());

            // 4. 解析 collapsed 格式的火焰图数据
            if (stopResult.getResults() != null && !stopResult.getResults().isEmpty()) {
                String collapsed = extractCollapsedFromResults(stopResult.getResults());
                log.info("[内存分配分析] 提取的 collapsed 数据长度: {}", collapsed != null ? collapsed.length() : "null");
                if (collapsed != null) {
                    log.info("[内存分配分析] collapsed 前 500 字符: {}", collapsed.substring(0, Math.min(500, collapsed.length())));
                }
                if (collapsed != null && !collapsed.isEmpty()) {
                    List<Map<String, Object>> topMethods = parseCollapsedFormat(collapsed);
                    log.info("[内存分配分析] 解析出 TOP 方法数量: {}", topMethods.size());
                    result.put("topMethods", topMethods);
                    result.put("totalSamples", calculateTotalSamples(topMethods));
                } else {
                    log.warn("[内存分配分析] collapsed 数据为空");
                    result.put("error", "无法解析火焰图数据：collapsed 格式为空");
                }
            } else {
                log.warn("[内存分配分析] stopResult.getResults() 为空");
                result.put("error", "profiler 未返回数据");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[内存分配分析] 采样被中断", e);
            result.put("error", "采样被中断");
        } catch (Exception e) {
            log.error("[内存分配分析] 异常: {}", e.getMessage(), e);
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * 从 profiler 结果中提取 collapsed 格式数据
     */
    private String extractCollapsedFromResults(List<Map<String, Object>> results) {
        for (Map<String, Object> item : results) {
            // 1. 优先检查 htmlContent 字段（某些版本可能直接返回）
            Object htmlContent = item.get("htmlContent");
            if (htmlContent != null) {
                String content = htmlContent.toString();
                // 检查是否是 collapsed 格式（每行以分号分隔的调用栈 + 空格 + 数字）
                if (content.contains(";") && !content.contains("<html>")) {
                    return content;
                }
            }

            // 2. 如果有 outputFile，从文件中读取 collapsed 数据
            Object outputFile = item.get("outputFile");
            if (outputFile != null) {
                String filePath = outputFile.toString();
                log.info("[内存分配分析] 从文件读取 collapsed 数据: {}", filePath);
                try {
                    java.io.File file = new java.io.File(filePath);
                    if (file.exists()) {
                        byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
                        String content = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                        log.info("[内存分配分析] 文件读取成功，长度: {}", content.length());
                        return content;
                    } else {
                        log.warn("[内存分配分析] 文件不存在: {}", filePath);
                    }
                } catch (Exception e) {
                    log.error("[内存分配分析] 读取文件失败: {}", filePath, e);
                }
            }

            // 3. 检查 output 或 result 字段
            Object output = item.get("output");
            if (output != null) {
                return output.toString();
            }
            Object result = item.get("result");
            if (result != null) {
                return result.toString();
            }
        }
        return null;
    }

    /**
     * 解析 collapsed 格式（每行格式：调用栈栈帧1;调用栈栈帧2;... 样本数）
     * 返回 TOP 20 调用链（按样本数降序）
     */
    private List<Map<String, Object>> parseCollapsedFormat(String collapsed) {
        Map<String, Long> chainSamples = new HashMap<>();
        String[] lines = collapsed.split("\n");

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            // 格式: a;b;c;...;d 123
            int lastSpace = line.lastIndexOf(' ');
            if (lastSpace <= 0) continue;

            String stackTrace = line.substring(0, lastSpace);
            String countStr = line.substring(lastSpace + 1);

            try {
                long count = Long.parseLong(countStr);
                String[] frames = stackTrace.split(";");

                // 找到用户方法（业务代码），构建调用链
                List<String> userCallChain = new ArrayList<>();
                for (String frame : frames) {
                    frame = frame.trim();
                    if (frame.isEmpty()) continue;

                    // 跳过内存分配标记和未知帧
                    if (frame.contains("_[") && frame.endsWith("]")) continue;
                    if (frame.equals("[unknown]") || frame.equals("[break_stack_range]")) continue;

                    // 转换成点号分隔格式
                    String dotNotation = frame.replace('/', '.');

                    // 标记是否是用户方法
                    boolean isUserMethod = !isInternalFrame(frame);
                    userCallChain.add(dotNotation + (isUserMethod ? " [USER]" : ""));
                }

                // 如果有用户方法，构建调用链字符串
                if (!userCallChain.isEmpty()) {
                    String chainKey = String.join(" -> ", userCallChain);
                    chainSamples.merge(chainKey, count, Long::sum);
                }
            } catch (NumberFormatException e) {
                // 跳过无法解析的行
            }
        }

        // 排序并取 TOP 20
        List<Map<String, Object>> topChains = new ArrayList<>();
        chainSamples.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(20)
            .forEach(entry -> {
                Map<String, Object> chain = new HashMap<>();
                chain.put("callChain", entry.getKey());
                chain.put("samples", entry.getValue());

                // 提取用户方法（标记了 [USER] 的）
                List<String> userMethods = new ArrayList<>();
                String[] parts = entry.getKey().split(" -> ");
                for (String part : parts) {
                    if (part.endsWith(" [USER]")) {
                        userMethods.add(part.replace(" [USER]", ""));
                    }
                }
                chain.put("userMethods", userMethods);
                // 取最后一个用户方法作为主要方法
                chain.put("primaryMethod", userMethods.isEmpty() ? "未知" : userMethods.get(userMethods.size() - 1));

                topChains.add(chain);
            });

        return topChains;
    }

    /**
     * 判断是否是 JVM/框架内部方法（需要跳过）
     */
    private boolean isInternalFrame(String frame) {
        // 跳过 Arthas 内部方法
        if (frame.startsWith("com/alibaba/arthas/") || frame.startsWith("com/taobao/arthas/")) {
            return true;
        }
        // 跳过内存分配标记（如 byte[]_[k], char[]_[i]）
        if (frame.contains("_[") && frame.endsWith("]")) {
            return true;
        }
        // 跳过未知帧
        if (frame.equals("[unknown]") || frame.equals("[break_stack_range]")) {
            return true;
        }
        // 注意：不跳过 java/javax/sun 开头的方法，因为这些可能是有意义的调用
        return false;
    }

    private long calculateTotalSamples(List<Map<String, Object>> methods) {
        long total = 0;
        for (Map<String, Object> m : methods) {
            total += (Long) m.get("samples");
        }
        return total;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

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
                                long size = file.length();
                                if (size > MAX_FLAMEGRAPH_BYTES) {
                                    // 超大火焰图不内联返回：一次性读进内存再塞进 JSON，
                                    // 会让 Agent 和 Server 两侧的内存同时飙升，
                                    // 这种情况只回传路径，由前端引导用户走文件下载。
                                    log.warn("火焰图过大，不内联返回: {} ({} bytes)", filePath, size);
                                    ((Map<String, Object>) first).put("tooLarge", Boolean.TRUE);
                                    ((Map<String, Object>) first).put("fileSizeBytes", size);
                                } else {
                                    byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
                                    String htmlContent = new String(bytes, StandardCharsets.UTF_8);
                                    ((Map<String, Object>) first).put("htmlContent", htmlContent);
                                    log.info("profiler 火焰图 HTML 已读取: {} ({} bytes)", filePath, htmlContent.length());
                                }
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

                // 自动清理：超过 MAX_FLAMEGRAPH_FILES 个时删除最旧的文件
                cleanupOldFlamegraphs(list, arthasOutputDir);
            }
            return Result.success(list);
        } catch (Exception e) {
            log.error("获取火焰图历史列表失败: pid={}, error={}", pid, e.getMessage());
            return Result.error(500, "获取火焰图历史列表失败: " + e.getMessage());
        }
    }

    /**
     * 清理旧的火焰图文件，保留最新的 MAX_FLAMEGRAPH_FILES 个。
     */
    private void cleanupOldFlamegraphs(List<Map<String, Object>> list, File arthasOutputDir) {
        if (list.size() <= MAX_FLAMEGRAPH_FILES) {
            return;
        }
        // list 已按时间降序排序，删除超出部分（最旧的）
        int deleteCount = list.size() - MAX_FLAMEGRAPH_FILES;
        log.info("火焰图文件数量 {} 超过限制 {}，开始清理 {} 个旧文件",
                list.size(), MAX_FLAMEGRAPH_FILES, deleteCount);
        for (int i = MAX_FLAMEGRAPH_FILES; i < list.size(); i++) {
            Map<String, Object> item = list.get(i);
            String fileName = (String) item.get("fileName");
            if (fileName != null) {
                File file = new File(arthasOutputDir, fileName);
                if (file.exists() && file.delete()) {
                    log.info("已删除旧火焰图文件: {}", fileName);
                }
            }
            list.remove(i);
            i--; // 删除后索引前移
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
