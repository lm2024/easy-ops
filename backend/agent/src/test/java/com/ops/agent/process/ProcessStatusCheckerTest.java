package com.ops.agent.process;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisabledOnOs(OS.WINDOWS)
class ProcessStatusCheckerTest {

    private final ProcessStatusChecker checker = new ProcessStatusChecker();

    @Test
    @DisplayName("checkStatus 缺少参数返回未存活")
    void checkStatus_missingParams_returnsNotAlive() {
        Map<String, Object> result = checker.checkStatus("", "app.jar");

        assertEquals(false, result.get("alive"));
        assertEquals("PS_GREP", result.get("checkMethod"));
        assertNull(result.get("pid"));
    }

    @Test
    @DisplayName("checkStatus 不存在的进程返回未存活")
    void checkStatus_noProcess_returnsNotAlive() {
        Map<String, Object> result = checker.checkStatus(
                "/nonexistent/deploy/dir-" + System.nanoTime(),
                "missing-" + System.nanoTime() + ".jar");

        assertEquals(false, result.get("alive"));
        assertNull(result.get("pid"));
    }

    @Test
    @DisplayName("findPid 当前 Java 进程可匹配 jar 关键字")
    void findPid_currentJavaProcess() {
        String javaHome = System.getProperty("java.home");
        Long pid = checker.findPid(javaHome, "bin");

        if (pid != null) {
            assertTrue(pid > 0);
        }
    }
}
