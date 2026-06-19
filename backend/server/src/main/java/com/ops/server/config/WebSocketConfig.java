package com.ops.server.config;

import com.ops.server.interceptor.WebSocketAuthInterceptor;
import com.ops.server.websocket.ConsoleHandler;
import com.ops.server.websocket.DeployHandler;
import com.ops.server.websocket.MonitorHandler;
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

    @Autowired
    private ConsoleHandler consoleHandler;

    @Autowired
    private DeployHandler deployHandler;

    @Autowired
    private MonitorHandler monitorHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(consoleHandler, "/ws/console")
                .setAllowedOrigins("*")
                .addInterceptors(webSocketAuthInterceptor);

        registry.addHandler(deployHandler, "/ws/deploy")
                .setAllowedOrigins("*")
                .addInterceptors(webSocketAuthInterceptor);

        registry.addHandler(monitorHandler, "/ws/monitor")
                .setAllowedOrigins("*")
                .addInterceptors(webSocketAuthInterceptor);
    }
}
