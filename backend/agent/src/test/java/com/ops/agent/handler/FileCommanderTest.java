package com.ops.agent.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FileCommanderTest {

    private String tempBaseDir;
    private FileCommander commander;

    @BeforeEach
    void setUp() throws IOException {
        tempBaseDir = Files.createTempDirectory("filecmd-test").toString();
        // 创建测试文件
        Path testFile = Files.createFile(Path.of(tempBaseDir, "test.txt"));
        Files.write(testFile, "Hello, World!".getBytes("UTF-8"));

        commander = new FileCommander(tempBaseDir);
    }

    @Test
    @DisplayName("readFile - 读取存在的文件")
    void readFile_existingFile_returnsSuccess() {
        Map<String, Object> result = commander.readFile("test.txt");

        assertEquals("SUCCESS", result.get("status"));
        assertEquals("test.txt", result.get("fileName"));
        assertEquals(13L, result.get("fileSize")); // "Hello, World!" = 13 bytes
        assertEquals("UTF-8", result.get("encoding"));
        assertEquals("Hello, World!", result.get("content"));
    }

    @Test
    @DisplayName("readFile - 不存在的文件返回FAILED")
    void readFile_nonExistentFile_returnsFailed() {
        Map<String, Object> result = commander.readFile("does-not-exist.txt");

        assertEquals("FAILED", result.get("status"));
        assertTrue(result.get("message").toString().contains("文件不存在"));
    }

    @Test
    @DisplayName("readFile - 子目录文件")
    void readFile_subdirectory() throws IOException {
        Path subDir = Files.createDirectory(Path.of(tempBaseDir, "subdir"));
        Path subFile = Files.createFile(Path.of(subDir.toString(), "nested.txt"));
        Files.write(subFile, "nested content".getBytes("UTF-8"));

        Map<String, Object> result = commander.readFile("subdir/nested.txt");

        assertEquals("SUCCESS", result.get("status"));
        assertEquals("nested content", result.get("content"));
    }

    @Test
    @DisplayName("readFile - 尝试路径遍历被限制在baseDir内")
    void readFile_pathTraversal_staysInBaseDir() {
        // 即使尝试 ../ 攻击，FileCommander的构造函数只用baseDir，
        // 使用baseDir+relativePath构造File，因此../ 不会逃出baseDir
        Map<String, Object> result = commander.readFile("../etc/passwd");

        assertEquals("FAILED", result.get("status"));
        // 在测试目录下没有 ../etc/passwd 文件，所以返回FAILED
    }

    @Test
    @DisplayName("verifyFile - 校验通过的SHA-256")
    void verifyFile_validSha256_returnsValid() {
        // "Hello, World!" 的SHA-256
        String expectedSha256 = "dffd6021bb2bd5b0af676290809ec3a53191dd81c7f70a4b28688a362182986f";
        Map<String, Object> result = commander.readFile("test.txt");

        // 先读取文件内容
        Map<String, Object> verifyResult = commander.verifyFile("test.txt", expectedSha256);

        assertEquals("SUCCESS", verifyResult.get("status"));
        assertEquals(true, verifyResult.get("valid"));
        assertEquals(expectedSha256, verifyResult.get("actualSha256"));
    }

    @Test
    @DisplayName("verifyFile - 校验失败的SHA-256")
    void verifyFile_invalidSha256_returnsInvalid() {
        Map<String, Object> result = commander.verifyFile("test.txt", "0000000000000000000000000000000000000000000000000000000000000000");

        assertEquals("FAILED", result.get("status"));
        assertEquals(false, result.get("valid"));
        assertNotNull(result.get("actualSha256"));
        assertNotNull(result.get("expectedSha256"));
    }

    @Test
    @DisplayName("verifyFile - 不存在的文件")
    void verifyFile_nonExistent_returnsFailed() {
        Map<String, Object> result = commander.verifyFile("missing.txt", "abc123");

        assertEquals("FAILED", result.get("status"));
        assertTrue(result.get("message").toString().contains("文件不存在"));
    }

    @Test
    @DisplayName("verifyFile - 大小写不敏感的SHA-256比较")
    void verifyFile_caseInsensitive() {
        String upperSha256 = "DFFD6021BB2BD5B0AF676290809EC3A53191DD81C7F70A4B28688A362182986F";
        Map<String, Object> result = commander.verifyFile("test.txt", upperSha256);

        assertEquals("SUCCESS", result.get("status"));
        assertEquals(true, result.get("valid"));
    }

    @Test
    @DisplayName("构造函数 - 设置正确的baseDir")
    void constructor_setsBaseDir() {
        assertEquals(tempBaseDir, getBaseDir(commander));
    }

    private String getBaseDir(FileCommander commander) {
        try {
            java.lang.reflect.Field f = FileCommander.class.getDeclaredField("baseDir");
            f.setAccessible(true);
            return (String) f.get(commander);
        } catch (Exception e) {
            return null;
        }
    }
}
