package com.ops.agent.controller;

import com.ops.agent.file.ConfigFileService;
import com.ops.agent.file.LogFileService;
import com.ops.common.response.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Agent文件接收接口
 * 接收Server下发的Jar包
 */
@RestController
@RequestMapping("/file")
public class FileController {

    @Value("${agent.data-path:/app/data}")
    private String dataPath;

    private final ConfigFileService configFileService = new ConfigFileService();
    private final LogFileService logFileService = new LogFileService();

    /**
     * 接收Server下发的Jar包
     */
    @PostMapping("/receive")
    public Result<Map<String, Object>> receiveFile(@RequestParam String projectId,
                                                    @RequestParam String versionName,
                                                    @RequestParam MultipartFile file) {
        try {
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || originalFilename.isEmpty()) {
                originalFilename = "app.jar";
            }
            String dirPath = dataPath + "/versions/" + projectId + "/" + versionName;
            File dir = new File(dirPath);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            File destFile = new File(dir, originalFilename);
            if (destFile.exists()) {
                destFile.delete();
            }

            // 使用 InputStream 手动写入，避免 transferTo 的临时文件路径问题
            java.io.InputStream in = file.getInputStream();
            java.io.OutputStream out = new java.io.FileOutputStream(destFile);
            byte[] buffer = new byte[8192];
            int bytesRead;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            long totalSize = 0;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                md.update(buffer, 0, bytesRead);
                totalSize += bytesRead;
            }
            in.close();
            out.close();

            String sha256 = bytesToHex(md.digest());

            Map<String, Object> data = new HashMap<>();
            data.put("filePath", destFile.getAbsolutePath());
            data.put("fileName", originalFilename);
            data.put("fileSize", totalSize);
            data.put("sha256", sha256);
            data.put("status", "RECEIVED");

            return Result.success(data);
        } catch (Exception e) {
            return Result.error(500, "文件接收失败: " + e.getMessage());
        }
    }

    /**
     * 获取日志文件内容
     */
    @GetMapping("/log")
    public Result<String> getLog(@RequestParam String logPath,
                                  @RequestParam(defaultValue = "0") int offset,
                                  @RequestParam(defaultValue = "100") int lines) {
        try {
            File logFile = new File(logPath);
            if (!logFile.exists()) {
                return Result.error(400, "日志文件不存在: " + logPath);
            }

            try (Stream<String> linesStream = Files.lines(logFile.toPath())) {
                String content = linesStream.skip(offset).limit(lines)
                        .reduce("", (a, b) -> a + b + "\n");
                return Result.success(content.trim());
            }
        } catch (Exception e) {
            return Result.error(500, "读取日志失败: " + e.getMessage());
        }
    }

    /**
     * 获取配置文件内容
     */
    @GetMapping("/config")
    public Result<String> getConfig(@RequestParam String configPath) {
        try {
            return Result.success(configFileService.readConfig(configPath));
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("不存在")) {
                return Result.error(400, e.getMessage());
            }
            return Result.error(500, "读取配置失败: " + e.getMessage());
        }
    }

    /**
     * 写入配置文件，可选备份原文件。
     */
    @PostMapping("/config")
    public Result<Map<String, Object>> writeConfig(@RequestBody Map<String, Object> body) {
        Object configPathObj = body.get("configPath");
        Object contentObj = body.get("content");
        if (configPathObj == null || contentObj == null) {
            return Result.paramError("configPath 与 content 不能为空");
        }
        boolean backup = Boolean.TRUE.equals(body.get("backup"));
        try {
            return Result.success(configFileService.writeConfig(
                    configPathObj.toString(), contentObj.toString(), backup));
        } catch (IOException e) {
            return Result.error(500, "写入配置失败: " + e.getMessage());
        }
    }

    /**
     * 备份配置文件到 .backup/{timestamp}/ 目录。
     */
    @PostMapping("/config/backup")
    public Result<Map<String, Object>> backupConfig(@RequestBody Map<String, String> body) {
        String configPath = body.get("configPath");
        if (configPath == null || configPath.trim().isEmpty()) {
            return Result.paramError("configPath 不能为空");
        }
        try {
            return Result.success(configFileService.backupConfig(configPath));
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("不存在")) {
                return Result.error(400, e.getMessage());
            }
            return Result.error(500, "备份配置失败: " + e.getMessage());
        }
    }

    /**
     * 列出日志目录下的文件。
     */
    @GetMapping("/log/list")
    public Result<List<Map<String, Object>>> listLogs(@RequestParam String logDir) {
        try {
            return Result.success(logFileService.listLogs(logDir));
        } catch (IOException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 读取日志文件尾部 N 行。
     */
    @GetMapping("/log/tail")
    public Result<Map<String, Object>> tailLog(@RequestParam String logPath,
                                               @RequestParam(defaultValue = "200") int lines) {
        try {
            return Result.success(logFileService.tail(logPath, lines));
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("不存在")) {
                return Result.error(400, e.getMessage());
            }
            return Result.error(500, "读取日志尾部失败: " + e.getMessage());
        }
    }

    /**
     * 在日志文件中搜索关键词。
     */
    @PostMapping("/log/search")
    public Result<Map<String, Object>> searchLog(@RequestBody Map<String, Object> body) {
        Object logPathObj = body.get("logPath");
        Object keywordObj = body.get("keyword");
        if (logPathObj == null || keywordObj == null) {
            return Result.paramError("logPath 与 keyword 不能为空");
        }
        int maxResults = body.get("maxResults") != null
                ? Integer.parseInt(body.get("maxResults").toString()) : 100;
        int contextLines = body.get("contextLines") != null
                ? Integer.parseInt(body.get("contextLines").toString()) : 2;
        try {
            return Result.success(logFileService.search(
                    logPathObj.toString(), keywordObj.toString(), maxResults, contextLines));
        } catch (IOException e) {
            return Result.error(500, "日志搜索失败: " + e.getMessage());
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
