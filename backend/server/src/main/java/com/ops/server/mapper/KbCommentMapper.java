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
}
