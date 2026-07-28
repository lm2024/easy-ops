package com.ops.agent.client;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WebSocket 客户端 (Agent 侧)
 * 管理与 Server 的 WebSocket 长连接，支持自动重连和消息收发。
 */
@Component
public class WebSocketClient {

    private final AtomicBoolean connected = new AtomicBoolean(false);
    private ScheduledExecutorService scheduler;

    /**
     * 每个 endpoint → session，用于 sendMessage 时实际发送。
     */
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    /** 重连退避计数器，成功连接后重置 */
    private final AtomicInteger reconnectAttempt = new AtomicInteger(0);

    /** 最大重连间隔 60 秒 */
    private static final long MAX_RECONNECT_SEC = 60;

    @PostConstruct
    public void init() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    public void connect(String wsUrl, String token) {
        try {
            StandardWebSocketClient client = new StandardWebSocketClient();
            String[] endpoints = {"/ws/console", "/ws/deploy", "/ws/monitor"};
            for (String endpoint : endpoints) {
                String url = wsUrl.replace("http://", "ws://")
                        .replace("https://", "wss://")
                        + endpoint;
                connectToEndpoint(client, url, token);
            }
        } catch (Exception e) {
            System.err.println("[Agent WebSocket] Connect failed: " + e.getMessage());
        }
    }

    private void connectToEndpoint(StandardWebSocketClient client, String url, String token) {
        try {
            URI uri = new URI(url);
            AgentWebSocketHandler handler = new AgentWebSocketHandler(url);
            WebSocketSession session = client.doHandshake(handler, uri.toString()).get(5, TimeUnit.SECONDS);
            if (session != null && session.isOpen()) {
                sessions.put(url, session);
                connected.set(true);
                reconnectAttempt.set(0);
                System.out.println("[Agent WebSocket] Connected to " + url);
            }
        } catch (Exception e) {
            System.err.println("[Agent WebSocket] Connect to " + url + " failed: " + e.getMessage());
            scheduleReconnect(client, url, token);
        }
    }

    /**
     * 指数退避重连：1s → 2s → 4s → ... → 最大 60s
     */
    private void scheduleReconnect(StandardWebSocketClient client, String url, String token) {
        int attempt = reconnectAttempt.incrementAndGet();
        long delay = Math.min((long) Math.pow(2, attempt - 1), MAX_RECONNECT_SEC);
        System.out.println("[Agent WebSocket] Will reconnect to " + url + " in " + delay + "s (attempt " + attempt + ")");
        scheduler.schedule(() -> connectToEndpoint(client, url, token), delay, TimeUnit.SECONDS);
    }

    /**
     * 发送文本消息到所有已连接的 Session
     */
    public void sendMessage(String message) {
        if (sessions.isEmpty()) {
            System.err.println("[Agent WebSocket] No active sessions");
            return;
        }
        for (Map.Entry<String, WebSocketSession> entry : sessions.entrySet()) {
            WebSocketSession session = entry.getValue();
            if (session != null && session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(message));
                } catch (Exception e) {
                    System.err.println("[Agent WebSocket] Send to " + entry.getKey() + " failed: " + e.getMessage());
                    sessions.remove(entry.getKey());
                }
            } else {
                sessions.remove(entry.getKey());
            }
        }
        if (sessions.isEmpty()) {
            connected.set(false);
        }
    }

    public void disconnect() {
        connected.set(false);
        for (Map.Entry<String, WebSocketSession> entry : sessions.entrySet()) {
            try {
                WebSocketSession session = entry.getValue();
                if (session != null && session.isOpen()) {
                    session.close();
                }
            } catch (Exception ignored) {}
        }
        sessions.clear();
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    /**
     * 返回当前连接数
     */
    public int activeSessionCount() {
        return sessions.size();
    }

    private class AgentWebSocketHandler extends TextWebSocketHandler {

        private final String endpointUrl;

        AgentWebSocketHandler(String endpointUrl) {
            this.endpointUrl = endpointUrl;
        }

        @Override
        public void afterConnectionEstablished(WebSocketSession session) {
            sessions.put(endpointUrl, session);
            connected.set(true);
            reconnectAttempt.set(0);
            System.out.println("[Agent WebSocket] Connection established: " + endpointUrl);
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            sessions.remove(endpointUrl);
            if (sessions.isEmpty()) {
                connected.set(false);
            }
            System.out.println("[Agent WebSocket] Connection closed: " + endpointUrl + " status=" + status);
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            // 子类可覆写处理 Server 推送的消息
            System.out.println("[Agent WebSocket] Received: " + message.getPayload());
        }
    }
}
