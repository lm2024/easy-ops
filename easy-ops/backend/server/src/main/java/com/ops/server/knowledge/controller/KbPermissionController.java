package com.ops.server.knowledge.controller;

import com.ops.common.model.KbDocumentPermissionModel;
import com.ops.common.response.Result;
import com.ops.server.knowledge.service.KbPermissionService;
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

    /** 设置权限 */
    @PostMapping
    public Result<?> setPermission(@RequestBody KbDocumentPermissionModel permission) {
        return Result.success(permissionService.setPermission(permission));
    }

    /** 获取权限列表 */
    @GetMapping
    public Result<?> getPermissions(@RequestParam Long targetId, @RequestParam String targetType) {
        List<KbDocumentPermissionModel> permissions = permissionService.getTargetPermissions(targetId, targetType);
        return Result.success(permissions);
    }

    /** 删除权限 */
    @DeleteMapping("/{id}")
    public Result<?> removePermission(@PathVariable Long id) {
        permissionService.removePermission(id);
        return Result.success();
    }

    /** 检查权限 */
    @GetMapping("/check")
    public Result<?> checkAccess(@RequestParam Long documentId, @RequestParam String requiredLevel) {
        Long userId = securityContext.getCurrentUserId();
        boolean hasAccess = permissionService.checkAccess(documentId, userId, requiredLevel);
        return Result.success(hasAccess);
    }
}
