package com.ops.agent.daemon;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class HeartbeatDaemonTest {

    private HeartbeatDaemon daemon;

    @BeforeEach
    void setUp() {
        daemon = new HeartbeatDaemon();
        ReflectionTestUtils.setField(daemon, "serverUrl", "http://localhost:8081/api");
        ReflectionTestUtils.setField(daemon, "agentToken", "test-token");
        ReflectionTestUtils.setField(daemon, "nodeName", "test-node");
        ReflectionTestUtils.setField(daemon, "checkInterval", 30);
    }

    @Test
    @DisplayName("构造函数 - Token未配置时抛出异常")
    void run_noToken_throwsException() {
        HeartbeatDaemon noTokenDaemon = new HeartbeatDaemon();
        ReflectionTestUtils.setField(noTokenDaemon, "serverUrl", "http://localhost:8081/api");
        ReflectionTestUtils.setField(noTokenDaemon, "agentToken", "");
        ReflectionTestUtils.setField(noTokenDaemon, "nodeName", "default-node");
        ReflectionTestUtils.setField(noTokenDaemon, "checkInterval", 30);

        assertThrows(IllegalStateException.class, () -> noTokenDaemon.run());
    }

    @Test
    @DisplayName("构造函数 - Token配置正常时不抛异常")
    void run_withToken_succeeds() {
        assertDoesNotThrow(() -> daemon.run());
    }

    @Test
    @DisplayName("sendHeartbeat - 不抛异常")
    void sendHeartbeat_noException() {
        // 心跳可能因网络失败，但不应抛未处理异常
        assertDoesNotThrow(() -> daemon.sendHeartbeat());
    }

    @Test
    @DisplayName("hashNodeId - 生成hash节点ID")
    void hashNodeId_generatesHash() {
        String result = daemon.hashNodeId("192.168.1.100");
        assertNotNull(result);
        assertFalse(result.isEmpty());

        String result2 = daemon.hashNodeId("192.168.1.100");
        assertEquals(result, result2); // 相同输入产生相同输出
    }

    @Test
    @DisplayName("hashNodeId - 不同IP产生不同hash")
    void hashNodeId_differentIPs() {
        String hash1 = daemon.hashNodeId("192.168.1.100");
        String hash2 = daemon.hashNodeId("10.0.0.1");

        assertNotEquals(hash1, hash2);
    }

    @Test
    @DisplayName("getTotalMemoryMB - 返回有效的内存值")
    void getTotalMemoryMB_returnsPositiveValue() {
        long mem = getTotalMemoryMB(daemon);
        assertTrue(mem > 0);
        assertTrue(mem < Long.MAX_VALUE / (1024 * 1024)); // 合理的MB值
    }

    private long getTotalMemoryMB(HeartbeatDaemon daemon) {
        try {
            java.lang.reflect.Method m = HeartbeatDaemon.class.getDeclaredMethod("getTotalMemoryMB");
            m.setAccessible(true);
            return (Long) m.invoke(daemon);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("getNodeName - 使用默认值")
    void nodeName_defaultValue() {
        String name = (String) ReflectionTestUtils.getField(daemon, "nodeName");
        assertEquals("test-node", name);
    }
}
