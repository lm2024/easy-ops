package com.ops.server.util;

import com.ops.server.mapper.UserProjectRelationMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Collections;

/**
 * 安全上下文工具 (SEC-003/SEC-004)
 * 从当前请求中提取用户信息和项目访问权限
 */
@Component
public class SecurityContext {

    @Autowired
    private HttpServletRequest request;

    @Autowired
    private UserProjectRelationMapper userProjectRelationMapper;

    /**
     * 获取当前用户ID（从请求属性）
     */
    public Long getCurrentUserId() {
        Object attr = request.getAttribute("currentUserId");
        if (attr == null) return null;
        return Long.parseLong(attr.toString());
    }

    /**
     * 获取当前用户名（从请求属性）
     */
    public String getCurrentUsername() {
        return (String) request.getAttribute("currentUsername");
    }

    public Long getCurrentTenantId() {
        Object attr = request.getAttribute("currentTenantId");
        if (attr == null) return null;
        try { return Long.valueOf(attr.toString()); } catch (NumberFormatException e) { return null; }
    }

    /**
     * 获取当前用户角色（从请求属性）
     */
    public String getCurrentRole() {
        return (String) request.getAttribute("currentRole");
    }

    /**
     * 获取当前 nodeId（Agent 请求）
     */
    public String getCurrentNodeId() {
        return (String) request.getAttribute("currentNodeId");
    }

    /**
     * 获取当前用户在某租户内的角色（TENANT_ADMIN / OPERATOR / VIEWER）。
     * 来自 tenant_user.role，由 AuthInterceptor 写入请求属性。
     */
    public String getTenantRole() {
        return (String) request.getAttribute("currentTenantRole");
    }

    /**
     * 判断当前用户是否为平台管理员（sys_user.role = admin / super_admin）。
     * 平台管理员跨租户管理所有租户、用户、平台配置。
     */
    public boolean isSuperAdmin() {
        String role = getCurrentRole();
        return role != null && (role.equalsIgnoreCase("admin") || role.equalsIgnoreCase("super_admin"));
    }

    /**
     * 判断当前用户在某租户内是否为租户管理员（tenant_user.role = TENANT_ADMIN）。
     */
    public boolean isTenantAdmin() {
        String tenantRole = getTenantRole();
        return tenantRole != null && tenantRole.equalsIgnoreCase("TENANT_ADMIN");
    }

    /**
     * 判断当前用户在某租户内是否为只读 VIEWER。
     */
    public boolean isViewer() {
        String tenantRole = getTenantRole();
        return tenantRole != null && tenantRole.equalsIgnoreCase("VIEWER");
    }

    /**
     * 获取当前用户可访问的 projectIds 列表 (SEC-004)
     * 如果是 admin 角色，返回所有 projectIds
     */
    public List<Long> getAccessibleProjectIds() {
        String role = getCurrentRole();
        Long tenantId = getCurrentTenantId();
        if (role != null && (role.equalsIgnoreCase("admin") || role.equalsIgnoreCase("super_admin"))) {
            return tenantId == null ? userProjectRelationMapper.findAllProjectIds()
                    : userProjectRelationMapper.findAllProjectIdsByTenant(tenantId);
        }
        Long userId = getCurrentUserId();
        if (userId == null) {
            return null;
        }
        return tenantId == null ? userProjectRelationMapper.findProjectIdsByUserId(userId)
                : userProjectRelationMapper.findProjectIdsByUserIdAndTenant(userId, tenantId);
    }

    /**
     * 判断当前用户是否为管理员
     */
    public boolean isAdmin() {
        String role = getCurrentRole();
        return role != null && role.equalsIgnoreCase("admin");
    }

    /** 别名，与 isSuperAdmin() 等价。保留向后兼容。 */
    public boolean isPlatformAdmin() {
        return isSuperAdmin();
    }

    public List<Long> getAccessibleProjectIdsForQuery() {
        List<Long> ids = getAccessibleProjectIds();
        return ids == null ? Collections.singletonList(-1L) : ids;
    }

    /**
     * 判断当前用户是否有权限访问指定项目 (SEC-004)
     * admin 角色拥有所有项目的权限
     */
    public boolean hasProjectPermission(Long projectId) {
        if (projectId == null) return true;
        String role = getCurrentRole();
        if (role != null && (role.equalsIgnoreCase("admin") || role.equalsIgnoreCase("super_admin"))) {
            return true;
        }
        Long userId = getCurrentUserId();
        if (userId == null) {
            return false;
        }
        Long tenantId = getCurrentTenantId();
        if (tenantId == null) {
            return userProjectRelationMapper.countByUserIdAndProjectId(userId, projectId) > 0;
        }
        return userProjectRelationMapper.countByUserIdAndProjectIdAndTenant(userId, projectId, tenantId) > 0;
    }
}
