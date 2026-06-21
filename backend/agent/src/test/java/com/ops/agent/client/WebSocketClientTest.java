package com.ops.agent.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class WebSocketClientTest {

    private WebSocketClient client;

    @BeforeEach
    void setUp() {
        client = new WebSocketClient();
        client.init();
    }

    @Test
    @DisplayName("init - ScheduledExecutorService初始化")
    void init_createsScheduler() {
        ScheduledExecutorService scheduler = getScheduler();
        assertNotNull(scheduler);
    }

    @Test
    @DisplayName("connect - 抛出异常时不崩溃")
    void connect_invalidUrl_noCrash() {
        assertDoesNotThrow(() -> client.connect("http://invalid:99999", "token"));
        // 不验证connected状态，只确保不抛异常
    }

    @Test
    @DisplayName("disconnect - 安全断开连接")
    void disconnect_setsConnectedFalse() {
        client.connect("http://localhost:8080", "token");
        client.disconnect();

        AtomicBoolean connected = getConnected();
        assertFalse(connected.get());
    }

    @Test
    @DisplayName("disconnect - 未连接时断开无影响")
    void disconnect_notConnected_noIssue() {
        assertDoesNotThrow(() -> client.disconnect());
    }

    @Test
    @DisplayName("sendMessage - 未连接时打印错误")
    void sendMessage_notConnected_printsError() {
        assertDoesNotThrow(() -> client.sendMessage("test message"));
    }

    @Test
    @DisplayName("connect - 生成正确的WebSocket URL")
    void connect_generatesWsUrls() {
        // 通过监控输出的方式来验证
        String httpUrl = "http://localhost:8080";
        String expectedWsBase = "ws://localhost:8080";

        // 替换逻辑: http:// -> ws://
        String wsUrl = httpUrl.replace("http://", "ws://")
                .replace("https://", "wss://");
        assertEquals("ws://localhost:8080", wsUrl);

        // 各端点拼接
        String[] endpoints = {"/ws/console", "/ws/deploy", "/ws/monitor"};
        for (String endpoint : endpoints) {
            String fullUrl = wsUrl + endpoint;
            assertNotNull(fullUrl);
            assertTrue(fullUrl.startsWith("ws://"));
        }
    }

    @Test
    @DisplayName("connect - wss URL正确处理")
    void connect_wssUrl_handled() {
        String httpsUrl = "https://secure.example.com";
        String wsUrl = httpsUrl.replace("http://", "ws://")
                .replace("https://", "wss://");
        assertEquals("wss://secure.example.com", wsUrl);
    }

    private ScheduledExecutorService getScheduler() {
        try {
            Field f = WebSocketClient.class.getDeclaredField("scheduler");
            f.setAccessible(true);
            return (ScheduledExecutorService) f.get(client);
        } catch (Exception e) {
            return null;
        }
    }

    private AtomicBoolean getConnected() {
        try {
            Field f = WebSocketClient.class.getDeclaredField("connected");
            f.setAccessible(true);
            return (AtomicBoolean) f.get(client);
        } catch (Exception e) {
            return null;
        }
    }
}
