package com.ops.server.knowledge.service;

import com.ops.common.model.KbCommentModel;
import com.ops.server.mapper.KbCommentMapper;
import com.ops.server.util.SecurityContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 知识库评论服务
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class KnowledgeCommentService {

    @Autowired
    private KbCommentMapper commentMapper;
    @Autowired
    private SecurityContext securityContext;

    /**
     * 获取文档评论列表
     */
    public List<KbCommentModel> listByDocument(Long documentId) {
        return commentMapper.findByDocumentId(documentId);
    }

    /**
     * 新增评论
     */
    public KbCommentModel add(KbCommentModel comment) {
        long now = System.currentTimeMillis();
        comment.setUserId(securityContext.getCurrentUserId());
        if (comment.getParentId() == null) {
            comment.setParentId(0L);
        }
        comment.setCreateTime(now);
        comment.setUpdateTime(now);
        commentMapper.insert(comment);
        return comment;
    }
}
