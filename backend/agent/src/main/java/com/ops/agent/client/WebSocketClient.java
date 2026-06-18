package com.ops.agent.client;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketContainer;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.annotation.PostConstruct;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.net.URI;
import java.security.KeyStore;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * WebSocket客户端 (Agent侧)
 * 与Server建立WebSocket连接，实现实时通信
 */
@Component
public class WebSocketClient {

    private WebSocketContainer container;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private ScheduledExecutorService scheduler;
    private TextWebSocketHandler handler;

    private String serverWsUrl;

    /**
     * 初始化WebSocket容器
     */
    @PostConstruct
    public void init() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    /**
     * 连接到Server的WebSocket端点
     *
     * @param wsUrl Server WebSocket地址 (如 ws://localhost:8081/api/ws/agent)
     * @param token  认证token
     * @param textHandler 消息处理器
     */
    public void connect(String wsUrl, String token, TextWebSocketHandler textHandler) {
        this.serverWsUrl = wsUrl;
        this.handler = textHandler;

        try {
            container = new StandardWebSocketClient();

            // Connect to each WebSocket endpoint
            String[] endpoints = {"/ws/console", "/ws/deploy", "/ws/monitor"};
            for (String endpoint : endpoints) {
                String url = wsUrl.replace("http://", "ws://")
                        .replace("https://", "wss://")
                        + endpoint;
                connectToEndpoint(url, token);
            }
        } catch (Exception e) {
            System.err.println("[Agent WebSocket] Connect failed: " + e.getMessage());
        }
    }

    private void connectToEndpoint(String url, String token) {
        try {
            URI uri = new URI(url);
            container.connect(uri, new AgentWebSocketHandler(token),
                    session -> {
                        connected.set(true);
                        System.out.println("[Agent WebSocket] Connected to " + url);
                    });
        } catch (Exception e) {
            System.err.println("[Agent WebSocket] Connect to " + url + " failed: " + e.getMessage());
        }
    }

    /**
     * 发送消息到Server
     *
     * @param message 消息内容
     */
    public void sendMessage(String message) {
        if (!connected.get()) {
            System.err.println("[Agent WebSocket] Not connected");
            return;
        }
        // Implementation would send via active sessions
    }

    /**
     * 关闭连接
     */
    public void disconnect() {
        connected.set(false);
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    /**
     * Agent端WebSocket处理器
     */
    private class AgentWebSocketHandler extends TextWebSocketHandler {

        private final String token;

        AgentWebSocketHandler(String token) {
            this.token = token;
        }

        @Override
        public void afterConnectionEstablished(javax.websocket.Session session) {
            System.out.println("[Agent WebSocket] Connection established");
        }

        @Override
        protected void handleTextMessage(javax.websocket.Session session, TextMessage message) {
            String payload = message.getPayload();
            if (handler != null) {
                handler.handleTextMessage(message);
            }
        }
    }
}
