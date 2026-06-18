package com.ops.agent.controller;

import com.ops.common.response.Result;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Agent文件接收接口
 * 接收Server下发的Jar包
 */
@RestController
@RequestMapping("/file")
public class FileController {

    private static final String SERVER_PATH = "./data";

    /**
     * 接收Server下发的Jar包
     */
    @PostMapping("/receive")
    public Result<Map<String, Object>> receiveFile(@RequestParam String projectId,
                                                    @RequestParam String versionName,
                                                    @RequestParam MultipartFile file) {
        try {
            String originalFilename = file.getOriginalFilename();
            String dirPath = SERVER_PATH + "/versions/" + projectId + "/" + versionName;
            File dir = new File(dirPath);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String savePath = dirPath + "/" + originalFilename;
            file.transferTo(new File(savePath));

            // Calculate SHA-256
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = file.getBytes();
            String sha256 = bytesToHex(md.digest(bytes));

            Map<String, Object> data = new HashMap<>();
            data.put("filePath", savePath);
            data.put("fileName", originalFilename);
            data.put("fileSize", file.getSize());
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
            File configFile = new File(configPath);
            if (!configFile.exists()) {
                return Result.error(400, "配置文件不存在: " + configPath);
            }
            String content = new String(Files.readAllBytes(configFile.toPath()));
            return Result.success(content);
        } catch (Exception e) {
            return Result.error(500, "读取配置失败: " + e.getMessage());
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
