package com.ops.server.config;

import com.ops.common.model.UserModel;
import com.ops.server.mapper.SysConfigMapper;
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
 * 行为逻辑：
 *   ┌─ 无 admin 用户 → INSERT 创建（用 YML 配置的密码）
 *   │
 *   ├─ 有 admin 用户 ─┬─ 用户手动改过密码 → 不覆盖
 *   │                 │   （sys_config.admin_password_is_default = false）
 *   │                 │
 *   │                 └─ 仍是默认密码 → 用 YML 最新配置刷新
 *   │                     （支持改 YML 后重启更新）
 */
@Component
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    static final String CONFIG_KEY = "admin_password_is_default";

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private SysConfigMapper sysConfigMapper;

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

        String isDefault = sysConfigMapper.getValue(CONFIG_KEY);
        if ("false".equals(isDefault)) {
            log.info("[DataInit] 管理员密码已被用户手动修改，保留用户设置");
            return;
        }

        String newHash = BCrypt.hashpw(defaultPwd, BCrypt.gensalt(10));
        admin.setPassword(newHash);
        admin.setUpdateTime(now);
        userMapper.update(admin);
        log.info("[DataInit] 已刷新管理员默认密码（来源：app.admin.default-password）");
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

        sysConfigMapper.upsert(CONFIG_KEY, "true", "管理员是否使用默认密码", System.currentTimeMillis());
        log.info("[DataInit] 已创建默认管理员（密码来自 app.admin.default-password）");
    }
}
