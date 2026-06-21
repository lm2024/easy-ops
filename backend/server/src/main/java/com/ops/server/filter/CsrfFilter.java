package com.ops.server.filter;

import com.alibaba.fastjson2.JSON;
import com.ops.common.response.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * SEC-005: CSRF 防护过滤器
 *
 * 本项目采用 Token 认证（非 Session），因此在 API 场景下，
 * Authorization 头本身即为 CSRF 防护。但考虑到前端可能通过表单
 * 提交（如浏览器登录页），本过滤器要求所有 POST/PUT/DELETE 请求：
 *   1. 已认证（有 Authorization 或 X-Token 头）
 *   2. 携带 X-CSRF-Token 与 Auth header 中的 token 一致
 *
 * 对于 Agent 请求（X-Token 头），豁免 CSRF 检查（Agent 间通信）。
 */
@Component
public class CsrfFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(CsrfFilter.class);

    // 不需要 CSRF 检查的路径
    private static final Set<String> EXCLUDED_PATHS = new HashSet<>(Arrays.asList(
            "/auth/login",
            "/nodes/heartbeat",
            "/ws/**",
            "/h2-console/**"
    ));

    private static final Set<String> SAFE_METHODS = new HashSet<>(Arrays.asList(
            "GET", "OPTIONS", "HEAD"
    ));

    @Override
    public void init(FilterConfig filterConfig) {
        // no-op
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String uri = httpRequest.getRequestURI();
        String method = httpRequest.getMethod();

        // Skip excluded paths
        for (String pattern : EXCLUDED_PATHS) {
            if (pattern.endsWith("/**")) {
                String prefix = pattern.substring(0, pattern.length() - 3);
                if (uri.startsWith(prefix)) {
                    chain.doFilter(request, response);
                    return;
                }
            } else if (uri.equals(pattern)) {
                chain.doFilter(request, response);
                return;
            }
        }

        // Only protect POST/PUT/DELETE
        if (SAFE_METHODS.contains(method.toUpperCase())) {
            chain.doFilter(request, response);
            return;
        }

        // Agent requests (X-Token) are exempt - they use mTLS conceptually
        String agentToken = httpRequest.getHeader("X-Token");
        if (agentToken != null && !agentToken.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }

        // User requests require CSRF token
        String authHeader = httpRequest.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String csrfToken = httpRequest.getHeader("X-CSRF-Token");
            // Extract the actual token from Bearer header
            String authValue = authHeader.substring(7).trim();
            // CSRF token must match (or be the same as) the auth token
            if (csrfToken != null && csrfToken.trim().equals(authValue)) {
                chain.doFilter(request, response);
                return;
            }
        }

        // If no Authorization header, skip CSRF check (AuthInterceptor will handle)
        if (authHeader == null || authHeader.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }

        // CSRF check failed
        log.warn("CSRF validation failed for {}: {}", method, uri);
        sendError(httpResponse, 403, "CSRF token missing or invalid");
    }

    @Override
    public void destroy() {
        // no-op
    }

    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        Result<?> result = Result.error(status, message);
        response.getWriter().write(JSON.toJSONString(result));
    }
}
