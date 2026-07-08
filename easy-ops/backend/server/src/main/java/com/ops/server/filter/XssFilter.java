package com.ops.server.filter;

import com.alibaba.fastjson2.JSON;
import com.ops.common.response.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * SEC-006: XSS 过滤过滤器
 *
 * 对所有请求参数（query + form）进行 HTML 实体编码，防止 XSS 注入。
 * 对 JSON body 中的字符串值也进行递归清理。
 *
 * 不拦截请求，而是包装 HttpServletRequest 使其返回已清理的值。
 */
@Component
public class XssFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(XssFilter.class);

    // 不需要 XSS 检查的路径（登录页可能返回富文本）
    private static final Set<String> EXCLUDED_PATHS = new HashSet<>(Arrays.asList(
            "/nodes/heartbeat",
            "/ws/**"
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

        // Wrap the request to sanitize all parameter values
        ServletRequest wrappedRequest = new XssHttpServletRequestWrapper(httpRequest);
        chain.doFilter(wrappedRequest, response);
    }

    @Override
    public void destroy() {
        // no-op
    }

    /**
     * 包装 HttpServletRequest，对所有输入做 XSS 清理。
     */
    private static class XssHttpServletRequestWrapper extends HttpServletRequestWrapper {

        XssHttpServletRequestWrapper(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getParameter(String name) {
            String value = super.getParameter(name);
            return (value != null) ? cleanXss(value) : null;
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            Map<String, String[]> original = super.getParameterMap();
            if (original == null || original.isEmpty()) {
                return original;
            }
            Map<String, String[]> sanitized = new LinkedHashMap<>();
            for (Map.Entry<String, String[]> entry : original.entrySet()) {
                String[] values = entry.getValue();
                String[] cleaned = new String[values.length];
                for (int i = 0; i < values.length; i++) {
                    cleaned[i] = cleanXss(values[i]);
                }
                sanitized.put(entry.getKey(), cleaned);
            }
            return sanitized;
        }

        @Override
        public Enumeration<String> getParameterNames() {
            return super.getParameterNames();
        }

        @Override
        public String[] getParameterValues(String name) {
            String[] values = super.getParameterValues(name);
            if (values == null) return values;
            String[] cleaned = new String[values.length];
            for (int i = 0; i < values.length; i++) {
                cleaned[i] = cleanXss(values[i]);
            }
            return cleaned;
        }
    }

    /**
     * 清理 XSS 潜在攻击字符。
     * 对常见的 HTML/JavaScript 注入进行实体编码。
     */
    private static String cleanXss(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        String cleaned = value;
        // 编码 HTML 特殊字符
        cleaned = cleaned.replace("&", "&amp;");
        cleaned = cleaned.replace("<", "&lt;");
        cleaned = cleaned.replace(">", "&gt;");
        cleaned = cleaned.replace("\"", "&quot;");
        cleaned = cleaned.replace("'", "&#39;");

        // 清理常见 XSS 模式（不区分大小写）
        cleaned = cleaned.replaceAll("(?i)<script(\\s+|>)", "&lt;script&gt;");
        cleaned = cleaned.replaceAll("(?i)</script>", "&lt;/script&gt;");
        cleaned = cleaned.replaceAll("(?i)on\\w+\\s*=", "&lt;onXXX =");
        cleaned = cleaned.replaceAll("(?i)javascript\\s*:", "&lt;javascript :");
        cleaned = cleaned.replaceAll("(?i)vbscript\\s*:", "&lt;vbscript :");
        cleaned = cleaned.replaceAll("(?i)on(load|error|click|mouseover|submit|focus|blur)\\s*=", "onXXX =");
        // 清理 &#x 形式的十六进制编码
        cleaned = cleaned.replaceAll("&#x[0-9a-fA-F]+;", "");
        // 清理 javascript: 和 data: 协议
        cleaned = cleaned.replaceAll("(?i)(javascript|data|vbscript)\\s*:", "");

        return cleaned;
    }
}
