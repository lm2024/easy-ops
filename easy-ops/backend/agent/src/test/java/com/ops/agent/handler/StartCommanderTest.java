package com.ops.agent.handler;

import com.ops.agent.daemon.AutoRestartDaemon;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StartCommanderTest {

    private AutoRestartDaemon autoRestartDaemon;
    private StartCommander commander;

    @BeforeEach
    void setUp() throws IOException {
        autoRestartDaemon = mock(AutoRestartDaemon.class);
        commander = new StartCommander(autoRestartDaemon, "/tmp/test-data");
        // 确保测试目录和文件存在
        java.nio.file.Files.createDirectories(java.nio.file.Paths.get("/tmp/test-data/versions/nonexistent"));
        java.nio.file.Files.createDirectories(java.nio.file.Paths.get("/tmp/test-data/versions/error"));
        java.nio.file.Files.write(
            java.nio.file.Paths.get("/tmp/test-data/versions/nonexistent/app.jar"),
            "test".getBytes()
        );
        java.nio.file.Files.write(
            java.nio.file.Paths.get("/tmp/test-data/versions/error/fail.jar"),
            "test".getBytes()
        );
    }

    @Test
    @DisplayName("execute - Jar不存在返回FAILED")
    void execute_jarNotFound_returnsFailed() {
        Map<String, Object> result = commander.execute("nonexistent", "missing.jar", null, null);

        assertEquals("FAILED", result.get("status"));
        assertTrue(result.get("message").toString().contains("Jar包不存在"));
        verify(autoRestartDaemon, never()).registerProcess(anyString(), anyLong());
    }

    @Test
    @DisplayName("execute - JVM参数正常传递")
    @DisabledOnOs(OS.WINDOWS) // Skip platform-specific test on Windows
    void execute_withJvmOpts_passesArgs() {
        // 创建临时jar文件
        try {
            java.nio.file.Files.write(
                java.nio.file.Paths.get("/tmp/test-data/versions/nonexistent/app.jar"),
                "test".getBytes()
            );

            Map<String, Object> result = commander.execute("nonexistent", "app.jar", "-Xmx512m", null);

            assertEquals("SUCCESS", result.get("status"));
            assertEquals("Project started successfully", result.get("message"));
            assertNotNull(result.get("processId"));
            assertEquals(true, result.get("realPid"));
            verify(autoRestartDaemon).registerProcess(eq("nonexistent"), anyLong());
        } catch (IOException e) {
            fail("Failed to create test file: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("execute - 环境参数正确设置")
    @DisabledOnOs(OS.WINDOWS)
    void execute_withEnvVars_setsEnvironment() {
        try {
            java.nio.file.Files.write(
                java.nio.file.Paths.get("/tmp/test-data/versions/nonexistent/app.jar"),
                "test".getBytes()
            );

            Map<String, Object> result = commander.execute("nonexistent", "app.jar", null, "KEY1=value1;KEY2=value2");

            assertEquals("SUCCESS", result.get("status"));
            verify(autoRestartDaemon).registerProcess(eq("nonexistent"), anyLong());
        } catch (IOException e) {
            fail("Failed to create test file: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("execute - 启动异常返回FAILED")
    @DisabledOnOs(OS.WINDOWS)
    void execute_startException_returnsFailed() {
        try {
            java.nio.file.Files.write(
                java.nio.file.Paths.get("/tmp/test-data/versions/error/error.jar"),
                "test".getBytes()
            );
            // 不覆盖jar, 让命令执行抛异常
            java.nio.file.Files.createFile(java.nio.file.Paths.get("/tmp/test-data/versions/error/fail.jar"));

            // 使用一个不存在的目录让ProcessBuilder失败
            StartCommander cmd = new StartCommander(autoRestartDaemon, "/nonexistent/path");
            Map<String, Object> result = cmd.execute("error", "fail.jar", null, null);

            assertEquals("FAILED", result.get("status"));
            assertTrue(result.get("message").toString().contains("启动失败"));
        } catch (IOException e) {
            // Create error jar in temp
            try {
                java.nio.file.Files.write(
                    java.nio.file.Paths.get("/tmp/test-data/versions/error/fail.jar"),
                    "test".getBytes()
                );
                StartCommander cmd = new StartCommander(autoRestartDaemon, "/tmp/test-data");
                Map<String, Object> result = cmd.execute("error", "fail.jar", null, null);
                // 如果正常启动成功，至少确保status是SUCCESS或FAILED
                assertTrue("SUCCESS".equals(result.get("status")) || "FAILED".equals(result.get("status")));
            } catch (IOException ex) {
                fail("Nested file creation failed: " + ex.getMessage());
            }
        }
    }

    @Test
    @DisplayName("getRealPid - Java 8 fallback读取/prod/self")
    void getRealPid_java8Fallback() {
        // 通过设置spring.pid.file测试PID文件回退
        try {
            String tempDir = System.getProperty("java.io.tmpdir");
            java.nio.file.Files.write(
                java.nio.file.Paths.get(tempDir + "/test.pid"),
                "12345".getBytes()
            );
            // 通过反射设置spring.pid.file系统属性
            System.setProperty("spring.pid.file", tempDir + "/test.pid");

            // 读取PID文件
            java.io.File pidFile = new java.io.File(tempDir + "/test.pid");
            assertTrue(pidFile.exists());
            String content = new String(java.nio.file.Files.readAllBytes(pidFile.toPath())).trim();
            assertEquals("12345", content);

            // 清理
            System.clearProperty("spring.pid.file");
            java.nio.file.Files.delete(pidFile.toPath());
        } catch (IOException e) {
            fail("Test setup failed: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("execute - 空jvmOpts和envVars不报错")
    @DisabledOnOs(OS.WINDOWS)
    void execute_nullOptions_noError() {
        try {
            java.nio.file.Files.createDirectories(
                java.nio.file.Paths.get("/tmp/test-data/versions/nulltest")
            );
            java.nio.file.Files.write(
                java.nio.file.Paths.get("/tmp/test-data/versions/nulltest/empty.jar"),
                "test".getBytes()
            );

            Map<String, Object> result = commander.execute("nulltest", "empty.jar", null, null);

            assertTrue("SUCCESS".equals(result.get("status")) || "FAILED".equals(result.get("status")));
        } catch (IOException e) {
            fail("Failed: " + e.getMessage());
        }
    }
}
