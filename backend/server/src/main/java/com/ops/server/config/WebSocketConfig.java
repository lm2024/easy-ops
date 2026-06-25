package com.ops.server.config;

import com.ops.server.interceptor.WebSocketAuthInterceptor;
import com.ops.server.selfheal.websocket.NotificationHandler;
import com.ops.server.websocket.ConsoleHandler;
import com.ops.server.websocket.DeployHandler;
import com.ops.server.websocket.MonitorHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置类
 * SEC-002: 认证拦截器已在 WebSocketAuthInterceptor 中处理
 */
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

    @Autowired
    private NotificationHandler notificationHandler;

    private String[] allowedOrigins;

    @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:5173}")
    public void setAllowedOrigins(String origins) {
        this.allowedOrigins = origins.split(",");
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(consoleHandler, "/ws/console")
                .setAllowedOrigins(allowedOrigins)
                .addInterceptors(webSocketAuthInterceptor);

        registry.addHandler(deployHandler, "/ws/deploy")
                .setAllowedOrigins(allowedOrigins)
                .addInterceptors(webSocketAuthInterceptor);

        registry.addHandler(monitorHandler, "/ws/monitor")
                .setAllowedOrigins(allowedOrigins)
                .addInterceptors(webSocketAuthInterceptor);

        registry.addHandler(notificationHandler, "/ws/notification")
                .setAllowedOrigins(allowedOrigins)
                .addInterceptors(webSocketAuthInterceptor);
    }
}
