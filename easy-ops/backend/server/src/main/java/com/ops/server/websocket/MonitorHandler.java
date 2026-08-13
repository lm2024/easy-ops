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

/**
 * 监控数据 WebSocket 处理器。
 * 按租户隔离：非平台管理员只接收本租户节点的实时指标。
 * 平台管理员（sys_user.role=admin）接收所有租户数据。
 */
@Component
public class MonitorHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(MonitorHandler.class);

    // sessionId -> session
    private final Map<String, WebSocketSession> monitorSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(@NotNull WebSocketSession session) {
        log.info("Monitor WebSocket connected: {}", session.getId());
        monitorSessions.put(session.getId(), session);
    }

    @Override
    public void afterConnectionClosed(@NotNull WebSocketSession session, @NotNull CloseStatus status) {
        // 移除该 session 的所有注册记录
        monitorSessions.values().removeIf(s -> s.equals(session));
        log.info("Monitor WebSocket closed: {}", session.getId());
    }

    @Override
    public void handleTransportError(@NotNull WebSocketSession session, @NotNull Throwable exception) {
        log.error("Monitor WebSocket transport error: {}", session.getId(), exception);
        try {
            afterConnectionClosed(session, CloseStatus.SERVER_ERROR);
        } catch (Exception e) {
            log.error("Error cleaning up monitor session after transport error", e);
        }
    }

    /**
     * 广播消息给所有连接的客户端。发送失败的 session 自动清理。
     */
    public void broadcast(String topic, String message) {
        broadcast(topic, message, null);
    }

    public void broadcast(String topic, String message, Long tenantId) {
        // 使用 removeIf 清理死 session + 发送消息一趟完成
        monitorSessions.values().removeIf(session -> {
            if (!session.isOpen()) {
                return true;
            }
            Object sessionTenant = session.getAttributes().get("tenantId");
            Object role = session.getAttributes().get("role");
            // 平台管理员（sys_user.role=admin）接收所有租户的实时指标（平台视图）
            boolean platform = role != null && ("admin".equalsIgnoreCase(String.valueOf(role))
                    || "super_admin".equalsIgnoreCase(String.valueOf(role)));
            if (tenantId != null) {
                // 按节点/资源租户过滤；平台管理员放行
                if (platform) {
                    try {
                        session.sendMessage(new org.springframework.web.socket.TextMessage(message));
                    } catch (IOException e) {
                        log.warn("Monitor broadcast failed for session {}, removing", session.getId());
                        return true;
                    }
                    return false;
                }
                if (sessionTenant == null || !tenantId.toString().equals(String.valueOf(sessionTenant))) {
                    return false;
                }
            }
            try {
                session.sendMessage(new org.springframework.web.socket.TextMessage(message));
                return false;
            } catch (IOException e) {
                log.warn("Monitor broadcast failed for session {}, removing", session.getId());
                return true;
            }
        });
    }

    public Map<String, WebSocketSession> getMonitorSessions() {
        return monitorSessions;
    }
}
