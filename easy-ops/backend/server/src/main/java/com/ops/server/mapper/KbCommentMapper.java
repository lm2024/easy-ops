package com.ops.server.mapper;

import com.ops.common.model.KbCommentModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KbCommentMapper {
    List<KbCommentModel> findByDocumentId(@Param("documentId") Long documentId);
    int insert(KbCommentModel comment);
    int deleteByDocumentId(@Param("documentId") Long documentId);

    /** 按类型查询评论/批注 */
    List<KbCommentModel> findByDocumentIdAndType(@Param("documentId") Long documentId, @Param("type") String type);

    /** 点赞评论（likes +1） */
    int incrementLikes(@Param("id") Long id);

    /** 查询回复列表 */
    List<KbCommentModel> findByReplyToId(@Param("replyToId") Long replyToId);
}
