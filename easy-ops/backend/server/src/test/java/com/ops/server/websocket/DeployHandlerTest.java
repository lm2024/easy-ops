package com.ops.server.websocket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeployHandlerTest {

    private DeployHandler handler;

    @BeforeEach
    void setUp() {
        handler = new DeployHandler();
    }

    @Test
    @DisplayName("getDeploySessions - 初始为空")
    void getDeploySessions_initiallyEmpty() {
        Map<String, WebSocketSession> sessions = handler.getDeploySessions();
        assertNotNull(sessions);
        assertTrue(sessions.isEmpty());
    }

    @Test
    @DisplayName("subscribe - 添加会话到组")
    void subscribe_addsSession() {
        WebSocketSession mockSession = mock(WebSocketSession.class);

        handler.subscribe("deploy-1", mockSession);

        Map<String, WebSocketSession> sessions = handler.getDeploySessions();
        assertEquals(1, sessions.size());
        assertTrue(sessions.containsKey("deploy-1"));
    }

    @Test
    @DisplayName("subscribe - 同一部署ID覆盖旧会话")
    void subscribe_overwritesOldSession() {
        WebSocketSession oldSession = mock(WebSocketSession.class);
        WebSocketSession newSession = mock(WebSocketSession.class);

        handler.subscribe("deploy-1", oldSession);
        handler.subscribe("deploy-1", newSession);

        Map<String, WebSocketSession> sessions = handler.getDeploySessions();
        assertEquals(1, sessions.size());
        assertSame(newSession, sessions.get("deploy-1"));
    }

    @Test
    @DisplayName("push - 会话不存在时不抛异常")
    void push_nonExistingSession_noOp() {
        assertDoesNotThrow(() -> handler.push("non-existing", "message"));
    }

    @Test
    @DisplayName("push - 发送给已关闭的会话跳过")
    void push_closedSession_skipped() {
        try {
            WebSocketSession closedSession = mock(WebSocketSession.class);
            when(closedSession.isOpen()).thenReturn(false);
            handler.subscribe("deploy-1", closedSession);
            handler.push("deploy-1", "message");
        } catch (Exception e) {
            fail("Should not throw: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("afterConnectionEstablished - 记录连接")
    void afterConnectionEstablished_records() throws Exception {
        WebSocketSession mockSession = mock(WebSocketSession.class);
        when(mockSession.getUri()).thenReturn(URI.create("ws://localhost/ws/deploy?deploy-1"));
        when(mockSession.getId()).thenReturn("sess-1");
        
        handler.afterConnectionEstablished(mockSession);
        assertEquals(1, handler.getDeploySessions().size());
    }

    @Test
    @DisplayName("afterConnectionClosed - 移除会话")
    void afterConnectionClosed_removesSession() throws Exception {
        WebSocketSession mockSession = mock(WebSocketSession.class);
        when(mockSession.getUri()).thenReturn(URI.create("ws://localhost/ws/deploy?deploy-1"));
        handler.subscribe("deploy-1", mockSession);
        handler.afterConnectionClosed(mockSession, org.springframework.web.socket.CloseStatus.NORMAL);
        assertTrue(handler.getDeploySessions().isEmpty());
    }

    @Test
    @DisplayName("handleTransportError - 清理会话")
    void handleTransportError_cleansUp() throws Exception {
        WebSocketSession mockSession = mock(WebSocketSession.class);
        when(mockSession.getUri()).thenReturn(URI.create("ws://localhost/ws/deploy?deploy-1"));
        when(mockSession.getId()).thenReturn("error-sess");
        handler.subscribe("deploy-1", mockSession);
        handler.handleTransportError(mockSession, new RuntimeException("transport error"));
    }
}
