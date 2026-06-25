package com.ops.server.knowledge.service;

import com.ops.common.exception.BusinessException;
import com.ops.common.model.KbCategoryModel;
import com.ops.server.mapper.KbCategoryMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库分类服务
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class KnowledgeCategoryService {

    @Autowired
    private KbCategoryMapper categoryMapper;

    /**
     * 获取分类树
     */
    public List<Map<String, Object>> getCategoryTree(Long projectId) {
        List<KbCategoryModel> all = categoryMapper.findAll(projectId);
        return buildTree(all, 0L);
    }

    /**
     * 创建分类
     */
    public KbCategoryModel create(KbCategoryModel category) {
        long now = System.currentTimeMillis();
        if (category.getParentId() == null) {
            category.setParentId(0L);
        }
        if (category.getSortOrder() == null) {
            category.setSortOrder(0);
        }
        category.setCreateTime(now);
        category.setUpdateTime(now);
        categoryMapper.insert(category);
        return category;
    }

    /**
     * 更新分类
     */
    public KbCategoryModel update(KbCategoryModel category) {
        category.setUpdateTime(System.currentTimeMillis());
        categoryMapper.update(category);
        return categoryMapper.findById(category.getId());
    }

    /**
     * 删除空分类
     */
    public void delete(Long id) {
        if (categoryMapper.countDocuments(id) > 0) {
            throw new BusinessException(1011, "分类下仍有文档，请先移动或删除文档");
        }
        categoryMapper.deleteById(id);
    }

    private List<Map<String, Object>> buildTree(List<KbCategoryModel> all, Long parentId) {
        List<Map<String, Object>> tree = new ArrayList<Map<String, Object>>();
        if (all == null) {
            return tree;
        }
        for (KbCategoryModel cat : all) {
            Long pid = cat.getParentId() != null ? cat.getParentId() : 0L;
            if (pid.equals(parentId)) {
                Map<String, Object> node = new HashMap<String, Object>();
                node.put("id", cat.getId());
                node.put("name", cat.getName());
                node.put("icon", cat.getIcon());
                node.put("sortOrder", cat.getSortOrder());
                node.put("projectId", cat.getProjectId());
                node.put("children", buildTree(all, cat.getId()));
                tree.add(node);
            }
        }
        return tree;
    }
}
