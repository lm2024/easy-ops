package com.ops.server.config;

import com.ops.common.model.UserModel;
import com.ops.server.mapper.UserMapper;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理员初始化 —— 启动时执行一次
 *
 * 密码来源：application.yml → app.admin.default-password（通过 AdminConfig 读取）
 *
 * 优先级：YML 配置 > 用户自己设置的密码
 *
 * 行为逻辑：
 *   ┌─ 无 admin 用户 → INSERT 创建（用 YML 配置的密码）
 *   │
 *   └─ 有 admin 用户 → 每次启动都用 YML 密码覆盖
 *       （YML 配置永远是第一优先级）
 */
@Component
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private AdminConfig adminConfig;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional(rollbackFor = Exception.class)
    public void initAdminUser() {
        String defaultPwd = adminConfig.getDefaultPassword();
        if (defaultPwd == null || defaultPwd.trim().isEmpty()) {
            log.warn("[DataInit] app.admin.default-password 未配置，跳过管理员初始化");
            return;
        }

        UserModel admin = userMapper.findByUsername("admin");
        long now = System.currentTimeMillis();

        if (admin == null) {
            createAdmin(defaultPwd, now);
            return;
        }

        // 每次启动都用 YML 密码覆盖（YML 配置永远是第一优先级）
        String newHash = BCrypt.hashpw(defaultPwd, BCrypt.gensalt(10));
        admin.setPassword(newHash);
        admin.setUpdateTime(now);
        userMapper.update(admin);
        log.info("[DataInit] 已同步管理员密码（来源：app.admin.default-password）");
    }

    private void createAdmin(String defaultPwd, long now) {
        String hash = BCrypt.hashpw(defaultPwd, BCrypt.gensalt(10));
        UserModel newAdmin = new UserModel();
        newAdmin.setUsername("admin");
        newAdmin.setPassword(hash);
        newAdmin.setRole("admin");
        newAdmin.setStatus(1);
        newAdmin.setCreateTime(now);
        newAdmin.setUpdateTime(now);
        userMapper.insert(newAdmin);
        log.info("[DataInit] 已创建默认管理员（密码来自 app.admin.default-password）");
    }
}
