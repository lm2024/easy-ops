package com.ops.server.knowledge.controller;

import com.ops.common.model.KbTagModel;
import com.ops.common.response.Result;
import com.ops.server.knowledge.service.KbTagService;
import com.ops.server.util.SecurityContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 标签 REST 接口
 */
@RestController
@RequestMapping("/kb/tags")
public class KbTagController {

    @Autowired
    private KbTagService tagService;

    @Autowired
    private SecurityContext securityContext;

    /** 标签列表 */
    @GetMapping
    public Result<?> listTags() {
        List<KbTagModel> tags = tagService.listTags();
        return Result.success(tags);
    }

    /** 创建标签 */
    @PostMapping
    public Result<?> createTag(@RequestBody KbTagModel tag) {
        try {
            return Result.success(tagService.createTag(tag));
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 更新标签 */
    @PutMapping("/{id}")
    public Result<?> updateTag(@PathVariable Long id, @RequestBody KbTagModel tag) {
        tag.setId(id);
        try {
            return Result.success(tagService.updateTag(tag));
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 删除标签 */
    @DeleteMapping("/{id}")
    public Result<?> deleteTag(@PathVariable Long id) {
        tagService.deleteTag(id);
        return Result.success();
    }

    /** 文档加标签 */
    @PostMapping("/documents/{documentId}")
    public Result<?> addDocumentTag(@PathVariable Long documentId, @RequestBody Map<String, Long> body) {
        Long tagId = body.get("tagId");
        if (tagId == null) {
            return Result.paramError("tagId 不能为空");
        }
        tagService.addTagToDocument(documentId, tagId);
        return Result.success();
    }

    /** 文档移除标签 */
    @DeleteMapping("/documents/{documentId}/{tagId}")
    public Result<?> removeDocumentTag(@PathVariable Long documentId, @PathVariable Long tagId) {
        tagService.removeTagFromDocument(documentId, tagId);
        return Result.success();
    }

    /** 获取文档标签 */
    @GetMapping("/documents/{documentId}")
    public Result<?> getDocumentTags(@PathVariable Long documentId) {
        List<KbTagModel> tags = tagService.getDocumentTags(documentId);
        return Result.success(tags);
    }
}
