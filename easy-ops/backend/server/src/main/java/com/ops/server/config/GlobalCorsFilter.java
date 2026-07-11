package com.ops.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 全局 CORS 过滤器：在 context-path=/api 场景下统一处理跨域，支持任意来源。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalCorsFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED_METHODS = new HashSet<>(Arrays.asList(
            "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD"));
    private static final String ALLOWED_HEADERS = "Authorization, Content-Type, Accept, X-CSRF-Token, X-Token, X-Requested-With";
    private static final String EXPOSED_HEADERS = "Authorization, Content-Disposition";

    @Value("${cors.allowed-origins:*}")
    private String allowedOrigins;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        if (origin != null && !origin.isEmpty() && isOriginAllowed(origin)) {
            response.setHeader("Access-Control-Allow-Origin", origin);
            response.setHeader("Access-Control-Allow-Credentials", "true");
            response.setHeader("Vary", "Origin");
        } else if (isWildcardAllowed()) {
            response.setHeader("Access-Control-Allow-Origin", "*");
        }

        response.setHeader("Access-Control-Allow-Methods", String.join(", ", ALLOWED_METHODS));
        response.setHeader("Access-Control-Allow-Headers", ALLOWED_HEADERS);
        response.setHeader("Access-Control-Expose-Headers", EXPOSED_HEADERS);
        response.setHeader("Access-Control-Max-Age", "3600");

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isWildcardAllowed() {
        if (allowedOrigins == null) {
            return true;
        }
        for (String part : allowedOrigins.split(",")) {
            if ("*".equals(part.trim())) {
                return true;
            }
        }
        return false;
    }

    private boolean isOriginAllowed(String origin) {
        if (isWildcardAllowed()) {
            return true;
        }
        for (String part : allowedOrigins.split(",")) {
            String allowed = part.trim();
            if (allowed.isEmpty()) {
                continue;
            }
            if (allowed.equals(origin)) {
                return true;
            }
            if (allowed.contains("*") && matchPattern(allowed, origin)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchPattern(String pattern, String origin) {
        String regex = pattern.replace(".", "\\.").replace("*", ".*");
        return origin.matches(regex);
    }
}
