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
        if (comment.getLikes() == null) {
            comment.setLikes(0);
        }
        if (comment.getType() == null) {
            comment.setType("COMMENT");
        }
        comment.setCreateTime(now);
        comment.setUpdateTime(now);
        commentMapper.insert(comment);
        return comment;
    }

    /**
     * 回复评论（设置 replyToId）
     */
    public KbCommentModel addReply(KbCommentModel reply) {
        long now = System.currentTimeMillis();
        reply.setUserId(securityContext.getCurrentUserId());
        if (reply.getParentId() == null) {
            reply.setParentId(0L);
        }
        if (reply.getLikes() == null) {
            reply.setLikes(0);
        }
        if (reply.getType() == null) {
            reply.setType("COMMENT");
        }
        reply.setCreateTime(now);
        reply.setUpdateTime(now);
        commentMapper.insert(reply);
        return reply;
    }

    /**
     * 点赞评论（likes +1）
     */
    public void likeComment(Long id) {
        commentMapper.incrementLikes(id);
    }

    /**
     * 按类型查询评论/批注
     * @param documentId 文档 ID
     * @param type 类型（COMMENT / ANNOTATION）
     */
    public List<KbCommentModel> listByDocumentAndType(Long documentId, String type) {
        return commentMapper.findByDocumentIdAndType(documentId, type);
    }

    /**
     * 查询回复列表
     */
    public List<KbCommentModel> listReplies(Long replyToId) {
        return commentMapper.findByReplyToId(replyToId);
    }
}
