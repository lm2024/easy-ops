package com.ops.server.websocket;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MonitorHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(MonitorHandler.class);

    // nodeId -> session
    private final Map<String, WebSocketSession> monitorSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(@NotNull WebSocketSession session) {
        log.info("Monitor WebSocket connected: {}", session.getId());
        subscribe(String.valueOf(session.getId()), session);
    }

    @Override
    public void afterConnectionClosed(@NotNull WebSocketSession session, @NotNull CloseStatus status) {
        // 清理所有指向该 session 的订阅
        for (Map.Entry<String, WebSocketSession> entry : monitorSessions.entrySet()) {
            if (entry.getValue().equals(session)) {
                monitorSessions.remove(entry.getKey());
                log.info("Monitor WebSocket session {} removed from nodeId {}", session.getId(), entry.getKey());
                break;
            }
        }
        log.info("Monitor WebSocket closed: {}", session.getId());
    }

    @Override
    public void handleTransportError(@NotNull WebSocketSession session, @NotNull Throwable exception) {
        log.error("Monitor WebSocket transport error: {}", session.getId(), exception);
        // 传输异常后清理会话
        try {
            afterConnectionClosed(session, CloseStatus.SERVER_ERROR);
        } catch (Exception e) {
            log.error("Error cleaning up monitor session after transport error", e);
        }
    }

    public void subscribe(String nodeId, WebSocketSession session) {
        monitorSessions.put(nodeId, session);
    }

    public void push(String nodeId, String message) {
        WebSocketSession session = monitorSessions.get(nodeId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new org.springframework.web.socket.TextMessage(message));
            } catch (IOException e) {
                log.error("Failed to send monitor message", e);
                // 发送失败时清理会话
                monitorSessions.remove(nodeId);
            }
        }
    }

    /**
     * 广播消息给所有连接的客户端
     */
    public void broadcast(String topic, String message) {
        for (WebSocketSession session : monitorSessions.values()) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new org.springframework.web.socket.TextMessage(message));
                } catch (IOException e) {
                    log.error("Failed to broadcast monitor message", e);
                }
            }
        }
    }

    public Map<String, WebSocketSession> getMonitorSessions() {
        return monitorSessions;
    }
}
