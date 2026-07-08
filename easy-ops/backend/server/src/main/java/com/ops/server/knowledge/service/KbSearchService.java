package com.ops.server.knowledge.service;

import com.ops.common.model.KbDocumentModel;
import com.ops.common.model.KbDocumentTagModel;
import com.ops.server.mapper.KbDocumentMapper;
import com.ops.server.mapper.KbDocumentTagMapper;
import com.ops.server.mapper.KbTagMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 搜索服务
 * 暂时使用 LIKE 方式，与现有 KbDocumentMapper.search 一致
 * 预留 FT 索引升级接口
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class KbSearchService {

    @Autowired
    private KbDocumentMapper documentMapper;

    @Autowired
    private KbDocumentTagMapper documentTagMapper;

    @Autowired
    private KbTagMapper tagMapper;

    /**
     * 全文搜索（LIKE 方式）
     */
    public Map<String, Object> fullTextSearch(String query, Integer page, Integer pageSize) {
        if (page == null || page <= 0) page = 1;
        if (pageSize == null || pageSize <= 0) pageSize = 20;

        List<KbDocumentModel> list = documentMapper.search(query, page, pageSize);
        Long total = documentMapper.countSearch(query);

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        return result;
    }

    /**
     * 高级搜索（关键词+分类+标签）
     */
    public Map<String, Object> advancedSearch(String query, Long categoryId, String tags, Integer page, Integer pageSize) {
        if (page == null || page <= 0) page = 1;
        if (pageSize == null || pageSize <= 0) pageSize = 20;

        // 1. 先按关键词搜索
        List<KbDocumentModel> keywordResults = documentMapper.search(query, page, pageSize);

        // 2. 如果有分类筛选，过滤不属于该分类的文档
        List<KbDocumentModel> filtered = new ArrayList<KbDocumentModel>();
        if (categoryId != null) {
            for (KbDocumentModel doc : keywordResults) {
                if (doc.getCategoryId() != null && doc.getCategoryId().equals(categoryId)) {
                    filtered.add(doc);
                }
            }
        } else {
            filtered.addAll(keywordResults);
        }

        // 3. 如果有标签筛选，按标签进一步过滤
        if (tags != null && !tags.isEmpty()) {
            String[] tagNames = tags.split(",");
            List<KbDocumentModel> tagFiltered = new ArrayList<KbDocumentModel>();
            for (KbDocumentModel doc : filtered) {
                List<KbDocumentTagModel> docTags = documentTagMapper.findByDocumentId(doc.getId());
                boolean matchAll = true;
                for (String tagName : tagNames) {
                    boolean found = false;
                    for (KbDocumentTagModel dt : docTags) {
                        com.ops.common.model.KbTagModel tag = tagMapper.selectById(dt.getTagId());
                        if (tag != null && tag.getName().equals(tagName.trim())) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        matchAll = false;
                        break;
                    }
                }
                if (matchAll) {
                    tagFiltered.add(doc);
                }
            }
            filtered = tagFiltered;
        }

        // 4. 分页截取
        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, filtered.size());
        List<KbDocumentModel> pagedList = filtered.subList(start, end);

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("list", pagedList);
        result.put("total", filtered.size());
        result.put("page", page);
        result.put("pageSize", pageSize);
        return result;
    }

    /**
     * 按标签搜索文档
     */
    public Map<String, Object> searchByTag(Long tagId, Integer page, Integer pageSize) {
        if (page == null || page <= 0) page = 1;
        if (pageSize == null || pageSize <= 0) pageSize = 20;

        // 查找所有关联该标签的文档 ID
        List<KbDocumentTagModel> docTagRefs = documentTagMapper.findByDocumentId(null);
        // 此处需要根据 tagId 查找关联，但现有 mapper 不支持按 tagId 查
        // 改用全量标签关联表筛选
        List<KbDocumentTagModel> allDocTags = documentTagMapper.selectAll();
        List<Long> docIds = new ArrayList<Long>();
        for (KbDocumentTagModel dt : allDocTags) {
            if (dt.getTagId().equals(tagId)) {
                docIds.add(dt.getDocumentId());
            }
        }

        // 查找文档详情
        List<KbDocumentModel> documents = new ArrayList<KbDocumentModel>();
        for (Long docId : docIds) {
            KbDocumentModel doc = documentMapper.findById(docId);
            if (doc != null) {
                documents.add(doc);
            }
        }

        // 分页截取
        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, documents.size());
        List<KbDocumentModel> pagedList;
        if (start >= documents.size()) {
            pagedList = new ArrayList<KbDocumentModel>();
        } else {
            pagedList = documents.subList(start, end);
        }

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("list", pagedList);
        result.put("total", documents.size());
        result.put("page", page);
        result.put("pageSize", pageSize);
        return result;
    }
}
