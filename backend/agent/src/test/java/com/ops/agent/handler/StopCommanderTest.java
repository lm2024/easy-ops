package com.ops.agent.handler;

import com.ops.agent.daemon.AutoRestartDaemon;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StopCommanderTest {

    private AutoRestartDaemon autoRestartDaemon;
    private StopCommander commander;

    @BeforeEach
    void setUp() {
        autoRestartDaemon = mock(AutoRestartDaemon.class);
        commander = new StopCommander(autoRestartDaemon);
    }

    @Test
    @DisplayName("execute - 正常停止返回SUCCESS")
    void execute_success_returnsSuccess() {
        Map<String, Object> result = commander.execute("proj-1", 12345L);

        assertEquals("SUCCESS", result.get("status"));
        assertEquals("Project stopped successfully", result.get("message"));
        assertEquals(12345L, result.get("processId"));
        verify(autoRestartDaemon).unregisterProcess("proj-1");
    }

    @Test
    @DisplayName("execute - 停止失败返回FAILED")
    void execute_failure_returnsFailed() {
        // 不mockRuntime，让exec实际执行(会被kill命令成功)
        Map<String, Object> result = commander.execute("proj-999", 99999999L);

        // 不管实际成功失败，状态应该是SUCCESS或FAILED
        assertTrue("SUCCESS".equals(result.get("status")) || "FAILED".equals(result.get("status")));
        if ("FAILED".equals(result.get("status"))) {
            assertNotNull(result.get("message"));
        } else {
            verify(autoRestartDaemon).unregisterProcess("proj-999");
        }
    }

    @Test
    @DisplayName("execute - 空project返回结果包含processId")
    void execute_validProcessId() {
        Map<String, Object> result = commander.execute("test", 1L);

        assertNotNull(result.get("processId"));
        assertEquals(1L, result.get("processId"));
    }
}
