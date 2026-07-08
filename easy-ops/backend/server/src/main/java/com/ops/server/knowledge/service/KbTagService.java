package com.ops.server.knowledge.service;

import com.ops.common.exception.BusinessException;
import com.ops.common.model.KbDocumentTagModel;
import com.ops.common.model.KbTagModel;
import com.ops.server.mapper.KbDocumentTagMapper;
import com.ops.server.mapper.KbTagMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 标签服务
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class KbTagService {

    @Autowired
    private KbTagMapper tagMapper;

    @Autowired
    private KbDocumentTagMapper documentTagMapper;

    /**
     * 获取所有标签
     */
    public List<KbTagModel> listTags() {
        return tagMapper.selectAll();
    }

    /**
     * 创建标签（校验名称唯一）
     */
    public KbTagModel createTag(KbTagModel tag) {
        KbTagModel existing = tagMapper.findByName(tag.getName());
        if (existing != null) {
            throw new BusinessException(400, "标签名称已存在: " + tag.getName());
        }
        tag.setCreateTime(System.currentTimeMillis());
        tagMapper.insert(tag);
        return tag;
    }

    /**
     * 更新标签
     */
    public KbTagModel updateTag(KbTagModel tag) {
        KbTagModel existing = tagMapper.findByName(tag.getName());
        if (existing != null && !existing.getId().equals(tag.getId())) {
            throw new BusinessException(400, "标签名称已存在: " + tag.getName());
        }
        tagMapper.update(tag);
        return tagMapper.selectById(tag.getId());
    }

    /**
     * 删除标签
     */
    public void deleteTag(Long id) {
        tagMapper.delete(id);
    }

    /**
     * 为文档添加标签
     */
    public void addTagToDocument(Long documentId, Long tagId) {
        KbDocumentTagModel existingRef = findDocTagRef(documentId, tagId);
        if (existingRef != null) {
            return; // 已存在关联，不重复添加
        }
        KbDocumentTagModel ref = new KbDocumentTagModel();
        ref.setDocumentId(documentId);
        ref.setTagId(tagId);
        ref.setCreateTime(System.currentTimeMillis());
        documentTagMapper.insert(ref);
    }

    /**
     * 从文档移除标签
     */
    public void removeTagFromDocument(Long documentId, Long tagId) {
        documentTagMapper.deleteByDocumentIdAndTagId(documentId, tagId);
    }

    /**
     * 获取文档的标签列表
     */
    public List<KbTagModel> getDocumentTags(Long documentId) {
        return tagMapper.findByDocumentId(documentId);
    }

    /**
     * 查找文档-标签关联记录
     */
    private KbDocumentTagModel findDocTagRef(Long documentId, Long tagId) {
        List<KbDocumentTagModel> refs = documentTagMapper.findByDocumentId(documentId);
        for (KbDocumentTagModel ref : refs) {
            if (ref.getTagId().equals(tagId)) {
                return ref;
            }
        }
        return null;
    }
}
