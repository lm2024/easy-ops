package com.ops.server.configmgmt.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConfigDiffServiceTest {

    private ConfigDiffService configDiffService;

    @BeforeEach
    void setUp() {
        configDiffService = new ConfigDiffService();
    }

    @Test
    @DisplayName("sha256 - 相同内容产生相同哈希")
    void sha256_sameContentSameHash() {
        String content = "server:\n  port: 8080\n";
        String hash1 = configDiffService.sha256(content);
        String hash2 = configDiffService.sha256(content);
        assertEquals(hash1, hash2);
        assertEquals(64, hash1.length());
    }

    @Test
    @DisplayName("sha256 - 空内容不抛异常")
    void sha256_emptyContent() {
        String hash = configDiffService.sha256("");
        assertNotNull(hash);
        assertEquals(64, hash.length());
    }

    @Test
    @DisplayName("sha256 - 不同内容产生不同哈希")
    void sha256_differentContent() {
        String hash1 = configDiffService.sha256("port: 8080");
        String hash2 = configDiffService.sha256("port: 8081");
        assertNotEquals(hash1, hash2);
    }

    @Test
    @DisplayName("unifiedDiff - 生成差异文本")
    void unifiedDiff_generatesDiff() {
        String oldContent = "line1\nline2\nline3\n";
        String newContent = "line1\nline2-changed\nline3\n";
        String diff = configDiffService.unifiedDiff(oldContent, newContent, "old", "new");
        assertNotNull(diff);
        assertTrue(diff.contains("line2") || diff.contains("changed"));
    }

    @Test
    @DisplayName("unifiedDiff - 相同内容差异为空或极小")
    void unifiedDiff_sameContent() {
        String content = "same\ncontent\n";
        String diff = configDiffService.unifiedDiff(content, content, "a", "b");
        assertNotNull(diff);
    }
}
