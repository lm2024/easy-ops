package com.ops.server.websocket;

import com.ops.common.model.NodeModel;
import com.ops.server.mapper.NodeMapper;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ConsoleHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ConsoleHandler.class);

    // projectId -> (nodeId -> session)
    private final Map<String, Map<String, WebSocketSession>> sessionGroups = new ConcurrentHashMap<>();

    @Autowired
    private NodeMapper nodeMapper;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public void afterConnectionEstablished(@NotNull WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) {
            log.warn("Console WebSocket connected without URI: {}", session.getId());
            return;
        }

        String query = uri.getQuery();
        String projectId = null;
        String nodeId = null;
        if (query != null) {
            for (String param : query.split("&")) {
                String[] pair = param.split("=", 2);
                if (pair.length == 2) {
                    if ("projectId".equals(pair[0])) projectId = pair[1];
                    if ("nodeId".equals(pair[0])) nodeId = pair[1];
                }
            }
        }

        if (projectId == null || nodeId == null) {
            log.warn("Console WebSocket missing projectId/nodeId: {}", session.getId());
            try {
                session.close(CloseStatus.BAD_DATA);
            } catch (IOException ignored) {}
            return;
        }

        subscribe(projectId, nodeId, session);
        log.info("Console WebSocket connected: {} for project={}, node={}", session.getId(), projectId, nodeId);

        // 发送欢迎消息
        sendToSession(session, "[控制台已连接 - 项目:" + projectId + " 节点:" + nodeId + "]\n");
    }

    @Override
    protected void handleTextMessage(@NotNull WebSocketSession session, @NotNull TextMessage message) {
        String command = message.getPayload();
        if (command == null || command.trim().isEmpty()) return;

        log.info("Console command from {}: {}", session.getId(), command);

        // 从 sessionGroups 中查找该 session 对应的 projectId 和 nodeId
        String projectId = null;
        String nodeId = null;
        outer:
        for (Map.Entry<String, Map<String, WebSocketSession>> pEntry : sessionGroups.entrySet()) {
            for (Map.Entry<String, WebSocketSession> nEntry : pEntry.getValue().entrySet()) {
                if (nEntry.getValue().equals(session)) {
                    projectId = pEntry.getKey();
                    nodeId = nEntry.getKey();
                    break outer;
                }
            }
        }

        if (projectId == null || nodeId == null) {
            sendToSession(session, "[错误] 未找到会话绑定，请重新连接\n");
            return;
        }

        // 在终端回显命令
        sendToSession(session, "$ " + command + "\n");

        // 调用 Agent 的 Shell API 执行命令
        executeCommand(session, projectId, nodeId, command);
    }

    @Override
    public void afterConnectionClosed(@NotNull WebSocketSession session, @NotNull CloseStatus status) {
        // 从所有订阅中移除该 session
        for (Map.Entry<String, Map<String, WebSocketSession>> pEntry : sessionGroups.entrySet()) {
            String projectId = pEntry.getKey();
            Map<String, WebSocketSession> nodeMap = pEntry.getValue();
            nodeMap.values().removeIf(s -> s.equals(session));
            if (nodeMap.isEmpty()) {
                sessionGroups.remove(projectId);
            }
        }
        log.info("Console WebSocket closed: {}, status: {}", session.getId(), status);
    }

    @Override
    public void handleTransportError(@NotNull WebSocketSession session, @NotNull Throwable exception) {
        log.error("Console WebSocket transport error: {}", session.getId(), exception);
    }

    // ====== 内部方法 ======

    private void subscribe(String projectId, String nodeId, WebSocketSession session) {
        sessionGroups.computeIfAbsent(projectId, k -> new ConcurrentHashMap<>())
                .put(nodeId, session);
    }

    private void executeCommand(WebSocketSession session, String projectId, String nodeId, String command) {
        try {
            // 从数据库查询节点信息
            NodeModel node = nodeMapper.findById(Long.parseLong(nodeId));
            if (node == null) {
                sendToSession(session, "[错误] 节点不存在 (ID: " + nodeId + ")\n");
                return;
            }

            String agentIp = node.getIp() != null ? node.getIp() : "127.0.0.1";
            int agentPort = node.getPort() != null ? node.getPort() : 2123;
            String agentUrl = "http://" + agentIp + ":" + agentPort + "/api/shell/exec";

            Map<String, String> request = new HashMap<>();
            request.put("command", command);

            log.info("Calling agent shell API: {}", agentUrl);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(agentUrl, request, Map.class);

            if (response != null) {
                // Agent 返回 Result 包装: {code, message, data: {stdout, exitCode, elapsed}}
                Object dataObj = response.get("data");
                Map<String, Object> data;
                if (dataObj instanceof Map) {
                    data = (Map<String, Object>) dataObj;
                } else {
                    data = response; // fallback: 直接解析根层次
                }

                Object stdout = data.get("stdout");
                Object exitCode = data.get("exitCode");
                Object elapsed = data.get("elapsed");

                if (stdout != null && !stdout.toString().isEmpty()) {
                    sendToSession(session, stdout.toString());
                    if (!stdout.toString().endsWith("\n")) {
                        sendToSession(session, "\n");
                    }
                }
                sendToSession(session, "\n[进程退出码: " + (exitCode != null ? exitCode : "?") +
                        " | 耗时: " + (elapsed != null ? elapsed : "?") + "ms]\n");
            } else {
                sendToSession(session, "[错误] Agent 无响应\n");
            }
        } catch (Exception e) {
            log.error("Failed to execute command on agent", e);
            sendToSession(session, "[错误] 命令执行失败: " + e.getMessage() + "\n");
        }
    }

    private void sendToSession(WebSocketSession session, String message) {
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(message));
            } catch (IOException e) {
                log.error("Failed to send message to session {}", session.getId(), e);
            }
        }
    }

    public void sendToGroup(String projectId, String nodeId, String message) {
        Map<String, WebSocketSession> nodeMap = sessionGroups.get(projectId);
        if (nodeMap == null) return;
        WebSocketSession session = nodeMap.get(nodeId);
        sendToSession(session, message);
    }

    public Map<String, Map<String, WebSocketSession>> getSessionGroups() {
        return sessionGroups;
    }
}
