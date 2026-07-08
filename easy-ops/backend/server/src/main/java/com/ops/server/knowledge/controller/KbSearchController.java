package com.ops.server.knowledge.controller;

import com.ops.common.response.Result;
import com.ops.server.knowledge.service.KbSearchService;
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

    /** 全文搜索 */
    @GetMapping
    public Result<?> fullTextSearch(@RequestParam String q,
                                    @RequestParam(defaultValue = "1") Integer page,
                                    @RequestParam(defaultValue = "20") Integer pageSize) {
        Map<String, Object> result = searchService.fullTextSearch(q, page, pageSize);
        return Result.success(result);
    }

    /** 高级搜索 */
    @GetMapping("/advanced")
    public Result<?> advancedSearch(@RequestParam(required = false) String q,
                                    @RequestParam(required = false) Long categoryId,
                                    @RequestParam(required = false) String tags,
                                    @RequestParam(defaultValue = "1") Integer page,
                                    @RequestParam(defaultValue = "20") Integer pageSize) {
        Map<String, Object> result = searchService.advancedSearch(q, categoryId, tags, page, pageSize);
        return Result.success(result);
    }

    /** 按标签搜索 */
    @GetMapping("/tag/{tagId}")
    public Result<?> searchByTag(@PathVariable Long tagId,
                                 @RequestParam(defaultValue = "1") Integer page,
                                 @RequestParam(defaultValue = "20") Integer pageSize) {
        Map<String, Object> result = searchService.searchByTag(tagId, page, pageSize);
        return Result.success(result);
    }
}
