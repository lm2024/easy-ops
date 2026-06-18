package com.ops.server.config;

import com.ops.server.interceptor.WebSocketAuthInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private WebSocketAuthInterceptor webSocketAuthInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new com.ops.server.websocket.ConsoleHandler(), "/ws/console")
                .setAllowedOrigins("*")
                .addInterceptors(webSocketAuthInterceptor);

        registry.addHandler(new com.ops.server.websocket.DeployHandler(), "/ws/deploy")
                .setAllowedOrigins("*")
                .addInterceptors(webSocketAuthInterceptor);

        registry.addHandler(new com.ops.server.websocket.MonitorHandler(), "/ws/monitor")
                .setAllowedOrigins("*")
                .addInterceptors(webSocketAuthInterceptor);
    }
}
