package com.ops.server.websocket;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private com.ops.server.mapper.DeployRecordMapper deployRecordMapper;

    @Autowired
    private com.ops.server.mapper.ProjectMapper projectMapper;

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
            // 租户归属校验：非平台用户只能订阅本租户的部署
            if (!canAccessDeploy(session, deployId)) {
                log.warn("Deploy WS rejected: session={} deployId={} (tenant mismatch)", session.getId(), deployId);
                try {
                    session.close(CloseStatus.POLICY_VIOLATION);
                } catch (IOException ignored) {
                }
                return;
            }
            subscribe(deployId, session);
            log.info("Deploy WebSocket connected: session={}, deployId={}", session.getId(), deployId);
        } else {
            log.warn("Deploy WebSocket connected without deployId: {}", session.getId());
        }
    }

    /** 校验会话所属租户与该部署所属租户一致（平台管理员放行；旧数据不阻断） */
    private boolean canAccessDeploy(WebSocketSession session, String deployId) {
        try {
            Object role = session.getAttributes().get("role");
            boolean platform = role != null && ("admin".equalsIgnoreCase(String.valueOf(role))
                    || "super_admin".equalsIgnoreCase(String.valueOf(role)));
            if (platform) return true;
            Object sessionTenant = session.getAttributes().get("tenantId");
            if (sessionTenant == null) return true;
            com.ops.common.model.DeployModel deploy = deployRecordMapper.findById(Long.parseLong(deployId));
            if (deploy == null) return false;
            Long deployTenant = null;
            if (deploy.getProjectId() != null) {
                com.ops.common.model.ProjectModel project = projectMapper.findById(deploy.getProjectId());
                deployTenant = project == null ? null : project.getTenantId();
            }
            if (deployTenant == null || deployTenant == 0L) return true; // 旧数据不阻断
            return deployTenant.toString().equals(String.valueOf(sessionTenant));
        } catch (Exception e) {
            log.warn("Deploy WS ownership check failed: {}", e.getMessage());
            return true; // 校验异常时放行，避免阻断部署进度展示
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
