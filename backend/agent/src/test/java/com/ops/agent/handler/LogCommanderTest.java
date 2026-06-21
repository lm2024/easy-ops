package com.ops.agent.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LogCommanderTest {

    private String tempLogDir;
    private LogCommander commander;

    @BeforeEach
    void setUp() throws IOException {
        tempLogDir = Files.createTempDirectory("logcmd-test").toString();
        commander = new LogCommander(tempLogDir);
    }

    @Test
    @DisplayName("getLog - 读取存在的日志文件")
    void getLog_existingLog_returnsSuccess() throws IOException {
        Path logFile = Files.createFile(Path.of(tempLogDir, "app.log"));
        Files.write(logFile, (
            "2026-01-01 INFO Starting application\n" +
            "2026-01-01 INFO Listening on port 8080\n" +
            "2026-01-01 WARN Low memory\n" +
            "2026-01-01 ERROR Connection failed\n" +
            "2026-01-01 INFO Retrying connection\n"
        ).getBytes("UTF-8"));

        Map<String, Object> result = commander.getLog("app.log", 0, 3);

        assertEquals("SUCCESS", result.get("status"));
        List<String> lines = (List<String>) result.get("lines");
        assertNotNull(lines);
        assertEquals(3, lines.size());
        assertEquals("2026-01-01 INFO Starting application", lines.get(0));
        assertEquals("2026-01-01 INFO Listening on port 8080", lines.get(1));
        assertEquals("2026-01-01 WARN Low memory", lines.get(2));
        assertEquals(5, result.get("totalLines"));
    }

    @Test
    @DisplayName("getLog - 偏移量读取")
    void getLog_withOffset_returnsFromOffset() throws IOException {
        Path logFile = Files.createFile(Path.of(tempLogDir, "app.log"));
        Files.write(logFile, (
            "line1\nline2\nline3\nline4\nline5\n"
        ).getBytes("UTF-8"));

        Map<String, Object> result = commander.getLog("app.log", 2, 2);

        assertEquals("SUCCESS", result.get("status"));
        List<String> lines = (List<String>) result.get("lines");
        assertEquals(2, lines.size());
        assertEquals("line3", lines.get(0));
        assertEquals("line4", lines.get(1));
    }

    @Test
    @DisplayName("getLog - 请求行数超过实际行数")
    void getLog_moreLinesThanAvailable() throws IOException {
        Path logFile = Files.createFile(Path.of(tempLogDir, "app.log"));
        Files.write(logFile, "a\nb\n".getBytes("UTF-8"));

        Map<String, Object> result = commander.getLog("app.log", 0, 100);

        assertEquals("SUCCESS", result.get("status"));
        List<String> lines = (List<String>) result.get("lines");
        assertEquals(2, lines.size()); // 只返回存在的行数
    }

    @Test
    @DisplayName("getLog - 不存在的日志文件")
    void getLog_nonExistentLog_returnsFailed() {
        Map<String, Object> result = commander.getLog("missing.log", 0, 10);

        assertEquals("FAILED", result.get("status"));
        assertNotNull(result.get("message"));
        assertTrue(result.get("message").toString().contains("读取日志失败"));
    }

    @Test
    @DisplayName("getLog - 大偏移量返回空列表")
    void getLog_largeOffset_returnsEmpty() throws IOException {
        Path logFile = Files.createFile(Path.of(tempLogDir, "app.log"));
        Files.write(logFile, "short\nlog\n".getBytes("UTF-8"));

        Map<String, Object> result = commander.getLog("app.log", 100, 10);

        assertEquals("SUCCESS", result.get("status"));
        List<String> lines = (List<String>) result.get("lines");
        assertTrue(lines.isEmpty());
        assertEquals(2, result.get("totalLines"));
    }

    @Test
    @DisplayName("构造函数 - 设置正确的logPath")
    void constructor_setsLogPath() {
        assertEquals(tempLogDir, getLogPath(commander));
    }

    private String getLogPath(LogCommander commander) {
        try {
            java.lang.reflect.Field f = LogCommander.class.getDeclaredField("logPath");
            f.setAccessible(true);
            return (String) f.get(commander);
        } catch (Exception e) {
            return null;
        }
    }
}
