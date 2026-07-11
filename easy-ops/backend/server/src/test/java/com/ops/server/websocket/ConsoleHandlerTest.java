package com.ops.server.websocket;

import com.ops.common.model.NodeModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.CloseStatus;
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
        ConsoleAgentClient agentClient = org.mockito.Mockito.mock(ConsoleAgentClient.class);
        org.mockito.Mockito.when(agentClient.resolveCwd(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("/root");
        org.mockito.Mockito.when(agentClient.findNode(org.mockito.ArgumentMatchers.eq("1")))
                .thenReturn(buildNode("1", "test-node"));
        ReflectionTestUtils.setField(handler, "agentClient", agentClient);
    }

    private NodeModel buildNode(String id, String name) {
        NodeModel node = new NodeModel();
        node.setId(Long.parseLong(id));
        node.setName(name);
        node.setIp("127.0.0.1");
        node.setPort(2123);
        return node;
    }

    @Test
    @DisplayName("getSessionGroups - 初始为空")
    void getSessionGroups_initiallyEmpty() {
        Map<String, Map<String, WebSocketSession>> groups = handler.getSessionGroups();
        assertNotNull(groups);
        assertTrue(groups.isEmpty());
    }

    @Test
    @DisplayName("afterConnectionClosed - 移除会话")
    void afterConnectionClosed_removesSession() throws Exception {
        WebSocketSession mockSession = mock(WebSocketSession.class);
        when(mockSession.getUri()).thenReturn(URI.create("ws://localhost/ws/console?projectId=1&nodeId=1"));
        when(mockSession.getId()).thenReturn("close-sess");

        handler.afterConnectionEstablished(mockSession);
        assertFalse(handler.getSessionGroups().isEmpty());

        handler.afterConnectionClosed(mockSession, CloseStatus.NORMAL);
        assertTrue(handler.getSessionGroups().isEmpty());
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
