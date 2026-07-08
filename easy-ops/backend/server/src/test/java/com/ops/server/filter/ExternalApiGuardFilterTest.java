package com.ops.server.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SEC-009: ExternalApiGuardFilter 单元测试
 */
class ExternalApiGuardFilterTest {

    private ExternalApiGuardFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ExternalApiGuardFilter();
    }

    @Test
    void get_request_passes() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
    }

    @Test
    void non_api_path_passes() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
    }

    @Test
    void post_to_agent_path_passes() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/deploy/1/start");
        request.setRemoteAddr("192.168.1.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
    }

    @Test
    void post_to_agent_proxy_path_passes() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/agent-proxy/execute");
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
    }

    @Test
    void post_to_non_agent_api_passes() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/projects");
        request.setRemoteAddr("192.168.1.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
    }

    @Test
    void rate_limit_exceeded_returns_429() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/projects");
        request.setRemoteAddr("192.168.1.1");

        // 发超过限制数量的请求
        int maxRequests = 100;
        for (int i = 0; i <= maxRequests; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());

            if (i >= maxRequests) {
                assertEquals(429, response.getStatus(), "应超过速率限制");
                String content = response.getContentAsString();
                assertTrue(content.contains("频繁") || content.contains("频繁"));
            }
        }
    }

    @Test
    void different_ips_have_separate_limits() throws Exception {
        MockHttpServletRequest request1 = new MockHttpServletRequest("POST", "/api/projects");
        request1.setRemoteAddr("192.168.1.1");

        // 发 150 个请求从 IP1
        for (int i = 0; i < 150; i++) {
            MockHttpServletResponse response1 = new MockHttpServletResponse();
            filter.doFilter(request1, response1, new MockFilterChain());
        }

        // IP2 不受 IP1 影响
        MockHttpServletRequest request2 = new MockHttpServletRequest("POST", "/api/projects");
        request2.setRemoteAddr("192.168.2.1");
        MockHttpServletResponse response2 = new MockHttpServletResponse();
        filter.doFilter(request2, response2, new MockFilterChain());
        assertEquals(200, response2.getStatus(), "IP2 不应被限制");
    }

    @Test
    void options_request_not_rate_limited() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/projects");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
    }

    @Test
    void delete_to_agent_path_passes() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/deploy/1/cancel");
        request.setRemoteAddr("192.168.1.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
    }
}
