package com.ops.server.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * SEC-008: 密钥绕过审计过滤器
 *
 * 审计所有对敏感配置端点的访问：
 *   1. 登录接口记录密码长度（不记录明文）
 *   2. 配置保存接口记录 key 名称（不记录 value）
 *   3. 日志中记录审计信息供安全审计追踪
 */
@Component
public class KeyAuditFilter implements Filter {

    private static final Logger auditLog = LoggerFactory.getLogger("SECURITY_AUDIT");
    private static final Logger log = LoggerFactory.getLogger(KeyAuditFilter.class);

    private static final Set<String> AUDIT_PATHS = new HashSet<>(Arrays.asList(
            "/auth/login",
            "/sys/config"
    ));

    // 敏感的 sys_config key 名称（不需要审计其 value，但要审计谁在访问）
    private static final Set<String> SENSITIVE_CONFIG_KEYS = new HashSet<>(Arrays.asList(
            "ai.apiKey",
            "jwt.secret"
    ));

    @Override
    public void init(FilterConfig filterConfig) {
        // no-op
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String uri = httpRequest.getRequestURI();
        String method = httpRequest.getMethod();

        // 非目标路径直接放行
        boolean shouldAudit = false;
        for (String pattern : AUDIT_PATHS) {
            if (uri.equals(pattern)) {
                shouldAudit = true;
                break;
            }
        }

        if (shouldAudit && "POST".equalsIgnoreCase(method)) {
            auditSensitiveRequest(httpRequest);
        }

        chain.doFilter(request, response);
    }

    private void auditSensitiveRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();

        if (uri.equals("/auth/login")) {
            String username = request.getParameter("username");
            // 记录登录尝试（不记录密码明文）
            String remoteAddr = request.getRemoteAddr();
            auditLog.warn("SECURITY_AUDIT: Login attempt for user='{}' from IP='{}'",
                    maskUsername(username), remoteAddr);

        } else if (uri.equals("/sys/config")) {
            String key = request.getParameter("key") != null ? request.getParameter("key") : "";
            if (SENSITIVE_CONFIG_KEYS.contains(key)) {
                String operator = request.getHeader("Authorization") != null ? "authenticated" : "anonymous";
                auditLog.warn("SECURITY_AUDIT: Sensitive config access key='{}' by='{}' from IP='{}'",
                        key, operator, request.getRemoteAddr());
            }
        }
    }

    /**
     * 掩码用户名：只显示首尾各 1 个字符
     */
    private String maskUsername(String username) {
        if (username == null || username.length() <= 2) {
            return "***";
        }
        return username.charAt(0) + "***" + username.charAt(username.length() - 1);
    }

    @Override
    public void destroy() {
        // no-op
    }
}
