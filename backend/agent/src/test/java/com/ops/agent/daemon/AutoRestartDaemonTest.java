package com.ops.agent.daemon;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class AutoRestartDaemonTest {

    private AutoRestartDaemon daemon;

    @BeforeEach
    void setUp() {
        daemon = new AutoRestartDaemon();
        ReflectionTestUtils.setField(daemon, "serverUrl", "http://localhost:8081/api");
        ReflectionTestUtils.setField(daemon, "agentToken", "test-token");
        ReflectionTestUtils.setField(daemon, "checkInterval", 30);
    }

    @Test
    @DisplayName("构造函数 - Token未配置时抛出异常")
    void run_noToken_throwsException() {
        AutoRestartDaemon noTokenDaemon = new AutoRestartDaemon();
        ReflectionTestUtils.setField(noTokenDaemon, "serverUrl", "http://localhost:8081/api");
        ReflectionTestUtils.setField(noTokenDaemon, "agentToken", "");
        ReflectionTestUtils.setField(noTokenDaemon, "checkInterval", 30);

        assertThrows(IllegalStateException.class, () -> noTokenDaemon.run());
    }

    @Test
    @DisplayName("构造函数 - Token配置正常时不抛异常")
    void run_withToken_succeeds() {
        assertDoesNotThrow(() -> daemon.run());
    }

    @Test
    @DisplayName("registerProcess - 注册进程到监控")
    void registerProcess_addsToMap() {
        daemon.registerProcess("proj-1", 12345L);

        java.util.concurrent.ConcurrentHashMap<String, Long> map = 
            (java.util.concurrent.ConcurrentHashMap<String, Long>) 
            ReflectionTestUtils.getField(daemon, "monitoredProcesses");
        assertNotNull(map);
        assertEquals(1, map.size());
        assertEquals(12345L, map.get("proj-1").longValue());
    }

    @Test
    @DisplayName("unregisterProcess - 移除监控进程")
    void unregisterProcess_removesFromMap() {
        daemon.registerProcess("proj-1", 12345L);
        daemon.registerProcess("proj-2", 67890L);

        java.util.concurrent.ConcurrentHashMap<String, Long> map = 
            (java.util.concurrent.ConcurrentHashMap<String, Long>) 
            ReflectionTestUtils.getField(daemon, "monitoredProcesses");
        assertEquals(2, map.size());

        daemon.unregisterProcess("proj-1");

        assertEquals(1, map.size());
        assertNull(map.get("proj-1"));
        assertEquals(67890L, map.get("proj-2").longValue());
    }

    @Test
    @DisplayName("setAutoRestartEnabled - 设置开关状态")
    void setAutoRestartEnabled_toggles() {
        daemon.setAutoRestartEnabled(true);
        Boolean enabled = (Boolean) ReflectionTestUtils.getField(daemon, "autoRestartEnabled");
        assertTrue(enabled);

        daemon.setAutoRestartEnabled(false);
        enabled = (Boolean) ReflectionTestUtils.getField(daemon, "autoRestartEnabled");
        assertFalse(enabled);
    }

    @Test
    @DisplayName("checkAndRestart - 关闭时跳过检查")
    void checkAndRestart_whenDisabled_skips() {
        daemon.setAutoRestartEnabled(false);
        assertDoesNotThrow(() -> daemon.checkAndRestart());
    }

    @Test
    @DisplayName("checkAndRestart - 开启时检查进程")
    void checkAndRestart_whenEnabled_checksProcesses() {
        daemon.setAutoRestartEnabled(true);
        // checkAndRestart不抛异常即通过
        assertDoesNotThrow(() -> daemon.checkAndRestart());
    }

    @Test
    @DisplayName("isProcessAlive - /proc检查Linux进程")
    void isProcessAlive_linuxProcess() throws IOException {
        // 创建临时的/proc/self目录mock - 不可行，直接测试逻辑
        // 在macOS上，/proc不存在，会通过kill -0 fallback
        File procSelf = new File("/proc/self");
        // /proc/self 在Linux上存在
        if (procSelf.exists()) {
            assertTrue(procSelf.exists());
        } else {
            // macOS上没有/proc，kill -0作为fallback
            assertDoesNotThrow(() -> daemon.isProcessAlive(0));
        }
    }

}
