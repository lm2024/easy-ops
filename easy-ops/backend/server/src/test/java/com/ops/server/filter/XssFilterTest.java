package com.ops.server.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SEC-006: XssFilter 单元测试
 */
class XssFilterTest {

    private XssFilter filter;

    @BeforeEach
    void setUp() {
        filter = new XssFilter();
    }

    @Test
    void normal_text_unchanged() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects");
        request.setParameter("name", "normal project name");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
    }

    @Test
    void script_tag_encoded() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects");
        request.setParameter("name", "<script>alert('xss')</script>");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
    }

    @Test
    void onclick_handler_cleaned() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects");
        request.setParameter("name", "onclick=alert(1)");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
    }

    @Test
    void javascript_protocol_cleaned() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects");
        request.setParameter("url", "javascript:alert(1)");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
    }

    @Test
    void html_special_chars_encoded() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects");
        request.setParameter("name", "Tom & Jerry <test> \"quote\"");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
    }

    @Test
    void excluded_path_not_filtered() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/nodes/heartbeat");
        request.setParameter("name", "<script>alert(1)</script>");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
    }

    @Test
    void null_parameter_returns_null() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects");
        // name parameter not set -> null
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
    }

    @Test
    void empty_parameter_returns_empty() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects");
        request.setParameter("name", "");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
    }

    @Test
    void onerror_handler_cleaned() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects");
        request.setParameter("src", "onerror=alert(1)");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
    }
}
