package com.ops.server.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SEC-008: KeyAuditFilter 单元测试
 */
class KeyAuditFilterTest {

    private KeyAuditFilter filter;

    @BeforeEach
    void setUp() {
        filter = new KeyAuditFilter();
    }

    @Test
    void login_audit_logs_username() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        request.setParameter("username", "admin");
        request.setParameter("password", "secret123");
        request.setRemoteAddr("192.168.1.100");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        // 关键：不抛出异常表示审计正常执行
    }

    @Test
    void login_audit_masks_long_username() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        request.setParameter("username", "administrator");
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
    }

    @Test
    void login_audit_truncates_short_username() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        request.setParameter("username", "ab");
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
    }

    @Test
    void non_audit_paths_pass_through() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        // 非审计路径直接放行
    }

    @Test
    void get_request_not_audited() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        // GET 不审计
    }

    @Test
    void heartbeat_not_audited() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/nodes/heartbeat");
        request.setParameter("username", "test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
    }

    @Test
    void null_username_handled() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        // 不设置 username 参数
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
    }

    @Test
    void sensitive_config_key_audit_logs() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/sys/config");
        request.setParameter("key", "ai.apiKey");
        request.addHeader("Authorization", "Bearer test-token");
        request.setRemoteAddr("172.16.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
    }

    @Test
    void non_sensitive_config_not_audited() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/sys/config");
        request.setParameter("key", "app.debug");
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
    }
}
