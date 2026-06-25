package com.ops.server.configmgmt.service;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 配置哈希与 Diff 服务
 */
@Service
public class ConfigDiffService {

    /**
     * 计算内容 SHA-256 哈希
     */
    public String sha256(String content) {
        if (content == null) {
            content = "";
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 计算失败", e);
        }
    }

    /**
     * 生成 unified diff
     */
    public String unifiedDiff(String oldContent, String newContent, String oldLabel, String newLabel) {
        List<String> oldLines = splitLines(oldContent);
        List<String> newLines = splitLines(newContent);
        return String.join("\n", UnifiedDiffUtils.generateUnifiedDiff(
                oldLabel, newLabel, oldLines,
                DiffUtils.diff(oldLines, newLines), 3));
    }

    private List<String> splitLines(String content) {
        if (content == null || content.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.asList(content.split("\n", -1));
    }
}
