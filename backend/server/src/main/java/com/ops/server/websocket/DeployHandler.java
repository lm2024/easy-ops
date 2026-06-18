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
public class DeployHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(DeployHandler.class);

    // deployId -> session
    private final Map<String, WebSocketSession> deploySessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(@NotNull WebSocketSession session) {
        String deployId = session.getUri().getQuery();
        log.info("Deploy WebSocket connected: {}, deployId: {}", session.getId(), deployId);
    }

    @Override
    public void afterConnectionClosed(@NotNull WebSocketSession session, @NotNull CloseStatus status) {
        log.info("Deploy WebSocket closed: {}", session.getId());
    }

    public void subscribe(String deployId, WebSocketSession session) {
        deploySessions.put(deployId, session);
        log.info("Session {} subscribed to deploy {}", session.getId(), deployId);
    }

    public void push(String deployId, String message) {
        WebSocketSession session = deploySessions.get(deployId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new org.springframework.web.socket.TextMessage(message));
            } catch (IOException e) {
                log.error("Failed to send deploy message to {}", session.getId(), e);
            }
        }
    }

    public Map<String, WebSocketSession> getDeploySessions() {
        return deploySessions;
    }
}
