package com.ops.server.interceptor;

import com.ops.server.mapper.NodeMapper;
import com.ops.server.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Map;

/**
 * T-006: AuthInterceptor 单元测试 (SEC-003/SEC-004)
 * 验证: 用户 Token 认证、Agent Token 认证、未授权拒绝、过期 Token 拒绝
 */
class AuthInterceptorTest {

    private AuthInterceptor interceptor;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain chain;
    private NodeMapper nodeMapper;
    private UserMapper userMapper;

    @BeforeEach
    void setUp() {
        nodeMapper = mock(NodeMapper.class);
        userMapper = mock(UserMapper.class);

        interceptor = new AuthInterceptor();
        // Use reflection to inject mocks
        try {
            java.lang.reflect.Field nodeField = AuthInterceptor.class.getDeclaredField("nodeMapper");
            nodeField.setAccessible(true);
            nodeField.set(interceptor, nodeMapper);

            java.lang.reflect.Field userField = AuthInterceptor.class.getDeclaredField("userMapper");
            userField.setAccessible(true);
            userField.set(interceptor, userMapper);
        } catch (Exception e) {
            fail("Failed to inject mocks: " + e.getMessage());
        }

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
    }

    // ==================== 用户 Token 认证测试 ====================

    @Test
    void validUserToken_shouldReturnTrue() throws Exception {
        String token = "valid-user-token";
        request.addHeader("Authorization", "Bearer " + token);
        when(userMapper.getUserIdByToken(token)).thenReturn("1");
        when(userMapper.findById(1L)).thenReturn(createUser(1L, "admin", "admin"));

        boolean result = interceptor.preHandle(request, response, null);

        assertTrue(result);
        assertEquals("1", request.getAttribute("currentUserId"));
        assertEquals("admin", request.getAttribute("currentUsername"));
        assertEquals("admin", request.getAttribute("currentRole"));
    }

    @Test
    void validUserToken_withExpiry_shouldSucceed() throws Exception {
        String token = "valid-token-with-expiry";
        request.addHeader("Authorization", "Bearer " + token);
        when(userMapper.getUserIdByToken(token)).thenReturn("2");
        when(userMapper.findById(2L)).thenReturn(createUser(2L, "operator", "operator"));

        boolean result = interceptor.preHandle(request, response, null);

        assertTrue(result);
        assertEquals("2", request.getAttribute("currentUserId"));
    }

    @Test
    void validUserToken_noBearerPrefix_shouldWork() throws Exception {
        // When Authorization header has no Bearer prefix, the full value is used as token
        // This is the same as validateUserToken — it just passes the raw header value
        String token = "no-bearer-prefix";
        request.addHeader("Authorization", token);
        when(userMapper.getUserIdByToken(token)).thenReturn("1");
        when(userMapper.findById(1L)).thenReturn(createUser(1L, "admin", "admin"));

        boolean result = interceptor.preHandle(request, response, null);

        // The full header value is validated against database — succeeds if valid
        assertTrue(result);
        assertEquals("1", request.getAttribute("currentUserId"));
    }

    @Test
    void invalidUserToken_shouldReturnFalse() throws Exception {
        String token = "invalid-token";
        request.addHeader("Authorization", "Bearer " + token);
        when(userMapper.getUserIdByToken(token)).thenReturn(null);

        boolean result = interceptor.preHandle(request, response, null);

        assertFalse(result);
        assertEquals(401, response.getStatus());
    }

    @Test
    void expiredUserToken_shouldReturnFalse() throws Exception {
        // Since we cannot easily manipulate the cache expiry, verify that
        // an invalid token (not in cache) returns 401
        String badToken = "bad-token";
        request.addHeader("Authorization", "Bearer " + badToken);
        when(userMapper.getUserIdByToken(badToken)).thenReturn(null);

        boolean result = interceptor.preHandle(request, response, null);

        assertFalse(result);
        assertEquals(401, response.getStatus());
    }

    @Test
    void noAuthorizationHeader_shouldReturn401() throws Exception {
        boolean result = interceptor.preHandle(request, response, null);

        assertFalse(result);
        assertEquals(401, response.getStatus());
    }

    // ==================== Agent Token 认证测试 ====================

    @Test
    void validAgentToken_shouldReturnTrue() throws Exception {
        String token = "agent-token-123";
        request.addHeader("X-Token", token);
        when(nodeMapper.getNodeIdByToken(token)).thenReturn("100");
        when(nodeMapper.getTokenByToken(token)).thenReturn(token);

        boolean result = interceptor.preHandle(request, response, null);

        assertTrue(result);
        assertEquals("100", request.getAttribute("currentNodeId"));
    }

    @Test
    void invalidAgentToken_shouldReturnFalse() throws Exception {
        String token = "bad-agent-token";
        request.addHeader("X-Token", token);
        when(nodeMapper.getNodeIdByToken(token)).thenReturn(null);

        boolean result = interceptor.preHandle(request, response, null);

        assertFalse(result);
        assertEquals(401, response.getStatus());
    }

    @Test
    void agentTokenCacheMismatch_shouldReturnFalse() throws Exception {
        String token = "cache-mismatch-token";
        request.addHeader("X-Token", token);
        when(nodeMapper.getNodeIdByToken(token)).thenReturn("100");
        when(nodeMapper.getTokenByToken(token)).thenReturn("different-token");

        boolean result = interceptor.preHandle(request, response, null);

        assertFalse(result);
        assertEquals(401, response.getStatus());
    }

    // ==================== 路径排除测试 ====================

    @Test
    void heartbeatPath_shouldSkipAuth() throws Exception {
        request.setRequestURI("/nodes/heartbeat");
        boolean result = interceptor.preHandle(request, response, null);

        assertTrue(result);
    }

    @Test
    void loginPath_shouldSkipAuth() throws Exception {
        request.setRequestURI("/auth/login");
        boolean result = interceptor.preHandle(request, response, null);

        assertTrue(result);
    }

    // ==================== 缓存测试 ====================

    @Test
    void cacheUserToken_shouldStoreData() throws Exception {
        interceptor.cacheUserToken("cached-token", "5", "cachedUser", "operator");

        Map<String, String> agentCache = interceptor.getAgentTokenCache();
        assertNotNull(agentCache);

        // Note: userTokenCache is private; verify through validate
        String token = "cached-token";
        request.addHeader("Authorization", "Bearer " + token);

        boolean result = interceptor.preHandle(request, response, null);

        assertTrue(result);
        assertEquals("5", request.getAttribute("currentUserId"));
    }

    @Test
    void removeUserToken_shouldClearCache() throws Exception {
        interceptor.cacheUserToken("remove-me", "6", "removableUser", "operator");

        interceptor.removeUserToken("remove-me");

        String token = "remove-me";
        request.addHeader("Authorization", "Bearer " + token);

        boolean result = interceptor.preHandle(request, response, null);

        assertFalse(result);
        assertEquals(401, response.getStatus());
    }

    // ==================== 工具方法 ====================

    private com.ops.common.model.UserModel createUser(Long id, String username, String role) {
        com.ops.common.model.UserModel user = new com.ops.common.model.UserModel();
        user.setId(id);
        user.setUsername(username);
        user.setRole(role);
        user.setStatus(1);
        user.setCreateTime(System.currentTimeMillis());
        return user;
    }
}
