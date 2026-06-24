package com.ops.server.interceptor;

import com.ops.server.mapper.NodeMapper;
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
 * 修复: 优先 Authorization: Bearer 头；浏览器 WebSocket 无法自定义 Header 时回退 query token
 */
@Component
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketAuthInterceptor.class);

    @Autowired
    private NodeMapper nodeMapper;

    @Autowired
    private AuthInterceptor authInterceptor;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler handler, Map<String, Object> attributes) throws Exception {
        // 将 ServerHttpRequest 转换为 ServletServerHttpRequest 以获取 HttpServletRequest
        HttpServletRequest servletRequest = null;
        if (request instanceof ServletServerHttpRequest) {
            servletRequest = ((ServletServerHttpRequest) request).getServletRequest();
        }

        String token = resolveToken(request, servletRequest);
        if (token == null || token.isEmpty()) {
            log.warn("WebSocket handshake rejected: missing token from {}",
                    servletRequest != null ? servletRequest.getRemoteAddr() : "unknown");
            return false;
        }

        // Validate token against login cache / database (same as REST API)
        AuthInterceptor.UserAuthContext userAuth = authInterceptor.lookupUserAuth(token);
        if (userAuth != null) {
            attributes.put("token", token);
            attributes.put("userId", userAuth.getUserId());
            attributes.put("username", userAuth.getUsername());
            log.debug("WebSocket authenticated successfully for user: {}", userAuth.getUsername());
            return true;
        }

        if (!validateAgentToken(token, servletRequest)) {
            return false;
        }

        attributes.put("token", token);
        log.debug("WebSocket authenticated successfully for agent token");
        return true;
    }

    /**
     * 解析 token：优先 Authorization 头，浏览器 WebSocket 回退 query 参数
     */
    private String resolveToken(ServerHttpRequest request, HttpServletRequest servletRequest) {
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader != null && !authHeader.isEmpty()) {
            if (authHeader.startsWith("Bearer ")) {
                return authHeader.substring(7);
            }
            log.warn("WebSocket handshake rejected: invalid Authorization header format from {}",
                    servletRequest != null ? servletRequest.getRemoteAddr() : "unknown");
            return null;
        }

        if (servletRequest != null) {
            String queryToken = servletRequest.getParameter("token");
            if (queryToken != null && !queryToken.trim().isEmpty()) {
                return queryToken.trim();
            }
        }
        return null;
    }

    /**
     * 校验 Agent token 是否有效
     */
    private boolean validateAgentToken(String token, HttpServletRequest servletRequest) {
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
