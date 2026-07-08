package com.ops.server.knowledge.service;

import com.ops.common.model.KbCategoryModel;
import com.ops.common.model.KbDocumentModel;
import com.ops.common.model.KbDocumentPermissionModel;
import com.ops.server.mapper.KbCategoryMapper;
import com.ops.server.mapper.KbDocumentMapper;
import com.ops.server.mapper.KbDocumentPermissionMapper;
import com.ops.server.util.SecurityContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 权限服务
 * 权限继承链：文档级 > 最近分类级 > 根分类级
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class KbPermissionService {

    @Autowired
    private KbDocumentPermissionMapper permissionMapper;

    @Autowired
    private KbDocumentMapper documentMapper;

    @Autowired
    private KbCategoryMapper categoryMapper;

    @Autowired
    private SecurityContext securityContext;

    /**
     * 设置权限（如果已存在则更新）
     */
    public KbDocumentPermissionModel setPermission(KbDocumentPermissionModel permission) {
        KbDocumentPermissionModel existing = permissionMapper.findByTargetAndUser(
                permission.getTargetId(), permission.getTargetType(), permission.getUserId());
        if (existing != null) {
            existing.setPermissionLevel(permission.getPermissionLevel());
            permissionMapper.update(existing);
            return existing;
        }
        permission.setCreateTime(System.currentTimeMillis());
        permissionMapper.insert(permission);
        return permission;
    }

    /**
     * 获取有效权限（含继承链解析）
     * 权限优先级：文档级 > 最近分类级 > 根分类级
     */
    public String getEffectivePermission(Long targetId, String targetType, Long userId) {
        // 1. 先查目标本身的权限
        KbDocumentPermissionModel directPerm = permissionMapper.findByTargetAndUser(targetId, targetType, userId);
        if (directPerm != null) {
            return directPerm.getPermissionLevel();
        }

        // 2. 如果是文档类型，沿分类链查找继承权限
        if ("DOCUMENT".equals(targetType)) {
            KbDocumentModel doc = documentMapper.findById(targetId);
            if (doc != null && doc.getCategoryId() != null) {
                List<Long> categoryChain = buildCategoryChain(doc.getCategoryId());
                // 从最近分类到根分类，按优先级查找
                for (Long categoryId : categoryChain) {
                    KbDocumentPermissionModel catPerm = permissionMapper.findByTargetAndUser(
                            categoryId, "CATEGORY", userId);
                    if (catPerm != null) {
                        return catPerm.getPermissionLevel();
                    }
                }
            }
        }

        // 3. 无权限记录时默认返回 null（表示未设置权限）
        return null;
    }

    /**
     * 检查访问权限
     * @param documentId 文档 ID
     * @param userId 用户 ID
     * @param requiredLevel 要求的权限等级（VIEW / EDIT / MANAGE）
     * @return true=有权限，false=无权限
     */
    public boolean checkAccess(Long documentId, Long userId, String requiredLevel) {
        String effectivePermission = getEffectivePermission(documentId, "DOCUMENT", userId);
        if (effectivePermission == null) {
            // 无权限记录时，admin 角色默认有权限，其他用户默认无权限
            return false;
        }
        return comparePermissionLevel(effectivePermission, requiredLevel) >= 0;
    }

    /**
     * 删除权限
     */
    public void removePermission(Long id) {
        permissionMapper.delete(id);
    }

    /**
     * 获取目标的权限列表
     */
    public List<KbDocumentPermissionModel> getTargetPermissions(Long targetId, String targetType) {
        return permissionMapper.findByTarget(targetId, targetType);
    }

    /**
     * 构建分类继承链（从叶子到根）
     * 返回列表中第一个元素是文档所属分类，最后一个元素是根分类
     */
    private List<Long> buildCategoryChain(Long categoryId) {
        List<Long> chain = new ArrayList<>();
        Long currentId = categoryId;
        // 最大深度限制防止循环
        int maxDepth = 20;
        while (currentId != null && currentId != 0L && maxDepth > 0) {
            chain.add(currentId);
            KbCategoryModel cat = categoryMapper.findById(currentId);
            if (cat == null) {
                break;
            }
            currentId = cat.getParentId();
            maxDepth--;
        }
        return chain;
    }

    /**
     * 比较权限等级
     * VIEW=1, EDIT=2, MANAGE=3
     * 返回值：>=0 表示 actual >= required
     */
    private int comparePermissionLevel(String actual, String required) {
        return permissionLevelValue(actual) - permissionLevelValue(required);
    }

    private int permissionLevelValue(String level) {
        if (level == null) return 0;
        switch (level) {
            case "VIEW": return 1;
            case "EDIT": return 2;
            case "MANAGE": return 3;
            default: return 0;
        }
    }
}
