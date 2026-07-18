package com.ops.server.selfheal.websocket;

import com.alibaba.fastjson2.JSON;
import com.ops.common.model.NotificationRecordModel;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通知 WebSocket 处理器：向在线用户广播通知
 */
@Component
public class NotificationHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(NotificationHandler.class);

    private final Map<String, WebSocketSession> userSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(@NotNull WebSocketSession session) {
        Object userId = session.getAttributes().get("userId");
        if (userId != null) {
            userSessions.put(String.valueOf(userId), session);
            log.info("Notification WS connected: userId={}", userId);
        }
    }

    @Override
    public void afterConnectionClosed(@NotNull WebSocketSession session, @NotNull CloseStatus status) {
        Object userId = session.getAttributes().get("userId");
        if (userId != null) {
            userSessions.remove(String.valueOf(userId));
        }
        log.info("Notification WS closed: {}", session.getId());
    }

    /**
     * 广播 ALERT 通知给所有在线用户（兼容旧调用）
     */
    public void broadcastAlert(NotificationRecordModel notification) {
        broadcastNewNotification(notification);
    }

    /**
     * 广播新通知给所有在线用户（包括监控告警、自愈告警等所有广播通知）
     */
    public void broadcastNewNotification(NotificationRecordModel notification) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "NEW_NOTIFICATION");
        payload.put("notification", toWsNotification(notification));
        String message = JSON.toJSONString(payload);

        for (Map.Entry<String, WebSocketSession> entry : userSessions.entrySet()) {
            WebSocketSession session = entry.getValue();
            if (session != null && session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(message));
                } catch (IOException e) {
                    log.error("Failed to broadcast notification to user {}", entry.getKey(), e);
                    userSessions.remove(entry.getKey());
                }
            }
        }
    }

    private Map<String, Object> toWsNotification(NotificationRecordModel n) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", n.getId());
        map.put("level", n.getLevel());
        map.put("title", n.getTitle());
        map.put("content", n.getContent());
        map.put("requireAck", n.getRequireAck() != null && n.getRequireAck() == 1);
        map.put("createTime", n.getCreateTime());
        return map;
    }

    public Map<String, WebSocketSession> getUserSessions() {
        return userSessions;
    }
}
