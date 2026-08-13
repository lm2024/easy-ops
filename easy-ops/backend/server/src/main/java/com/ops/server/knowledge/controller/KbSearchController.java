package com.ops.server.knowledge.controller;

import com.ops.common.model.KbCategoryModel;
import com.ops.common.response.Result;
import com.ops.server.knowledge.service.KbSearchService;
import com.ops.server.mapper.KbCategoryMapper;
import com.ops.server.service.TenantResourceAccessService;
import com.ops.server.util.SecurityContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 搜索 REST 接口
 */
@RestController
@RequestMapping("/kb/search")
public class KbSearchController {

    @Autowired
    private KbSearchService searchService;

    @Autowired
    private TenantResourceAccessService tenantResourceAccessService;

    @Autowired
    private KbCategoryMapper categoryMapper;

    @Autowired
    private SecurityContext securityContext;

    /** 全文搜索 */
    @GetMapping
    public Result<?> fullTextSearch(@RequestParam String q,
                                    @RequestParam(defaultValue = "1") Integer page,
                                    @RequestParam(defaultValue = "20") Integer pageSize) {
        Map<String, Object> result = searchService.fullTextSearch(q, securityContext.getCurrentTenantId(), page, pageSize);
        return Result.success(result);
    }

    /** 高级搜索 */
    @GetMapping("/advanced")
    public Result<?> advancedSearch(@RequestParam(required = false) String q,
                                    @RequestParam(required = false) Long categoryId,
                                    @RequestParam(required = false) String tags,
                                    @RequestParam(defaultValue = "1") Integer page,
                                    @RequestParam(defaultValue = "20") Integer pageSize) {
        if (categoryId != null) {
            requireCategoryAccess(categoryMapper.findById(categoryId));
        }
        Map<String, Object> result = searchService.advancedSearch(q, categoryId, tags, securityContext.getCurrentTenantId(), page, pageSize);
        return Result.success(result);
    }

    /** 按标签搜索 */
    @GetMapping("/tag/{tagId}")
    public Result<?> searchByTag(@PathVariable Long tagId,
                                 @RequestParam(defaultValue = "1") Integer page,
                                 @RequestParam(defaultValue = "20") Integer pageSize) {
        Map<String, Object> result = searchService.searchByTag(tagId, securityContext.getCurrentTenantId(), page, pageSize);
        return Result.success(result);
    }

    /** 校验分类归属（分类共享，文档按租户过滤；此处校验分类所属项目/租户） */
    private void requireCategoryAccess(KbCategoryModel category) {
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
    }
}
