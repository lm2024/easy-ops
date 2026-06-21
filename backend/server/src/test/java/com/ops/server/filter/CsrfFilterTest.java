package com.ops.server.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SEC-005: CsrfFilter 单元测试
 */
class CsrfFilterTest {

    private CsrfFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new CsrfFilter();
        chain = new MockFilterChain();
    }

    @Test
    void get_request_passes() throws Exception {
        request = new MockHttpServletRequest("GET", "/api/projects");
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        // GET is safe, should pass through
    }

    @Test
    void options_request_passes() throws Exception {
        request = new MockHttpServletRequest("OPTIONS", "/api/projects");
        filter.doFilter(request, new MockHttpServletResponse(), chain);
    }

    @Test
    void excluded_paths_skip_csrf_check() throws Exception {
        request = new MockHttpServletRequest("POST", "/auth/login");
        response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        // login is excluded, should not fail
    }

    @Test
    void heartbeat_excluded() throws Exception {
        request = new MockHttpServletRequest("POST", "/nodes/heartbeat");
        response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
    }

    @Test
    void agent_token_exempt_from_csrf() throws Exception {
        request = new MockHttpServletRequest("POST", "/api/deploy");
        request.addHeader("X-Token", "agent-token-123");
        response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        // Agent tokens are exempt from CSRF
    }

    @Test
    void post_without_auth_passes_through() throws Exception {
        request = new MockHttpServletRequest("POST", "/api/projects");
        response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        // No auth header = skip CSRF (AuthInterceptor handles auth)
    }

    @Test
    void post_with_bearer_and_matching_csrf_passes() throws Exception {
        request = new MockHttpServletRequest("POST", "/api/projects");
        request.addHeader("Authorization", "Bearer my-token");
        request.addHeader("X-CSRF-Token", "my-token");
        response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
    }

    @Test
    void post_with_bearer_missing_csrf_fails() throws Exception {
        request = new MockHttpServletRequest("POST", "/api/projects");
        request.addHeader("Authorization", "Bearer my-token");
        // No X-CSRF-Token
        response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        assertEquals(403, response.getStatus());
        String content = response.getContentAsString();
        assertTrue(content.contains("CSRF"));
    }

    @Test
    void post_with_bearer_wrong_csrf_fails() throws Exception {
        request = new MockHttpServletRequest("POST", "/api/projects");
        request.addHeader("Authorization", "Bearer my-token");
        request.addHeader("X-CSRF-Token", "wrong-token");
        response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        assertEquals(403, response.getStatus());
    }

    @Test
    void put_with_matching_tokens_passes() throws Exception {
        request = new MockHttpServletRequest("PUT", "/api/projects/1");
        request.addHeader("Authorization", "Bearer project-token");
        request.addHeader("X-CSRF-Token", "project-token");
        response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
    }

    @Test
    void delete_with_matching_tokens_passes() throws Exception {
        request = new MockHttpServletRequest("DELETE", "/api/projects/1");
        request.addHeader("Authorization", "Bearer delete-token");
        request.addHeader("X-CSRF-Token", "delete-token");
        response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
    }

    @Test
    void delete_without_csrf_token_fails() throws Exception {
        request = new MockHttpServletRequest("DELETE", "/api/projects/1");
        request.addHeader("Authorization", "Bearer my-token");
        response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        assertEquals(403, response.getStatus());
    }
}
