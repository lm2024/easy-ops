package com.ops.server.websocket;

import com.alibaba.fastjson2.JSON;
import com.ops.common.model.NodeModel;
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
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 控制台 WebSocket：JSON 协议 + Agent 远程 Shell。
 */
@Component
public class ConsoleHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ConsoleHandler.class);

    private final Map<String, Map<String, WebSocketSession>> sessionGroups = new ConcurrentHashMap<>();
    private final Map<String, SessionState> sessionState = new ConcurrentHashMap<>();

    @Autowired
    private ConsoleAgentClient agentClient;

    @Override
    public void afterConnectionEstablished(@NotNull WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) {
            return;
        }

        String projectId = null;
        String nodeId = null;
        String query = uri.getQuery();
        if (query != null) {
            for (String param : query.split("&")) {
                String[] pair = param.split("=", 2);
                if (pair.length == 2) {
                    if ("projectId".equals(pair[0])) {
                        projectId = pair[1];
                    }
                    if ("nodeId".equals(pair[0])) {
                        nodeId = pair[1];
                    }
                }
            }
        }

        if (projectId == null || nodeId == null) {
            closeQuietly(session, CloseStatus.BAD_DATA);
            return;
        }

        NodeModel node = agentClient.findNode(nodeId);
        if (node == null) {
            sendJson(session, event("error", "text", "节点不存在，请刷新页面后重试"));
            closeQuietly(session, CloseStatus.BAD_DATA);
            return;
        }
        String nodeName = node.getName();
        String cwd;
        try {
            cwd = agentClient.resolveCwd(nodeId);
        } catch (Exception e) {
            log.warn("Console init failed node={}: {}", nodeId, e.getMessage());
            String endpoint = (node.getIp() != null ? node.getIp() : "127.0.0.1") + ":"
                    + (node.getPort() != null ? node.getPort() : 2123);
            sendJson(session, event("error", "text",
                    "无法连接 Agent (" + endpoint + ")，请确认 Docker/Agent 已启动且节点在线"));
            closeQuietly(session, CloseStatus.SERVER_ERROR);
            return;
        }

        SessionState state = new SessionState(projectId, nodeId, nodeName, cwd);
        sessionState.put(session.getId(), state);
        subscribe(projectId, nodeId, session);

        log.info("Console connected session={} project={} node={} cwd={}", session.getId(), projectId, nodeId, cwd);
        sendJson(session, event("init",
                "projectId", projectId,
                "nodeId", nodeId,
                "nodeName", nodeName,
                "cwd", cwd));
    }

    @Override
    protected void handleTextMessage(@NotNull WebSocketSession session, @NotNull TextMessage message) {
        String payload = message.getPayload();
        if (payload == null || payload.trim().isEmpty()) {
            return;
        }

        SessionState state = sessionState.get(session.getId());
        if (state == null) {
            sendJson(session, event("error", "text", "会话未初始化，请重新连接"));
            return;
        }

        if (payload.trim().startsWith("{")) {
            handleJsonMessage(session, state, payload);
            return;
        }

        // 兼容旧版纯文本命令
        handleExec(session, state, payload.trim());
    }

    @Override
    public void afterConnectionClosed(@NotNull WebSocketSession session, @NotNull CloseStatus status) {
        sessionState.remove(session.getId());
        synchronized (sessionGroups) {
            for (Map.Entry<String, Map<String, WebSocketSession>> pEntry : sessionGroups.entrySet()) {
                Map<String, WebSocketSession> nodeMap = pEntry.getValue();
                synchronized (nodeMap) {
                    nodeMap.values().removeIf(s -> s.equals(session));
                    if (nodeMap.isEmpty()) {
                        sessionGroups.remove(pEntry.getKey());
                    }
                }
            }
        }
        log.info("Console closed session={} status={}", session.getId(), status);
    }

    @Override
    public void handleTransportError(@NotNull WebSocketSession session, @NotNull Throwable exception) {
        log.error("Console transport error session={}", session.getId(), exception);
        afterConnectionClosed(session, CloseStatus.SERVER_ERROR);
    }

    private void handleJsonMessage(WebSocketSession session, SessionState state, String payload) {
        try {
            Map<String, Object> json = JSON.parseObject(payload);
            String type = String.valueOf(json.getOrDefault("type", ""));
            if ("exec".equals(type)) {
                handleExec(session, state, String.valueOf(json.getOrDefault("command", "")).trim());
            } else if ("complete".equals(type)) {
                handleComplete(session, state, json);
            } else {
                sendJson(session, event("error", "text", "未知消息类型: " + type));
            }
        } catch (Exception e) {
            sendJson(session, event("error", "text", "消息解析失败: " + e.getMessage()));
        }
    }

    private void handleExec(WebSocketSession session, SessionState state, String command) {
        if (command.isEmpty()) {
            return;
        }
        log.info("Console exec node={} cmd={}", state.nodeId, command);

        String normalized = normalizeCommand(command);
        if ("pwd".equals(normalized)) {
            sendJson(session, event("output", "text", state.cwd + "\n"));
            sendMeta(session, state, 0, 0L);
            return;
        }
        if (normalized.startsWith("cd")) {
            handleCd(session, state, normalized);
            return;
        }
        if ("clear".equals(normalized) || "cls".equals(normalized)) {
            sendJson(session, event("clear"));
            sendMeta(session, state, 0, 0L);
            return;
        }

        String wrapped = "cd " + shellQuote(state.cwd) + " && " + normalized;
        try {
            Map<String, Object> response = agentClient.exec(state.nodeId, wrapped);
            writeExecResult(session, state, response);
        } catch (Exception e) {
            sendJson(session, event("error", "text", "命令执行失败: " + e.getMessage()));
            sendMeta(session, state, 1, 0L);
        }
    }

    private void handleCd(WebSocketSession session, SessionState state, String command) {
        String target = command.length() > 2 ? command.substring(2).trim() : "";
        String cdCmd;
        if (target.isEmpty() || "~".equals(target)) {
            cdCmd = "cd ~ && pwd";
        } else if (target.startsWith("/")) {
            cdCmd = "cd " + shellQuote(target) + " && pwd";
        } else {
            cdCmd = "cd " + shellQuote(state.cwd) + " && cd " + shellQuote(target) + " && pwd";
        }
        try {
            Map<String, Object> response = agentClient.exec(state.nodeId, cdCmd);
            String stdout = agentClient.extractStdout(response);
            Integer exitCode = agentClient.extractExitCode(response);
            if (stdout != null && !stdout.trim().isEmpty() && (exitCode == null || exitCode == 0)) {
                state.cwd = stdout.trim().split("\n")[0].trim();
                sendJson(session, event("output", "text", state.cwd + "\n"));
                sendMeta(session, state, 0, agentClient.extractElapsed(response));
            } else {
                sendJson(session, event("error", "text", "目录不存在或无法进入: " + (target.isEmpty() ? "~" : target)));
                sendMeta(session, state, exitCode != null ? exitCode : 1, agentClient.extractElapsed(response));
            }
        } catch (Exception e) {
            sendJson(session, event("error", "text", "cd 失败: " + e.getMessage()));
            sendMeta(session, state, 1, 0L);
        }
    }

    private void handleComplete(WebSocketSession session, SessionState state, Map<String, Object> json) {
        String line = String.valueOf(json.getOrDefault("line", ""));
        int cursor = parseInt(json.get("cursor"), line.length());
        try {
            List<String> candidates = agentClient.complete(state.nodeId, state.cwd, line, cursor);
            sendJson(session, event("complete", "candidates", candidates));
        } catch (Exception e) {
            sendJson(session, event("complete", "candidates", java.util.Collections.emptyList()));
        }
    }

    private void writeExecResult(WebSocketSession session, SessionState state, Map<String, Object> response) {
        String stdout = agentClient.extractStdout(response);
        Integer exitCode = agentClient.extractExitCode(response);
        Long elapsed = agentClient.extractElapsed(response);
        if (stdout != null && !stdout.isEmpty()) {
            sendJson(session, event("output", "text", stdout));
        }
        sendMeta(session, state, exitCode != null ? exitCode : 0, elapsed != null ? elapsed : 0L);
    }

    private void sendMeta(WebSocketSession session, SessionState state, int exitCode, Long elapsed) {
        sendJson(session, event("meta",
                "exitCode", exitCode,
                "elapsed", elapsed,
                "cwd", state.cwd));
    }

    private String normalizeCommand(String command) {
        String trimmed = command.trim();
        if ("ll".equals(trimmed)) {
            return "ls -al";
        }
        if ("la".equals(trimmed)) {
            return "ls -a";
        }
        return trimmed;
    }

    private void subscribe(String projectId, String nodeId, WebSocketSession session) {
        sessionGroups.computeIfAbsent(projectId, k -> new ConcurrentHashMap<>()).put(nodeId, session);
    }

    private void sendJson(WebSocketSession session, Map<String, Object> body) {
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            session.sendMessage(new TextMessage(JSON.toJSONString(body)));
        } catch (IOException e) {
            log.error("Console send failed session={}", session.getId(), e);
        }
    }

    private Map<String, Object> event(String type, Object... kv) {
        Map<String, Object> map = new HashMap<>();
        map.put("type", type);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            map.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return map;
    }

    private String shellQuote(String path) {
        return "\"" + path.replace("\"", "\\\"") + "\"";
    }

    private int parseInt(Object value, int defaultValue) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (IOException ignored) {
        }
    }

    public Map<String, Map<String, WebSocketSession>> getSessionGroups() {
        return sessionGroups;
    }

    private static final class SessionState {
        private final String projectId;
        private final String nodeId;
        private final String nodeName;
        private String cwd;

        private SessionState(String projectId, String nodeId, String nodeName, String cwd) {
            this.projectId = projectId;
            this.nodeId = nodeId;
            this.nodeName = nodeName;
            this.cwd = cwd;
        }
    }
}
