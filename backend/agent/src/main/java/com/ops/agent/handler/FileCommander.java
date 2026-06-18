package com.ops.agent.handler;

import java.io.File;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/**
 * 文件命令处理器
 */
public class FileCommander {

    private final String baseDir;

    public FileCommander(String baseDir) {
        this.baseDir = baseDir;
    }

    public Map<String, Object> readFile(String relativePath) {
        try {
            File file = new File(baseDir, relativePath);
            if (!file.exists()) {
                Map<String, Object> result = new HashMap<>();
                result.put("status", "FAILED");
                result.put("message", "文件不存在: " + relativePath);
                return result;
            }

            byte[] content = Files.readAllBytes(file.toPath());

            Map<String, Object> result = new HashMap<>();
            result.put("status", "SUCCESS");
            result.put("fileName", file.getName());
            result.put("fileSize", file.length());
            result.put("encoding", "UTF-8");
            result.put("content", new String(content, "UTF-8"));
            return result;
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("status", "FAILED");
            result.put("message", "读取文件失败: " + e.getMessage());
            return result;
        }
    }

    public Map<String, Object> verifyFile(String relativePath, String expectedSha256) {
        try {
            File file = new File(baseDir, relativePath);
            if (!file.exists()) {
                Map<String, Object> result = new HashMap<>();
                result.put("status", "FAILED");
                result.put("message", "文件不存在: " + relativePath);
                return result;
            }

            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = Files.readAllBytes(file.toPath());
            String actualSha256 = bytesToHex(md.digest(bytes));

            boolean valid = expectedSha256.equalsIgnoreCase(actualSha256);
            Map<String, Object> result = new HashMap<>();
            result.put("status", valid ? "SUCCESS" : "FAILED");
            result.put("fileName", file.getName());
            result.put("expectedSha256", expectedSha256);
            result.put("actualSha256", actualSha256);
            result.put("valid", valid);
            return result;
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("status", "FAILED");
            result.put("message", "校验失败: " + e.getMessage());
            return result;
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
