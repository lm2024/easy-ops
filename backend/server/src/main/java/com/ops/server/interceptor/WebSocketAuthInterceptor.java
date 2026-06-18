package com.ops.server.interceptor;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketAuthInterceptor.class);

    @Override
    public boolean beforeHandshake(@NotNull ServerHttpRequest request, @NotNull ServerHttpResponse response,
                                    @NotNull WebSocketHandler handler, @NotNull Map<String, Object> attributes) throws Exception {
        String token = request.getHeaders().getFirst("Sec-WebSocket-Protocol");
        if (token != null && !token.isEmpty()) {
            log.debug("WebSocket handshake with token: {}", token);
            attributes.put("token", token);
            return true;
        }
        // WebSocket auth check (pass for now, will be checked at session level)
        return true;
    }

    @Override
    public void afterHandshake(@NotNull ServerHttpRequest request, @NotNull ServerHttpResponse response,
                                @NotNull WebSocketHandler handler, Exception exception) {
    }
}
