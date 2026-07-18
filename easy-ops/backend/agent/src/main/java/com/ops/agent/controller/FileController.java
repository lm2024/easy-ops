package com.ops.agent.controller;

import com.ops.agent.file.ConfigFileService;
import com.ops.agent.file.LogDiscoveryService;
import com.ops.agent.file.LogFileService;
import com.ops.common.response.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private final LogDiscoveryService logDiscoveryService = new LogDiscoveryService();

    /**
     * 接收Server下发的Jar包
     */
    @PostMapping("/receive")
    public Result<Map<String, Object>> receiveFile(@RequestParam String projectId,
                                                    @RequestParam String versionName,
                                                    @RequestParam MultipartFile file,
                                                    @RequestParam(required = false) String targetDir) {
        try {
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || originalFilename.isEmpty()) {
                originalFilename = "app.jar";
            }
            String dirPath;
            if (targetDir != null && !targetDir.trim().isEmpty()) {
                dirPath = targetDir.trim();
            } else {
                dirPath = dataPath + "/versions/" + projectId + "/" + versionName;
            }
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
    public Result<Map<String, Object>> getLog(@RequestParam String logPath,
                                              @RequestParam(defaultValue = "0") int offset,
                                              @RequestParam(defaultValue = "100") int lines,
                                              @RequestParam(required = false) String level,
                                              @RequestParam(defaultValue = "page") String mode) {
        try {
            if ("tail".equalsIgnoreCase(mode)) {
                return Result.success(logFileService.tail(logPath, lines, level));
            }
            return Result.success(logFileService.readPage(logPath, offset, lines, level));
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("不存在")) {
                return Result.error(400, e.getMessage());
            }
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
    public Result<List<Map<String, Object>>> listLogs(@RequestParam String logDir,
                                                     @RequestParam(required = false) String pattern) {
        try {
            return Result.success(logFileService.listLogs(logDir, pattern));
        } catch (IOException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 智能发现节点上的日志文件（扫描部署目录及常见 logs 目录）。
     */
    @GetMapping("/log/discover")
    public Result<Map<String, Object>> discoverLogs(@RequestParam(required = false) String deployDir,
                                                     @RequestParam(required = false) String logDir) {
        try {
            return Result.success(logDiscoveryService.discover(deployDir, logDir, dataPath));
        } catch (IOException e) {
            return Result.error(400, "日志扫描失败: " + e.getMessage());
        }
    }

    /**
     * 读取日志文件尾部 N 行。
     */
    @GetMapping("/log/tail")
    public Result<Map<String, Object>> tailLog(@RequestParam String logPath,
                                               @RequestParam(defaultValue = "200") int lines,
                                               @RequestParam(required = false) String level) {
        try {
            return Result.success(logFileService.tail(logPath, lines, level));
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
        String level = body.get("level") != null ? body.get("level").toString() : null;
        try {
            return Result.success(logFileService.search(
                    logPathObj.toString(), keywordObj.toString(), maxResults, contextLines, level));
        } catch (IOException e) {
            return Result.error(500, "日志搜索失败: " + e.getMessage());
        }
    }

    /**
     * 解压 zip 到目标目录（用于前端 dist 部署）
     */
    @PostMapping("/unzip")
    public Result<Map<String, Object>> unzipDeploy(@RequestBody Map<String, String> body) {
        String zipPath = body.get("zipPath");
        String targetDir = body.get("targetDir");
        if (zipPath == null || targetDir == null || zipPath.trim().isEmpty() || targetDir.trim().isEmpty()) {
            return Result.paramError("zipPath 与 targetDir 不能为空");
        }
        try {
            File zipFile = new File(zipPath);
            if (!zipFile.exists()) {
                return Result.error(400, "zip 文件不存在: " + zipPath);
            }
            File dest = new File(targetDir);
            if (!dest.exists()) {
                dest.mkdirs();
            }
            unzipFile(zipFile, dest);
            Map<String, Object> data = new HashMap<>();
            data.put("targetDir", dest.getAbsolutePath());
            data.put("status", "UNZIPPED");
            return Result.success(data);
        } catch (Exception e) {
            return Result.error(500, "解压失败: " + e.getMessage());
        }
    }

    private void unzipFile(File zipFile, File destDir) throws IOException {
        java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new java.io.FileInputStream(zipFile));
        java.util.zip.ZipEntry entry;
        byte[] buffer = new byte[8192];
        while ((entry = zis.getNextEntry()) != null) {
            File outFile = new File(destDir, entry.getName());
            if (entry.isDirectory()) {
                outFile.mkdirs();
            } else {
                File parent = outFile.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                java.io.FileOutputStream fos = new java.io.FileOutputStream(outFile);
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }
                fos.close();
            }
            zis.closeEntry();
        }
        zis.close();
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
