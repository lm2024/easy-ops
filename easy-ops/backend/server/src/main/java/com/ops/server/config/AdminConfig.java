package com.ops.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 管理员配置 —— 唯一来源：application.yml → app.admin.default-password
 * 所有代码（初始化、重置 API）都从这个类读取，不硬编码。
 */
@Component
@ConfigurationProperties(prefix = "app.admin")
public class AdminConfig {

    /** 默认管理员密码（明文）—— 来自 YML 配置 */
    private String defaultPassword;

    public String getDefaultPassword() {
        return defaultPassword;
    }

    public void setDefaultPassword(String defaultPassword) {
        this.defaultPassword = defaultPassword;
    }
}
