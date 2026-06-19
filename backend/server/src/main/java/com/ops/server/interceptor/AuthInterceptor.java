package com.ops.server.interceptor;

import com.alibaba.fastjson2.JSON;
import com.ops.common.response.Result;
import com.ops.server.mapper.NodeMapper;
import com.ops.server.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuthInterceptor.class);

    @Autowired
    private NodeMapper nodeMapper;

    @Autowired
    private UserMapper userMapper;

    // Agent token cache: nodeId -> token
    private final Map<String, String> agentTokenCache = new ConcurrentHashMap<>();

    // User token cache: token -> userData (Map with expiry)
    private final Map<String, TokenData> userTokenCache = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();
        String method = request.getMethod();

        // Skip heartbeat and login endpoints (already excluded in WebConfig)
        if (uri.contains("/heartbeat") || uri.contains("/auth/login")) {
            return true;
        }

        // Check Agent token (X-Token header)
        String agentToken = request.getHeader("X-Token");
        if (agentToken != null && !agentToken.isEmpty()) {
            return validateAgentToken(response, agentToken);
        }

        // Check user token (Authorization header)
        String userToken = request.getHeader("Authorization");
        if (userToken != null && !userToken.isEmpty()) {
            // Remove "Bearer " prefix if present
            if (userToken.startsWith("Bearer ")) {
                userToken = userToken.substring(7);
            }
            return validateUserToken(response, userToken);
        }

        // No token provided
        sendUnauthorized(response);
        return false;
    }

    private boolean validateAgentToken(HttpServletResponse response, String token) throws java.io.IOException {
        String nodeId = extractNodeIdFromRequest(token);
        if (nodeId == null) {
            sendUnauthorized(response);
            return false;
        }
        // Re-authenticate with database
        String dbToken = nodeMapper.getTokenByToken(token);
        if (dbToken == null || !dbToken.equals(token)) {
            sendUnauthorized(response);
            return false;
        }
        // Update cache
        agentTokenCache.put(nodeId, token);
        return true;
    }

    private boolean validateUserToken(HttpServletResponse response, String token) throws java.io.IOException {
        TokenData data = userTokenCache.get(token);
        if (data == null) {
            sendUnauthorized(response);
            return false;
        }
        // Check expiry
        long now = System.currentTimeMillis();
        if (now > data.expireTime) {
            userTokenCache.remove(token);
            sendUnauthorized(response);
            return false;
        }
        // Refresh expiry
        data.expireTime = now + 24 * 60 * 60 * 1000;
        return true;
    }

    private String extractNodeIdFromRequest(String token) {
        // For Agent, the token maps to a node; extract node identifier
        return nodeMapper.getNodeIdByToken(token);
    }

    private void sendUnauthorized(HttpServletResponse response) throws java.io.IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json;charset=UTF-8");
        Result<?> result = Result.error(401, "Unauthorized");
        response.getWriter().write(JSON.toJSONString(result));
    }

    // Cache methods for controllers
    public void cacheUserToken(String token, String userId, String username, String role) {
        TokenData data = new TokenData(userId, username, role);
        data.expireTime = System.currentTimeMillis() + 24 * 60 * 60 * 1000;
        userTokenCache.put(token, data);
    }

    public void removeUserToken(String token) {
        userTokenCache.remove(token);
    }

    public Map<String, String> getAgentTokenCache() {
        return agentTokenCache;
    }

    private static class TokenData {
        String userId;
        String username;
        String role;
        long expireTime;

        TokenData(String userId, String username, String role) {
            this.userId = userId;
            this.username = username;
            this.role = role;
        }
    }
}
