package com.ops.agent.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LogDiscoveryServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void discover_findsVariousLogNames() throws Exception {
        Path appDir = tempDir.resolve("my-app");
        Path logsDir = appDir.resolve("logs");
        Files.createDirectories(logsDir);
        Files.write(logsDir.resolve("spring-boot.log"), "x".getBytes(StandardCharsets.UTF_8));
        Files.write(logsDir.resolve("error.log"), "e".getBytes(StandardCharsets.UTF_8));
        Files.write(appDir.resolve("catalina.out"), "o".getBytes(StandardCharsets.UTF_8));

        Path agentLogs = tempDir.resolve("agent-logs");
        Files.createDirectories(agentLogs);
        Files.write(agentLogs.resolve("agent.log"), "a".getBytes(StandardCharsets.UTF_8));

        LogDiscoveryService service = new LogDiscoveryService();
        Map<String, Object> result = service.discover(
                appDir.toString(), logsDir.toString(), tempDir.toString());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> files = (List<Map<String, Object>>) result.get("files");
        assertTrue(files.size() >= 3);
        assertNotNull(result.get("suggestedMain"));
    }

    @Test
    void isLogLikeFile_matchesProductionNames() {
        assertTrue(LogDiscoveryService.isLogLikeFile("application.log"));
        assertTrue(LogDiscoveryService.isLogLikeFile("app-error.log"));
        assertTrue(LogDiscoveryService.isLogLikeFile("nohup.out"));
        assertFalse(LogDiscoveryService.isLogLikeFile("app.jar"));
    }
}
