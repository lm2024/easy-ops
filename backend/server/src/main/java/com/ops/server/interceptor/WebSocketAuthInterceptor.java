package com.ops.server.interceptor;

import com.alibaba.fastjson2.JSON;
import com.ops.common.response.Result;
import com.ops.server.mapper.NodeMapper;
import com.ops.server.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * WebSocket 握手拦截器 - SEC-002 修复
 * 原问题: Token 从 Sec-WebSocket-Protocol 头部取，浏览器可伪造；无权限校验
 * 修复: 改用标准 Authorization: Bearer <token> 头，增加完整权限校验
 */
@Component
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketAuthInterceptor.class);

    @Autowired
    private NodeMapper nodeMapper;

    @Autowired
    private UserMapper userMapper;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler handler, Map<String, Object> attributes) throws Exception {
        // 将 ServerHttpRequest 转换为 ServletServerHttpRequest 以获取 HttpServletRequest
        HttpServletRequest servletRequest = null;
        if (request instanceof ServletServerHttpRequest) {
            servletRequest = ((ServletServerHttpRequest) request).getServletRequest();
        }

        // SEC-002: 不再从 Sec-WebSocket-Protocol 取 token
        // 改用标准 Authorization: Bearer <token> 头部
        String authHeader = request.getHeaders().getFirst("Authorization");

        if (authHeader == null || authHeader.isEmpty()) {
            log.warn("WebSocket handshake rejected: missing Authorization header from {}",
                    servletRequest != null ? servletRequest.getRemoteAddr() : "unknown");
            return false;
        }

        // Remove "Bearer " prefix if present
        String token;
        if (authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        } else {
            log.warn("WebSocket handshake rejected: invalid Authorization header format from {}",
                    servletRequest != null ? servletRequest.getRemoteAddr() : "unknown");
            return false;
        }

        // Validate token against database (same as REST API)
        if (!validateToken(token, servletRequest)) {
            return false;
        }

        // Store validated token and user info in handshake attributes
        attributes.put("token", token);
        String userId = userMapper.getUserIdByToken(token);
        if (userId != null) {
            attributes.put("userId", userId);
            try {
                attributes.put("username", userMapper.getUsernameById(Long.parseLong(userId)));
            } catch (NumberFormatException e) {
                // ignore
            }
        }

        log.debug("WebSocket authenticated successfully for token: {}", token);
        return true;
    }

    /**
     * 校验 token 是否有效
     */
    private boolean validateToken(String token, HttpServletRequest servletRequest) {
        // Validate user token
        String userId = userMapper.getUserIdByToken(token);
        if (userId != null) {
            return true;
        }

        // Validate agent token
        String dbToken = nodeMapper.getTokenByToken(token);
        if (dbToken != null && dbToken.equals(token)) {
            return true;
        }

        String remoteAddr = servletRequest != null ? servletRequest.getRemoteAddr() : "unknown";
        log.warn("WebSocket authentication failed: invalid token from {}", remoteAddr);
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler handler, Exception exception) {
        // no-op
    }
}
