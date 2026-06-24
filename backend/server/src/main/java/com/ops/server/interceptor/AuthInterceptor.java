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

    // Request attribute keys for controllers
    public static final String ATTR_USER_ID = "currentUserId";
    public static final String ATTR_USER_NAME = "currentUsername";
    public static final String ATTR_USER_ROLE = "currentRole";
    public static final String ATTR_NODE_ID = "currentNodeId";
    public static final String ATTR_USER_TOKENS = "userAccessibleProjectIds";

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

        // Clear previous user context
        request.removeAttribute(ATTR_USER_ID);
        request.removeAttribute(ATTR_USER_NAME);
        request.removeAttribute(ATTR_USER_ROLE);
        request.removeAttribute(ATTR_NODE_ID);

        // Check Agent token (X-Token header)
        String agentToken = request.getHeader("X-Token");
        if (agentToken != null && !agentToken.isEmpty()) {
            return validateAgentToken(request, response, agentToken);
        }

        // Check user token (Authorization header)
        String userToken = request.getHeader("Authorization");
        if (userToken != null && !userToken.isEmpty()) {
            // Remove "Bearer " prefix if present
            if (userToken.startsWith("Bearer ")) {
                userToken = userToken.substring(7);
            }
            return validateUserToken(request, response, userToken);
        }

        // No token provided
        sendUnauthorized(response);
        return false;
    }

    /**
     * SEC-003 修复: Agent token 校验并写入 nodeId 到请求属性
     */
    private boolean validateAgentToken(HttpServletRequest request, HttpServletResponse response, String token) throws java.io.IOException {
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

        // 写入请求属性供 Controller 使用 (SEC-003)
        request.setAttribute(ATTR_NODE_ID, nodeId);
        return true;
    }

    /**
     * SEC-003 修复: User token 校验并提取 userId/username/role 到请求属性
     */
    private boolean validateUserToken(HttpServletRequest request, HttpServletResponse response, String token) throws java.io.IOException {
        TokenData data = resolveUserTokenData(token);
        if (data == null) {
            sendUnauthorized(response);
            return false;
        }

        // 写入请求属性供 Controller 使用 (SEC-003)
        request.setAttribute(ATTR_USER_ID, data.userId);
        request.setAttribute(ATTR_USER_NAME, data.username);
        request.setAttribute(ATTR_USER_ROLE, data.role);

        return true;
    }

    /**
     * 根据用户 token 解析登录态，供 WebSocket 等场景复用内存缓存。
     */
    public UserAuthContext lookupUserAuth(String token) {
        TokenData data = resolveUserTokenData(token);
        if (data == null) {
            return null;
        }
        return new UserAuthContext(data.userId, data.username, data.role);
    }

    private TokenData resolveUserTokenData(String token) {
        TokenData data = userTokenCache.get(token);
        if (data != null) {
            long now = System.currentTimeMillis();
            if (now > data.expireTime) {
                userTokenCache.remove(token);
                return null;
            }
            data.expireTime = now + 24 * 60 * 60 * 1000;
            return data;
        }

        String userIdStr = userMapper.getUserIdByToken(token);
        if (userIdStr == null) {
            return null;
        }
        try {
            Long userId = Long.parseLong(userIdStr);
            com.ops.common.model.UserModel user = userMapper.findById(userId);
            if (user != null) {
                data = new TokenData(String.valueOf(user.getId()), user.getUsername(), user.getRole());
                data.expireTime = System.currentTimeMillis() + 24 * 60 * 60 * 1000;
                userTokenCache.put(token, data);
                return data;
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return null;
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

    public static final class UserAuthContext {
        private final String userId;
        private final String username;
        private final String role;

        public UserAuthContext(String userId, String username, String role) {
            this.userId = userId;
            this.username = username;
            this.role = role;
        }

        public String getUserId() {
            return userId;
        }

        public String getUsername() {
            return username;
        }

        public String getRole() {
            return role;
        }
    }
}
