package com.ops.agent.client;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * WebSocket客户端 (Agent侧)
 */
@Component
public class WebSocketClient {

    private final AtomicBoolean connected = new AtomicBoolean(false);
    private ScheduledExecutorService scheduler;

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
            client.doHandshake(new AgentWebSocketHandler(), uri.toString());
            connected.set(true);
            System.out.println("[Agent WebSocket] Connected to " + url);
        } catch (Exception e) {
            System.err.println("[Agent WebSocket] Connect to " + url + " failed: " + e.getMessage());
        }
    }

    public void sendMessage(String message) {
        if (!connected.get()) {
            System.err.println("[Agent WebSocket] Not connected");
            return;
        }
    }

    public void disconnect() {
        connected.set(false);
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    private class AgentWebSocketHandler extends TextWebSocketHandler {

        @Override
        public void afterConnectionEstablished(WebSocketSession session) {
            System.out.println("[Agent WebSocket] Connection established");
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            connected.set(false);
        }
    }
}
