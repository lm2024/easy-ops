package com.ops.server.knowledge.service;

import com.ops.common.exception.BusinessException;
import com.ops.common.model.KbDocumentModel;
import com.ops.common.model.KbTemplateModel;
import com.ops.server.mapper.KbTemplateMapper;
import com.ops.server.util.SecurityContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 模板服务
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class KbTemplateService {

    @Autowired
    private KbTemplateMapper templateMapper;

    @Autowired
    private KnowledgeDocumentService documentService;

    @Autowired
    private SecurityContext securityContext;

    /**
     * 模板列表（可按分类筛选）
     */
    public List<KbTemplateModel> listTemplates(String category) {
        if (category != null && !category.isEmpty()) {
            return templateMapper.findByCategory(category);
        }
        return templateMapper.selectAll();
    }

    /**
     * 创建模板
     */
    public KbTemplateModel createTemplate(KbTemplateModel template) {
        Long userId = securityContext.getCurrentUserId();
        long now = System.currentTimeMillis();
        template.setUserId(userId);
        if (template.getIsSystem() == null) {
            template.setIsSystem(0);
        }
        template.setCreateTime(now);
        template.setUpdateTime(now);
        templateMapper.insert(template);
        return template;
    }

    /**
     * 更新模板
     */
    public KbTemplateModel updateTemplate(KbTemplateModel template) {
        template.setUpdateTime(System.currentTimeMillis());
        templateMapper.update(template);
        return templateMapper.selectById(template.getId());
    }

    /**
     * 删除模板（系统模板不可删）
     */
    public void deleteTemplate(Long id) {
        KbTemplateModel template = templateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException(1004, "模板不存在");
        }
        if (template.getIsSystem() != null && template.getIsSystem() == 1) {
            throw new BusinessException(403, "系统模板不可删除");
        }
        templateMapper.delete(id);
    }

    /**
     * 从模板创建文档
     */
    public KbDocumentModel createFromTemplate(Long templateId, Long categoryId) {
        KbTemplateModel template = templateMapper.selectById(templateId);
        if (template == null) {
            throw new BusinessException(1004, "模板不存在");
        }
        KbDocumentModel doc = new KbDocumentModel();
        doc.setCategoryId(categoryId);
        doc.setTitle(template.getName());
        doc.setContent(template.getContent());
        doc.setStatus(1);
        return documentService.create(doc);
    }
}
