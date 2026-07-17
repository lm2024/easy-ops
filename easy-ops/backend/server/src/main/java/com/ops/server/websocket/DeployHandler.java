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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 部署进度 WebSocket 推送
 * 前端连接 /ws/deploy?deployId=xxx，后端按 deployId 推送实时进度
 */
@Component
public class DeployHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(DeployHandler.class);

    // deployId -> 订阅了该部署的 WebSocket 会话集合
    private final ConcurrentHashMap<String, Set<WebSocketSession>> deploySessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(@NotNull WebSocketSession session) {
        String query = session.getUri() != null ? session.getUri().getQuery() : null;
        String deployId = null;
        if (query != null) {
            for (String param : query.split("&")) {
                String[] kv = param.split("=", 2);
                if (kv.length == 2 && "deployId".equals(kv[0])) {
                    deployId = kv[1];
                    break;
                }
            }
        }
        if (deployId != null && !deployId.isEmpty()) {
            subscribe(deployId, session);
            log.info("Deploy WebSocket connected: session={}, deployId={}", session.getId(), deployId);
        } else {
            log.warn("Deploy WebSocket connected without deployId: {}", session.getId());
        }
    }

    @Override
    public void afterConnectionClosed(@NotNull WebSocketSession session, @NotNull CloseStatus status) {
        // 从所有 deployId 中移除该 session
        for (Map.Entry<String, Set<WebSocketSession>> entry : deploySessions.entrySet()) {
            entry.getValue().remove(session);
        }
        log.info("Deploy WebSocket closed: session={}, status={}", session.getId(), status);
    }

    @Override
    public void handleTransportError(@NotNull WebSocketSession session, @NotNull Throwable exception) {
        log.error("Deploy WebSocket transport error: session={}", session.getId(), exception);
        afterConnectionClosed(session, CloseStatus.SERVER_ERROR);
    }

    /**
     * 前端连接时按 deployId 订阅
     */
    public void subscribe(String deployId, WebSocketSession session) {
        deploySessions.computeIfAbsent(deployId, k -> new CopyOnWriteArraySet<>()).add(session);
    }

    /**
     * 向订阅了该 deployId 的所有 session 推送消息
     */
    public void push(String deployId, String message) {
        Set<WebSocketSession> sessions = deploySessions.get(deployId);
        if (sessions == null || sessions.isEmpty()) return;

        TextMessage textMsg = new TextMessage(message);
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(textMsg);
                } catch (IOException e) {
                    log.error("Failed to send deploy progress to session {}", session.getId(), e);
                    sessions.remove(session);
                }
            }
        }
    }

    /**
     * 部署完成后清理订阅
     */
    public void cleanup(String deployId) {
        Set<WebSocketSession> sessions = deploySessions.remove(deployId);
        if (sessions != null) {
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    try {
                        session.close();
                    } catch (IOException e) {
                        log.error("Failed to close deploy WebSocket session", e);
                    }
                }
            }
        }
    }
}
