package com.ops.server.websocket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConsoleHandlerTest {

    private ConsoleHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ConsoleHandler();
    }

    @Test
    @DisplayName("getSessionGroups - 初始为空")
    void getSessionGroups_initiallyEmpty() {
        Map<String, Map<String, WebSocketSession>> groups = handler.getSessionGroups();
        assertNotNull(groups);
        assertTrue(groups.isEmpty());
    }

    @Test
    @DisplayName("sendToGroup - 空组不抛异常")
    void sendToGroup_emptyGroups_noOp() {
        assertDoesNotThrow(() -> handler.sendToGroup("proj-1", "node-1", "message"));
    }

    @Test
    @DisplayName("afterConnectionEstablished - 注册会话到组")
    void afterConnectionEstablished_registersSession() throws Exception {
        WebSocketSession mockSession = mock(WebSocketSession.class);
        when(mockSession.getUri()).thenReturn(URI.create("ws://localhost/ws/console?projectId=1&nodeId=1"));
        when(mockSession.getId()).thenReturn("new-sess");
        when(mockSession.isOpen()).thenReturn(true);

        handler.afterConnectionEstablished(mockSession);

        Map<String, Map<String, WebSocketSession>> groups = handler.getSessionGroups();
        assertFalse(groups.isEmpty());
        assertTrue(groups.containsKey("1"));
        assertTrue(groups.get("1").containsKey("1"));
    }

    @Test
    @DisplayName("handleTransportError - 打印错误日志")
    void handleTransportError_printsError() throws Exception {
        WebSocketSession mockSession = mock(WebSocketSession.class);
        when(mockSession.getUri()).thenReturn(URI.create("ws://localhost/ws/console?projectId=1&nodeId=1"));
        when(mockSession.getId()).thenReturn("error-sess");

        handler.handleTransportError(mockSession, new RuntimeException("transport error"));
    }
}
