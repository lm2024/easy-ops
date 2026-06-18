package com.ops.agent.handler;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Map;

/**
 * 文件命令处理器
 * 处理文件操作：读取、校验、上传
 */
public class FileCommander {

    private final String baseDir;

    public FileCommander(String baseDir) {
        this.baseDir = baseDir;
    }

    /**
     * 读取文件内容
     *
     * @param relativePath 相对路径
     * @return 文件内容
     */
    public Map<String, Object> readFile(String relativePath) {
        try {
            File file = new File(baseDir, relativePath);
            if (!file.exists()) {
                return Map.of(
                        "status", "FAILED",
                        "message", "文件不存在: " + relativePath
                );
            }

            byte[] content = Files.readAllBytes(file.toPath());
            String encoding = guessEncoding(content);

            return Map.of(
                    "status", "SUCCESS",
                    "fileName", file.getName(),
                    "fileSize", file.length(),
                    "encoding", encoding,
                    "content", new String(content, encoding)
            );
        } catch (Exception e) {
            return Map.of(
                    "status", "FAILED",
                    "message", "读取文件失败: " + e.getMessage()
            );
        }
    }

    /**
     * 校验文件完整性
     *
     * @param relativePath 相对路径
     * @return 校验结果
     */
    public Map<String, Object> verifyFile(String relativePath, String expectedSha256) {
        try {
            File file = new File(baseDir, relativePath);
            if (!file.exists()) {
                return Map.of(
                        "status", "FAILED",
                        "message", "文件不存在: " + relativePath
                );
            }

            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = Files.readAllBytes(file.toPath());
            String actualSha256 = bytesToHex(md.digest(bytes));

            boolean valid = expectedSha256.equalsIgnoreCase(actualSha256);
            return Map.of(
                    "status", valid ? "SUCCESS" : "FAILED",
                    "fileName", file.getName(),
                    "expectedSha256", expectedSha256,
                    "actualSha256", actualSha256,
                    "valid", valid
            );
        } catch (Exception e) {
            return Map.of(
                    "status", "FAILED",
                    "message", "校验失败: " + e.getMessage()
            );
        }
    }

    private String guessEncoding(byte[] content) {
        // Simple heuristic: check for BOM
        if (content.length >= 3 && content[0] == (byte) 0xEF
                && content[1] == (byte) 0xBB && content[2] == (byte) 0xBF) {
            return "UTF-8";
        }
        return "UTF-8";
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
