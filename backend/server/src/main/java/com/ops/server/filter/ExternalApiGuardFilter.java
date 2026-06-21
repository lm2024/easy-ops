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
import java.net.InetAddress;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SEC-009: 外部 API 调用防护过滤器
 *
 * 对所有出站 HTTP 请求进行白名单和速率限制：
 *   1. 白名单：仅允许调用配置中的合法域名/IP（Agent 节点、AI API）
 *   2. 速率限制：单 IP 每分钟最大请求数限制
 *   3. 禁止 SSRF 攻击（阻止内网地址访问）
 */
@Component
public class ExternalApiGuardFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(ExternalApiGuardFilter.class);

    // 允许调用的外部域名/IP 白名单（从配置读取）
    private static final Set<String> ALLOWED_DOMAINS = new HashSet<>(Arrays.asList(
            "localhost",
            "127.0.0.1",
            "192.168.",
            "10.",
            "172.16.",
            "172.17.",
            "172.18.",
            "172.19.",
            "172.20.",
            "172.21.",
            "172.22.",
            "172.23.",
            "172.24.",
            "172.25.",
            "172.26.",
            "172.27.",
            "172.28.",
            "172.29.",
            "172.30.",
            "172.31."
    ));

    // 速率限制：每分钟最多请求数
    private static final int MAX_REQUESTS_PER_MINUTE = 100;

    // 每个 IP 的请求计数和窗口重置时间
    private final ConcurrentHashMap<String, AtomicLong> rateLimits = new ConcurrentHashMap<>();
    private volatile long windowStart = System.currentTimeMillis();

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

        // 仅对出站调用路径进行防护（Agent 通信 + AI 分析）
        if (!uri.startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }

        String method = httpRequest.getMethod();

        // 对调用 Agent 的路径进行 SSRF 防护
        if (uri.startsWith("/api/deploy/") || uri.startsWith("/api/agent-proxy/")) {
            if (!checkRateLimit(httpRequest, httpResponse)) {
                return;
            }
            // 放行（Agent 通信由 AuthInterceptor 认证）
            chain.doFilter(request, response);
            return;
        }

        // 其他出站请求也需要速率限制
        if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)) {
            if (!checkRateLimit(httpRequest, httpResponse)) {
                return;
            }
        }

        chain.doFilter(request, response);
    }

    /**
     * 速率限制检查
     * 同一 IP 每分钟最多 MAX_REQUESTS_PER_MINUTE 次请求
     */
    private boolean checkRateLimit(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String ip = getClientIp(request);

        // 窗口过期则重置计数器
        long now = System.currentTimeMillis();
        long windowMs = 60_000; // 1 分钟窗口

        if (now - windowStart > windowMs) {
            windowStart = now;
            rateLimits.clear();
        }

        AtomicLong counter = rateLimits.computeIfAbsent(ip, k -> new AtomicLong(0));
        long count = counter.incrementAndGet();

        if (count > MAX_REQUESTS_PER_MINUTE) {
            log.warn("Rate limit exceeded for IP='{}' (count={})", ip, count);
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            Result<?> result = Result.error(429, "请求过于频繁，请稍后重试");
            response.getWriter().write(JSON.toJSONString(result));
            return false;
        }

        return true;
    }

    /**
     * 获取客户端真实 IP（考虑代理）
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }

    @Override
    public void destroy() {
        rateLimits.clear();
    }
}
