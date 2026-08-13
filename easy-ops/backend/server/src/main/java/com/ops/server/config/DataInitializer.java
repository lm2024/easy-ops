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
        } else if (defaultTenant.getStatus() == null || defaultTenant.getStatus() != 1) {
            // 兜底：默认租户被误禁用时自动恢复，确保管理员始终有可用租户
            defaultTenant.setStatus(1);
            defaultTenant.setUpdateTime(now);
            tenantMapper.update(defaultTenant);
            log.warn("[DataInit] 默认租户已被禁用，已自动恢复启用");
        }
        migrateLegacyRows(defaultTenant.getId(), now);

        // 兜底：无论密码是否配置，都确保 admin 用户和租户成员关系存在
        // 防止 admin 的 tenant_user 记录被误删导致管理员无法访问租户功能
        UserModel admin = userMapper.findByUsername("admin");
        if (admin != null) {
            ensureAdminMember(admin, defaultTenant, now);
        }

        if (defaultPwd == null || defaultPwd.trim().isEmpty()) {
            log.warn("[DataInit] app.admin.default-password 未配置，跳过管理员密码初始化");
            return;
        }

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
        // 顶层：节点/项目归属默认租户
        jdbcTemplate.update("UPDATE node_info SET tenant_id = ? WHERE tenant_id = 0 OR tenant_id IS NULL", tenantId);
        jdbcTemplate.update("UPDATE project_info SET tenant_id = ? WHERE tenant_id = 0 OR tenant_id IS NULL", tenantId);

        // 经 project_id 继承 project_info.tenant_id
        String[] viaProject = {
                "version_package", "deploy_record", "monitor_snapshot", "alarm_record",
                "self_heal_policy", "self_heal_event", "notification_record", "ai_diagnosis_record",
                "project_config_file", "node_config_snapshot", "config_distribute_record",
                "project_log_profile", "project_health_probe",
                "project_script_file", "script_distribute_record", "node_script_snapshot",
                "kb_document", "kb_category"
        };
        for (String table : viaProject) {
            jdbcTemplate.update("UPDATE " + table + " t SET tenant_id = "
                    + "(SELECT p.tenant_id FROM project_info p WHERE p.id = t.project_id) "
                    + "WHERE (t.tenant_id IS NULL OR t.tenant_id = 0) AND t.project_id IS NOT NULL");
        }

        // 经 node_id 继承 node_info.tenant_id
        String[] viaNode = {
                "agent_upgrade_record", "nginx_access_source"
        };
        for (String table : viaNode) {
            jdbcTemplate.update("UPDATE " + table + " t SET tenant_id = "
                    + "(SELECT n.tenant_id FROM node_info n WHERE n.id = t.node_id) "
                    + "WHERE (t.tenant_id IS NULL OR t.tenant_id = 0) AND t.node_id IS NOT NULL");
        }

        // 经 source_id 继承 nginx_access_source.tenant_id
        String[] viaSource = {
                "nginx_source_whitelist", "nginx_traffic_alarm_rule"
        };
        for (String table : viaSource) {
            jdbcTemplate.update("UPDATE " + table + " t SET tenant_id = "
                    + "(SELECT s.tenant_id FROM nginx_access_source s WHERE s.id = t.source_id) "
                    + "WHERE (t.tenant_id IS NULL OR t.tenant_id = 0) AND t.source_id IS NOT NULL");
        }

        // 兜底：仍为 0/NULL 的资源行归默认租户（保证隔离查询不漏）
        String[] fallback = {
                "version_package", "deploy_record", "monitor_snapshot", "alarm_record",
                "self_heal_policy", "self_heal_event", "notification_record", "ai_diagnosis_record",
                "project_config_file", "node_config_snapshot", "config_distribute_record",
                "project_log_profile", "project_health_probe",
                "project_script_file", "script_distribute_record", "node_script_snapshot",
                "agent_upgrade_record", "nginx_access_source",
                "nginx_source_whitelist", "nginx_traffic_alarm_rule",
                "kb_document", "kb_category"
        };
        for (String table : fallback) {
            jdbcTemplate.update("UPDATE " + table + " SET tenant_id = ? WHERE tenant_id IS NULL OR tenant_id = 0", tenantId);
        }

        // 用户成员关系：一次性迁移（用 sys_config 标记），仅对「无任何租户成员」的存量用户补 default 租户
        // 避免每次重启把被管理员移除成员的用户重新加回 default 租户
        String membershipMigrated = null;
        try {
            membershipMigrated = jdbcTemplate.queryForObject(
                    "SELECT config_value FROM sys_config WHERE config_key = 'tenant_membership_migrated'", String.class);
        } catch (Exception ignored) {
        }
        if (membershipMigrated == null) {
            jdbcTemplate.update("INSERT INTO tenant_user (tenant_id, user_id, role, status, create_time, update_time) "
                    + "SELECT ?, id, CASE WHEN LOWER(role) = 'admin' THEN 'SUPER_ADMIN' ELSE 'OPERATOR' END, 1, ?, ? "
                    + "FROM sys_user u WHERE NOT EXISTS (SELECT 1 FROM tenant_user tu WHERE tu.user_id = u.id)",
                    tenantId, now, now);
            try {
                jdbcTemplate.update("MERGE INTO sys_config (config_key, config_value, update_time) "
                        + "KEY (config_key) VALUES ('tenant_membership_migrated', '1', ?)", now);
            } catch (Exception ignored) {
            }
        }
    }
}
