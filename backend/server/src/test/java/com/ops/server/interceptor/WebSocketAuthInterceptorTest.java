package com.ops.server.interceptor;

import com.ops.server.mapper.NodeMapper;
import com.ops.server.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * T-006: WebSocketAuthInterceptor 单元测试 (SEC-002)
 * 验证: Bearer Token 认证、无效 Token 拒绝、缺少 Header 拒绝
 */
class WebSocketAuthInterceptorTest {

    private WebSocketAuthInterceptor interceptor;
    private NodeMapper nodeMapper;
    private UserMapper userMapper;
    private Map<String, Object> attributes;

    @BeforeEach
    void setUp() throws Exception {
        nodeMapper = mock(NodeMapper.class);
        userMapper = mock(UserMapper.class);

        interceptor = new WebSocketAuthInterceptor();
        // Use reflection to inject mocks
        java.lang.reflect.Field nodeField = WebSocketAuthInterceptor.class.getDeclaredField("nodeMapper");
        nodeField.setAccessible(true);
        nodeField.set(interceptor, nodeMapper);

        java.lang.reflect.Field userField = WebSocketAuthInterceptor.class.getDeclaredField("userMapper");
        userField.setAccessible(true);
        userField.set(interceptor, userMapper);

        attributes = new HashMap<>();
    }

    /**
     * Create a ServerHttpRequest with the given Authorization header
     */
    private ServerHttpRequest createServerRequest(String authHeader) {
        ServerHttpRequest mockRequest = mock(ServerHttpRequest.class);
        HttpHeaders headers = new HttpHeaders();
        if (authHeader != null && !authHeader.isEmpty()) {
            headers.add("Authorization", authHeader);
        }
        when(mockRequest.getHeaders()).thenReturn(headers);
        return mockRequest;
    }

    // ==================== 有效 Token 测试 ====================

    @Test
    void validUserToken_shouldSucceed() throws Exception {
        String token = "valid-user-token";
        ServerHttpRequest request = createServerRequest("Bearer " + token);
        when(userMapper.getUserIdByToken(token)).thenReturn("1");
        when(userMapper.findById(1L)).thenReturn(createTestUser(1L, "admin"));

        boolean result = interceptor.beforeHandshake(request, mock(ServerHttpResponse.class), mock(WebSocketHandler.class), attributes);

        assertTrue(result);
        assertEquals("1", attributes.get("userId"));
    }

    @Test
    void validUserToken_withNoBearerPrefix_shouldFail() throws Exception {
        ServerHttpRequest request = createServerRequest("plain-token");
        when(userMapper.getUserIdByToken("plain-token")).thenReturn("1");
        when(userMapper.findById(1L)).thenReturn(createTestUser(1L, "admin"));

        boolean result = interceptor.beforeHandshake(request, mock(ServerHttpResponse.class), mock(WebSocketHandler.class), attributes);

        // No Bearer prefix is rejected by the interceptor
        assertFalse(result);
    }

    @Test
    void validAgentToken_shouldSucceed() throws Exception {
        String token = "valid-agent-token";
        ServerHttpRequest request = createServerRequest("Bearer " + token);
        when(userMapper.getUserIdByToken(token)).thenReturn(null);
        when(nodeMapper.getTokenByToken(token)).thenReturn(token);

        boolean result = interceptor.beforeHandshake(request, mock(ServerHttpResponse.class), mock(WebSocketHandler.class), attributes);

        assertTrue(result);
    }

    @Test
    void validAgentToken_storedInAttributes() throws Exception {
        String token = "agent-only-token";
        ServerHttpRequest request = createServerRequest("Bearer " + token);
        when(userMapper.getUserIdByToken(token)).thenReturn(null);
        when(nodeMapper.getTokenByToken(token)).thenReturn(token);

        boolean result = interceptor.beforeHandshake(request, mock(ServerHttpResponse.class), mock(WebSocketHandler.class), attributes);

        assertTrue(result);
        assertEquals(token, attributes.get("token"));
    }

    // ==================== 无效 Token 测试 ====================

    @Test
    void invalidUserToken_shouldFail() throws Exception {
        String token = "invalid-token";
        ServerHttpRequest request = createServerRequest("Bearer " + token);
        when(userMapper.getUserIdByToken(token)).thenReturn(null);
        when(nodeMapper.getTokenByToken(token)).thenReturn(null);

        boolean result = interceptor.beforeHandshake(request, mock(ServerHttpResponse.class), mock(WebSocketHandler.class), attributes);

        assertFalse(result);
    }

    @Test
    void missingAuthorizationHeader_shouldFail() throws Exception {
        ServerHttpRequest request = createServerRequest(null);

        boolean result = interceptor.beforeHandshake(request, mock(ServerHttpResponse.class), mock(WebSocketHandler.class), attributes);

        assertFalse(result);
    }

    @Test
    void emptyAuthorizationHeader_shouldFail() throws Exception {
        ServerHttpRequest request = createServerRequest("");

        boolean result = interceptor.beforeHandshake(request, mock(ServerHttpResponse.class), mock(WebSocketHandler.class), attributes);

        assertFalse(result);
    }

    @Test
    void wrongAgentToken_shouldFail() throws Exception {
        String token = "wrong-agent-token";
        ServerHttpRequest request = createServerRequest("Bearer " + token);
        when(userMapper.getUserIdByToken(token)).thenReturn(null);
        when(nodeMapper.getTokenByToken(token)).thenReturn("correct-token");

        boolean result = interceptor.beforeHandshake(request, mock(ServerHttpResponse.class), mock(WebSocketHandler.class), attributes);

        assertFalse(result);
    }

    // ==================== 混合 token 测试 ====================

    @Test
    void userTokenNotFound_butAgentTokenValid_shouldSucceedAsAgent() throws Exception {
        String token = "agent-but-user-lookup";
        ServerHttpRequest request = createServerRequest("Bearer " + token);
        when(userMapper.getUserIdByToken(token)).thenReturn(null);
        when(nodeMapper.getTokenByToken(token)).thenReturn(token);

        boolean result = interceptor.beforeHandshake(request, mock(ServerHttpResponse.class), mock(WebSocketHandler.class), attributes);

        assertTrue(result);
    }

    @Test
    void bothTokensInvalid_shouldFail() throws Exception {
        String token = "totally-invalid";
        ServerHttpRequest request = createServerRequest("Bearer " + token);
        when(userMapper.getUserIdByToken(token)).thenReturn(null);
        when(nodeMapper.getTokenByToken(token)).thenReturn(null);

        boolean result = interceptor.beforeHandshake(request, mock(ServerHttpResponse.class), mock(WebSocketHandler.class), attributes);

        assertFalse(result);
    }

    // ==================== 工具方法 ====================

    private com.ops.common.model.UserModel createTestUser(Long id, String username) {
        com.ops.common.model.UserModel user = new com.ops.common.model.UserModel();
        user.setId(id);
        user.setUsername(username);
        user.setRole("admin");
        user.setStatus(1);
        user.setCreateTime(System.currentTimeMillis());
        return user;
    }
}
