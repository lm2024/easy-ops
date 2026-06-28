package com.ops.server.knowledge.controller;

import com.ops.common.model.KbDocumentModel;
import com.ops.common.model.KbTemplateModel;
import com.ops.common.response.Result;
import com.ops.server.knowledge.service.KbTemplateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 模板 REST 接口
 */
@RestController
@RequestMapping("/kb/templates")
public class KbTemplateController {

    @Autowired
    private KbTemplateService templateService;

    /** 模板列表（category 可选） */
    @GetMapping
    public Result<?> listTemplates(@RequestParam(required = false) String category) {
        List<KbTemplateModel> templates = templateService.listTemplates(category);
        return Result.success(templates);
    }

    /** 创建模板 */
    @PostMapping
    public Result<?> createTemplate(@RequestBody KbTemplateModel template) {
        return Result.success(templateService.createTemplate(template));
    }

    /** 更新模板 */
    @PutMapping("/{id}")
    public Result<?> updateTemplate(@PathVariable Long id, @RequestBody KbTemplateModel template) {
        template.setId(id);
        return Result.success(templateService.updateTemplate(template));
    }

    /** 删除模板 */
    @DeleteMapping("/{id}")
    public Result<?> deleteTemplate(@PathVariable Long id) {
        try {
            templateService.deleteTemplate(id);
            return Result.success();
        } catch (Exception e) {
            return Result.error(e.getMessage().contains("系统") ? 403 : 1004, e.getMessage());
        }
    }

    /** 从模板创建文档 */
    @PostMapping("/create-document")
    public Result<?> createFromTemplate(@RequestBody Map<String, Long> body) {
        Long templateId = body.get("templateId");
        Long categoryId = body.get("categoryId");
        if (templateId == null || categoryId == null) {
            return Result.paramError("templateId 和 categoryId 不能为空");
        }
        try {
            KbDocumentModel doc = templateService.createFromTemplate(templateId, categoryId);
            return Result.success(doc);
        } catch (Exception e) {
            return Result.error(1004, e.getMessage());
        }
    }
}
