package com.ops.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * 安全配置类 — 启动时校验 jwt.secret 是否存在
 * <p>
 * 注意：当前项目的用户 token 实际由 {@link com.ops.server.controller.SystemController#generateToken()}
 * 使用 SecureRandom 生成随机字符串，并非标准 JWT（无签名/验签）。
 * jwt.secret 仅在此做启动存在性校验，未被任何签发或解析逻辑引用。
 * Agent 认证使用独立的 AGENT_TOKEN（X-Token header），前端仅透传 token，均与此值无关。
 * 本地开发使用 application.yml 中的默认值即可，无需设置环境变量。
 */
@Configuration
@Order(0)
public class SecurityConfig {

    @Value("${jwt.secret:#{null}}")
    private String jwtSecret;

    @Value("${jwt.expire-ms:86400000}")
    private long jwtExpireMs;

    /**
     * 启动时校验：必须通过环境变量提供 JWT 密钥
     */
    @Bean
    public CommandLineRunner validateSecurityConfig() {
        return args -> {
            if (jwtSecret == null || jwtSecret.trim().isEmpty()) {
                throw new IllegalStateException(
                    "SECURITY VIOLATION: jwt.secret is not configured. " +
                    "Please set the JWT_SECRET environment variable. " +
                    "Example: export JWT_SECRET='your-random-256-bit-key-here'"
                );
            }
            if (jwtSecret.trim().length() < 32) {
                throw new IllegalArgumentException(
                    "JWT_SECRET must be at least 32 characters long. " +
                    "Current length: " + jwtSecret.trim().length()
                );
            }
            System.out.println("[SecurityConfig] JWT secret loaded from environment. Expire: " + jwtExpireMs + "ms");
        };
    }

    @Bean
    public long jwtExpireMs() {
        return jwtExpireMs;
    }

    public String getJwtSecret() {
        return jwtSecret;
    }
}
