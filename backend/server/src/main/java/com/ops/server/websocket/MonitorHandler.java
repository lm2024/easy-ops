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
    }

    @Override
    public void afterConnectionClosed(@NotNull WebSocketSession session, @NotNull CloseStatus status) {
        log.info("Monitor WebSocket closed: {}", session.getId());
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
            }
        }
    }

    public Map<String, WebSocketSession> getMonitorSessions() {
        return monitorSessions;
    }
}
