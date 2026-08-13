package com.ops.server.interceptor;

import com.alibaba.fastjson2.JSON;
import com.ops.common.response.Result;
import com.ops.server.mapper.NodeMapper;
import com.ops.server.mapper.UserMapper;
import com.ops.server.mapper.TenantMapper;
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
    public static final String ATTR_TENANT_ID = "currentTenantId";
    public static final String ATTR_NODE_ID = "currentNodeId";
    public static final String ATTR_USER_TOKENS = "userAccessibleProjectIds";

    @Autowired
    private NodeMapper nodeMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private TenantMapper tenantMapper;
    // Agent token cache: nodeId -> token
    private final Map<String, String> agentTokenCache = new ConcurrentHashMap<>();

    // User token cache: token -> userData (Map with expiry)
    private final Map<String, TokenData> userTokenCache = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();
        String method = request.getMethod();

        // Skip heartbeat and login endpoints (already excluded in WebConfig)
        // /auth/auto-login 是白名单自动登录接口，本身也必须放行，否则会被自身鉴权拦截形成死循环
        if (uri.contains("/heartbeat") || uri.contains("/auth/login") || uri.contains("/auth/captcha")
                || uri.contains("/auth/auto-login")) {
            return true;
        }

        // Clear previous user context
        request.removeAttribute(ATTR_USER_ID);
        request.removeAttribute(ATTR_USER_NAME);
        request.removeAttribute(ATTR_USER_ROLE);
        request.removeAttribute(ATTR_TENANT_ID);
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
     * SEC-003 修复: Agent token 校验并写入 nodeId 到请求属性。
     * 同时设置 admin 用户身份，允许 Agent 调用管理接口（如创建日志源）。
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
        // Agent 身份也赋予 admin 权限，支持 Agent 调用管理接口
        request.setAttribute(ATTR_USER_ID, "1");
        request.setAttribute(ATTR_USER_NAME, "admin");
        request.setAttribute(ATTR_USER_ROLE, "admin");
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
        // synchronized 保证 tenantId/tenantRole 读取的一致性（switchTenant 可能并发修改）
        Long tenantId;
        String tenantRole;
        synchronized (data) {
            request.setAttribute(ATTR_USER_ID, data.userId);
            request.setAttribute(ATTR_USER_NAME, data.username);
            request.setAttribute(ATTR_USER_ROLE, data.role);
            tenantId = data.tenantId;
            tenantRole = data.tenantRole;
        }
        if (tenantId != null) {
            request.setAttribute(ATTR_TENANT_ID, tenantId);
        }
        // 租户内角色（tenant_user.role）：TENANT_ADMIN / OPERATOR / VIEWER
        if (tenantRole != null) {
            request.setAttribute("currentTenantRole", tenantRole);
        }

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
        return new UserAuthContext(data.userId, data.username, data.role, data.tenantId, data.tenantRole);
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
                // 平台管理员（sys_user.role=admin）默认平台视图（tenantId=null 全量），显式切换才进入租户视角
                boolean platformAdmin = user.getRole() != null
                        && ("admin".equalsIgnoreCase(user.getRole()) || "super_admin".equalsIgnoreCase(user.getRole()));
                Long tenantId = platformAdmin ? null : resolveTenantId(user.getId());
                data = new TokenData(String.valueOf(user.getId()), user.getUsername(), user.getRole(),
                        tenantId, platformAdmin ? null : resolveTenantRole(tenantId, user.getId()));
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
        Long uid = parseLong(userId);
        // 平台管理员默认平台视图（全量），显式切换才进入租户视角
        boolean platformAdmin = role != null
                && ("admin".equalsIgnoreCase(role) || "super_admin".equalsIgnoreCase(role));
        Long tenantId = platformAdmin ? null : resolveTenantId(uid);
        TokenData data = new TokenData(userId, username, role, tenantId,
                platformAdmin ? null : resolveTenantRole(tenantId, uid));
        data.expireTime = System.currentTimeMillis() + 24 * 60 * 60 * 1000;
        userTokenCache.put(token, data);
    }

    public void cacheUserToken(String token, String userId, String username, String role, Long tenantId) {
        Long uid = parseLong(userId);
        boolean platformAdmin = role != null
                && ("admin".equalsIgnoreCase(role) || "super_admin".equalsIgnoreCase(role));
        Long effectiveTenant = platformAdmin ? null : tenantId;
        TokenData data = new TokenData(userId, username, role, effectiveTenant,
                platformAdmin ? null : resolveTenantRole(effectiveTenant, uid));
        data.expireTime = System.currentTimeMillis() + 24 * 60 * 60 * 1000;
        userTokenCache.put(token, data);
    }

    /**
     * 平台管理员切换当前生效租户（受控：仅更新 token 缓存，不影响权限来源 sys_user.role）。
     * tenantId 为 null/0 时切回平台视图（全量）。
     * 校验：目标租户必须存在且启用（status=1），否则拒绝切换。
     */
    public boolean switchTenant(String token, Long tenantId) {
        TokenData data = userTokenCache.get(token);
        if (data == null) return false;
        synchronized (data) {
            if (tenantId == null || tenantId == 0L) {
                data.tenantId = null;
                data.tenantRole = null;
                return true;
            }
            // 校验目标租户存在且启用
            if (tenantMapper != null) {
                com.ops.common.model.TenantModel tenant = tenantMapper.findById(tenantId);
                if (tenant == null || tenant.getStatus() == null || tenant.getStatus() != 1) {
                    log.warn("[Auth] 切换租户失败：目标租户 tenantId={} 不存在或已禁用", tenantId);
                    return false;
                }
            }
            data.tenantId = tenantId;
            data.tenantRole = resolveTenantRole(tenantId, parseLong(data.userId));
            return true;
        }
    }

    private Long parseLong(String value) {
        try { return value == null ? null : Long.valueOf(value); } catch (NumberFormatException e) { return null; }
    }

    /**
     * 解析非管理员用户的生效租户。
     * 兜底逻辑：
     *  1. 无活跃成员关系 → 哨兵 -1（所有租户校验都会失败，用户无法访问任何资源）
     *  2. 租户被禁用（status≠1）→ 返回成员的 tenantId，但后续请求校验会拦截
     *  3. 租户不存在（被物理删除）→ 返回成员的 tenantId，同上
     * 管理员不走此方法（tenantId 直接设为 null → 平台视图）。
     */
    private Long resolveTenantId(Long userId) {
        if (userId == null || tenantMapper == null) return null;
        com.ops.common.model.TenantUserModel member = tenantMapper.findFirstActiveMember(userId);
        if (member == null) {
            // 非管理员用户无租户成员关系 → 哨兵 -1：租户查询为空，且不可被当成平台视图泄漏
            return -1L;
        }
        // 校验租户是否存在且启用；禁用/删除的租户不应放行
        try {
            com.ops.common.model.TenantModel tenant = tenantMapper.findById(member.getTenantId());
            if (tenant == null || tenant.getStatus() == null || tenant.getStatus() != 1) {
                log.warn("[Auth] 用户 userId={} 的租户 tenantId={} 已禁用或不存在，拒绝访问", userId, member.getTenantId());
                return -1L; // 回退到哨兵值，等效于无租户
            }
        } catch (Exception e) {
            log.warn("[Auth] 校验租户状态异常，拒绝用户 userId={}", userId, e);
            return -1L;
        }
        return member.getTenantId();
    }

    /** 解析用户在某租户内的角色（tenant_user.role），非成员返回 null */
    private String resolveTenantRole(Long tenantId, Long userId) {
        if (tenantId == null || userId == null || tenantMapper == null) return null;
        com.ops.common.model.TenantUserModel member = tenantMapper.findMember(tenantId, userId);
        return member == null ? null : member.getRole();
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
        Long tenantId;
        String tenantRole;
        long expireTime;

        TokenData(String userId, String username, String role, Long tenantId) {
            this(userId, username, role, tenantId, null);
        }

        TokenData(String userId, String username, String role, Long tenantId, String tenantRole) {
            this.userId = userId;
            this.username = username;
            this.role = role;
            this.tenantId = tenantId;
            this.tenantRole = tenantRole;
        }
    }

    public static final class UserAuthContext {
        private final String userId;
        private final String username;
        private final String role;
        private final Long tenantId;
        private final String tenantRole;

        public UserAuthContext(String userId, String username, String role) {
            this(userId, username, role, null, null);
        }

        public UserAuthContext(String userId, String username, String role, Long tenantId) {
            this(userId, username, role, tenantId, null);
        }

        public UserAuthContext(String userId, String username, String role, Long tenantId, String tenantRole) {
            this.userId = userId;
            this.username = username;
            this.role = role;
            this.tenantId = tenantId;
            this.tenantRole = tenantRole;
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

        public Long getTenantId() {
            return tenantId;
        }

        public String getTenantRole() {
            return tenantRole;
        }
    }
}
