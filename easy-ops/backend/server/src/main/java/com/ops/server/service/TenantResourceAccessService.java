package com.ops.server.service;

import com.ops.common.model.NodeModel;
import com.ops.common.model.ProjectModel;
import com.ops.server.mapper.NodeMapper;
import com.ops.server.mapper.ProjectMapper;
import com.ops.server.util.SecurityContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 统一处理资源归属校验，避免各 Controller 只校验列表而漏掉写操作。 */
@Service
public class TenantResourceAccessService {
    @Autowired
    private SecurityContext securityContext;
    @Autowired
    private ProjectMapper projectMapper;
    @Autowired
    private NodeMapper nodeMapper;

    public boolean canAccessProject(ProjectModel project) {
        if (project == null) return false;
        Long tenantId = securityContext.getCurrentTenantId();
        if (!tenantScopeEnabled(tenantId) || securityContext.isPlatformAdmin() || project.getTenantId() == null) return true;
        return tenantId.equals(project.getTenantId()) && securityContext.hasProjectPermission(project.getId());
    }

    public boolean canAccessNode(NodeModel node) {
        if (node == null) return false;
        Long tenantId = securityContext.getCurrentTenantId();
        return !tenantScopeEnabled(tenantId) || securityContext.isPlatformAdmin()
                || node.getTenantId() == null || tenantId.equals(node.getTenantId());
    }

    public boolean canAccessProjectNode(ProjectModel project, NodeModel node, Long nodeId) {
        if (!tenantScopeEnabled(securityContext.getCurrentTenantId())) return true;
        if (!canAccessProject(project) || !canAccessNode(node)) return false;
        if (nodeId == null || project.getNodeIds() == null) return false;
        String wanted = String.valueOf(nodeId);
        for (String item : project.getNodeIds().split(",")) {
            if (wanted.equals(item.trim())) return true;
        }
        return false;
    }

    public ProjectModel requireProject(Long projectId) {
        ProjectModel project = projectMapper.findById(projectId);
        if (!canAccessProject(project)) throw new IllegalArgumentException("无权访问该项目");
        return project;
    }

    public NodeModel requireNode(Long nodeId) {
        NodeModel node = nodeMapper.findById(nodeId);
        if (!canAccessNode(node)) throw new IllegalArgumentException("无权访问该节点");
        return node;
    }

    private boolean tenantScopeEnabled(Long tenantId) {
        return tenantId != null && tenantId.longValue() > 0L;
    }
}
