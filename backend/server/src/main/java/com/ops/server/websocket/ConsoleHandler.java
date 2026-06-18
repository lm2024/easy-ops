package com.ops.server.websocket;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ConsoleHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ConsoleHandler.class);

    // projectId + nodeId -> list of sessions
    private final Map<String, Map<String, WebSocketSession>> sessionGroups = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(@NotNull WebSocketSession session) {
        log.info("Console WebSocket connected: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(@NotNull WebSocketSession session, @NotNull TextMessage message) {
        log.debug("Console message: {}", message.getPayload());
    }

    @Override
    public void afterConnectionClosed(@NotNull WebSocketSession session, @NotNull CloseStatus status) {
        log.info("Console WebSocket closed: {}", session.getId());
    }

    public void subscribe(String projectId, String nodeId, WebSocketSession session) {
        String key = projectId + ":" + nodeId;
        sessionGroups.computeIfAbsent(projectId, k -> new ConcurrentHashMap<>())
                .put(nodeId, session);
        log.info("Session {} subscribed to {}", session.getId(), key);
    }

    public void unsubscribe(String projectId, String nodeId, WebSocketSession session) {
        Map<String, WebSocketSession> nodeMap = sessionGroups.get(projectId);
        if (nodeMap != null) {
            nodeMap.remove(nodeId);
        }
        log.info("Session {} unsubscribed from {}:{}, sessions left: {}", session.getId(), projectId, nodeId, nodeMap);
    }

    public void sendToGroup(String projectId, String nodeId, String message) {
        Map<String, WebSocketSession> nodeMap = sessionGroups.get(projectId);
        if (nodeMap == null) return;
        WebSocketSession session = nodeMap.get(nodeId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(message));
            } catch (IOException e) {
                log.error("Failed to send to {}", session.getId(), e);
            }
        }
    }

    public Map<String, Map<String, WebSocketSession>> getSessionGroups() {
        return sessionGroups;
    }
}
