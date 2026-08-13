package com.ops.server.service;

import com.ops.common.model.NodeModel;
import com.ops.common.model.ProjectModel;
import com.ops.common.model.NginxAccessSourceModel;
import com.ops.common.model.KbDocumentModel;
import com.ops.server.mapper.NodeMapper;
import com.ops.server.mapper.ProjectMapper;
import com.ops.server.mapper.NginxAccessSourceMapper;
import com.ops.server.mapper.KbDocumentMapper;
import com.ops.server.util.SecurityContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 统一处理资源归属校验，避免各 Controller 只校验列表而漏掉写操作。
 *
 * 兜底原则：管理员账号（sys_user.role=admin）永远拥有全部资源的访问权限，
 * 不受租户状态、成员关系、tenant_id 数据完整性影响。
 * 任何租户隔离校验的第一道关卡都是 isSuperAdmin() 放行。
 */
@Service
public class TenantResourceAccessService {
    @Autowired
    private SecurityContext securityContext;
    @Autowired
    private ProjectMapper projectMapper;
    @Autowired
    private NodeMapper nodeMapper;
    @Autowired
    private NginxAccessSourceMapper nginxAccessSourceMapper;
    @Autowired
    private KbDocumentMapper kbDocumentMapper;

    public boolean canAccessProject(ProjectModel project) {
        if (project == null) return false;
        // 管理员兜底：无条件放行
        if (securityContext.isSuperAdmin()) return true;
        Long tenantId = securityContext.getCurrentTenantId();
        if (!tenantScopeEnabled(tenantId) || project.getTenantId() == null) return true;
        return tenantId.equals(project.getTenantId()) && securityContext.hasProjectPermission(project.getId());
    }

    public boolean canAccessNode(NodeModel node) {
        if (node == null) return false;
        // 管理员兜底：无条件放行
        if (securityContext.isSuperAdmin()) return true;
        Long tenantId = securityContext.getCurrentTenantId();
        return !tenantScopeEnabled(tenantId)
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
        // 隔离未启用（平台视图/测试，tenantId=null）时不强制校验，保持兼容
        if (tenantScopeEnabled(securityContext.getCurrentTenantId()) && !canAccessProject(project)) {
            throw new IllegalArgumentException("无权访问该项目");
        }
        return project;
    }

    public NodeModel requireNode(Long nodeId) {
        NodeModel node = nodeMapper.findById(nodeId);
        if (tenantScopeEnabled(securityContext.getCurrentTenantId()) && !canAccessNode(node)) {
            throw new IllegalArgumentException("无权访问该节点");
        }
        return node;
    }

    public NginxAccessSourceModel requireSource(Long sourceId) {
        NginxAccessSourceModel source = sourceId == null ? null : nginxAccessSourceMapper.findById(sourceId);
        if (tenantScopeEnabled(securityContext.getCurrentTenantId()) && !canAccessSource(source)) {
            throw new IllegalArgumentException("无权访问该日志源");
        }
        return source;
    }

    public KbDocumentModel requireDocument(Long documentId) {
        KbDocumentModel doc = documentId == null ? null : kbDocumentMapper.findById(documentId);
        if (tenantScopeEnabled(securityContext.getCurrentTenantId()) && !canAccessDocument(doc)) {
            throw new IllegalArgumentException("无权访问该文档");
        }
        return doc;
    }

    public boolean canAccessSource(NginxAccessSourceModel source) {
        if (source == null) return false;
        if (securityContext.isSuperAdmin()) return true;
        Long tenantId = securityContext.getCurrentTenantId();
        if (!tenantScopeEnabled(tenantId)) return true;
        // 日志源物化 tenant_id；旧数据 tenant_id=0 时回退到 node 推导
        if (source.getTenantId() != null && source.getTenantId() > 0) {
            return tenantId.equals(source.getTenantId());
        }
        NodeModel node = nodeMapper.findById(source.getNodeId());
        return node != null && (node.getTenantId() == null || tenantId.equals(node.getTenantId()));
    }

    public boolean canAccessDocument(KbDocumentModel doc) {
        if (doc == null) return false;
        if (securityContext.isSuperAdmin()) return true;
        Long tenantId = securityContext.getCurrentTenantId();
        if (!tenantScopeEnabled(tenantId)) return true;
        if (doc.getTenantId() != null && doc.getTenantId() > 0) {
            return tenantId.equals(doc.getTenantId());
        }
        // 旧数据回退：文档 project_id → 项目租户
        if (doc.getProjectId() != null) {
            ProjectModel project = projectMapper.findById(doc.getProjectId());
            return project != null && (project.getTenantId() == null || tenantId.equals(project.getTenantId()));
        }
        return true;
    }

    private boolean tenantScopeEnabled(Long tenantId) {
        // 平台视图（super_admin）tenantId=null → 不过滤；非管理员始终有 tenant（无成员时哨兵 -1）
        return tenantId != null;
    }
}
