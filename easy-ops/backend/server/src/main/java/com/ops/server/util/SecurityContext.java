package com.ops.server.util;

import com.ops.server.mapper.UserProjectRelationMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

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
     * 获取当前用户可访问的 projectIds 列表 (SEC-004)
     * 如果是 admin 角色，返回所有 projectIds
     */
    public List<Long> getAccessibleProjectIds() {
        String role = getCurrentRole();
        if (role != null && role.equals("admin")) {
            return userProjectRelationMapper.findAllProjectIds();
        }
        Long userId = getCurrentUserId();
        if (userId == null) {
            return null;
        }
        return userProjectRelationMapper.findProjectIdsByUserId(userId);
    }

    /**
     * 判断当前用户是否为管理员
     */
    public boolean isAdmin() {
        String role = getCurrentRole();
        return role != null && role.equals("admin");
    }

    /**
     * 判断当前用户是否有权限访问指定项目 (SEC-004)
     * admin 角色拥有所有项目的权限
     */
    public boolean hasProjectPermission(Long projectId) {
        if (projectId == null) return true;
        String role = getCurrentRole();
        if (role != null && role.equals("admin")) {
            return true;
        }
        Long userId = getCurrentUserId();
        if (userId == null) {
            return false;
        }
        return userProjectRelationMapper.countByUserIdAndProjectId(userId, projectId) > 0;
    }
}
