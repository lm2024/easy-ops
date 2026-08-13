package com.ops.server.knowledge.controller;

import com.ops.common.model.KbCategoryModel;
import com.ops.common.model.KbDocumentPermissionModel;
import com.ops.common.response.Result;
import com.ops.server.knowledge.service.KbPermissionService;
import com.ops.server.mapper.KbCategoryMapper;
import com.ops.server.mapper.KbDocumentPermissionMapper;
import com.ops.server.service.TenantResourceAccessService;
import com.ops.server.util.SecurityContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 权限 REST 接口
 */
@RestController
@RequestMapping("/kb/permissions")
public class KbPermissionController {

    @Autowired
    private KbPermissionService permissionService;

    @Autowired
    private SecurityContext securityContext;

    @Autowired
    private TenantResourceAccessService tenantResourceAccessService;

    @Autowired
    private KbCategoryMapper categoryMapper;

    @Autowired
    private KbDocumentPermissionMapper permissionMapper;

    /** 设置权限 */
    @PostMapping
    public Result<?> setPermission(@RequestBody KbDocumentPermissionModel permission) {
        requireTargetAccess(permission.getTargetId(), permission.getTargetType());
        return Result.success(permissionService.setPermission(permission));
    }

    /** 获取权限列表 */
    @GetMapping
    public Result<?> getPermissions(@RequestParam Long targetId, @RequestParam String targetType) {
        requireTargetAccess(targetId, targetType);
        List<KbDocumentPermissionModel> permissions = permissionService.getTargetPermissions(targetId, targetType);
        return Result.success(permissions);
    }

    /** 删除权限 */
    @DeleteMapping("/{id}")
    public Result<?> removePermission(@PathVariable Long id) {
        KbDocumentPermissionModel permission = permissionMapper.selectById(id);
        if (permission != null) {
            requireTargetAccess(permission.getTargetId(), permission.getTargetType());
        }
        permissionService.removePermission(id);
        return Result.success();
    }

    /** 检查权限 */
    @GetMapping("/check")
    public Result<?> checkAccess(@RequestParam Long documentId, @RequestParam String requiredLevel) {
        tenantResourceAccessService.requireDocument(documentId);
        Long userId = securityContext.getCurrentUserId();
        boolean hasAccess = permissionService.checkAccess(documentId, userId, requiredLevel);
        return Result.success(hasAccess);
    }

    /** 校验权限目标归属：DOCUMENT → requireDocument；CATEGORY → 校验分类（project/租户） */
    private void requireTargetAccess(Long targetId, String targetType) {
        if (targetType == null || "DOCUMENT".equalsIgnoreCase(targetType)) {
            tenantResourceAccessService.requireDocument(targetId);
            return;
        }
        if ("CATEGORY".equalsIgnoreCase(targetType)) {
            KbCategoryModel category = categoryMapper.findById(targetId);
            if (category == null) {
                throw new IllegalArgumentException("分类不存在");
            }
            Long tenantId = securityContext.getCurrentTenantId();
            if (tenantId != null && !securityContext.isPlatformAdmin()) {
                if (category.getProjectId() != null) {
                    tenantResourceAccessService.requireProject(category.getProjectId());
                } else if (category.getTenantId() != null && category.getTenantId() > 0
                        && !tenantId.equals(category.getTenantId())) {
                    throw new IllegalArgumentException("无权访问该分类");
                }
            }
            return;
        }
        throw new IllegalArgumentException("未知的权限目标类型: " + targetType);
    }
}
