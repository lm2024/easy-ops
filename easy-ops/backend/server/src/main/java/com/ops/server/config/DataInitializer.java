package com.ops.server.config;

import com.ops.common.model.UserModel;
import com.ops.server.mapper.UserMapper;
import com.ops.server.mapper.TenantMapper;
import com.ops.common.model.TenantModel;
import com.ops.common.model.TenantUserModel;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

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

    @Autowired
    private TenantMapper tenantMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional(rollbackFor = Exception.class)
    public void initAdminUser() {
        String defaultPwd = adminConfig.getDefaultPassword();
        long now = System.currentTimeMillis();
        TenantModel defaultTenant = tenantMapper.findDefault();
        if (defaultTenant == null) {
            defaultTenant = new TenantModel();
            defaultTenant.setCode("default");
            defaultTenant.setName("默认租户");
            defaultTenant.setStatus(1);
            defaultTenant.setCreateTime(now);
            defaultTenant.setUpdateTime(now);
            tenantMapper.insert(defaultTenant);
            log.info("[DataInit] 已创建默认租户");
        }
        migrateLegacyRows(defaultTenant.getId(), now);

        if (defaultPwd == null || defaultPwd.trim().isEmpty()) {
            log.warn("[DataInit] app.admin.default-password 未配置，跳过管理员密码初始化");
            return;
        }

        UserModel admin = userMapper.findByUsername("admin");

        if (admin == null) {
            createAdmin(defaultPwd, now);
            return;
        }

        // 每次启动都用 YML 密码覆盖（YML 配置永远是第一优先级）
        String newHash = BCrypt.hashpw(defaultPwd, BCrypt.gensalt(10));
        admin.setPassword(newHash);
        admin.setUpdateTime(now);
        userMapper.update(admin);
        ensureAdminMember(admin, defaultTenant, now);
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
        TenantModel defaultTenant = tenantMapper.findDefault();
        if (defaultTenant != null) {
            ensureAdminMember(newAdmin, defaultTenant, now);
        }
        log.info("[DataInit] 已创建默认管理员（密码来自 app.admin.default-password）");
    }

    private void ensureAdminMember(UserModel admin, TenantModel tenant, long now) {
        if (tenantMapper.findMember(tenant.getId(), admin.getId()) != null) return;
        TenantUserModel member = new TenantUserModel();
        member.setTenantId(tenant.getId());
        member.setUserId(admin.getId());
        member.setRole("SUPER_ADMIN");
        member.setStatus(1);
        member.setCreateTime(now);
        member.setUpdateTime(now);
        tenantMapper.insertMember(member);
    }

    private void migrateLegacyRows(Long tenantId, long now) {
        jdbcTemplate.update("UPDATE node_info SET tenant_id = ? WHERE tenant_id = 0 OR tenant_id IS NULL", tenantId);
        jdbcTemplate.update("UPDATE project_info SET tenant_id = ? WHERE tenant_id = 0 OR tenant_id IS NULL", tenantId);
        jdbcTemplate.update("INSERT INTO tenant_user (tenant_id, user_id, role, status, create_time, update_time) "
                + "SELECT ?, id, CASE WHEN LOWER(role) = 'admin' THEN 'SUPER_ADMIN' ELSE 'OPERATOR' END, 1, ?, ? "
                + "FROM sys_user u WHERE NOT EXISTS (SELECT 1 FROM tenant_user tu WHERE tu.tenant_id = ? AND tu.user_id = u.id)",
                tenantId, now, now, tenantId);
    }
}
