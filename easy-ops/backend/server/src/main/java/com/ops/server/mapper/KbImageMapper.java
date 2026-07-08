package com.ops.server.mapper;

import com.ops.common.model.KbImageModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KbImageMapper {
    KbImageModel findById(@Param("id") Long id);
    List<KbImageModel> findByDocumentId(@Param("documentId") Long documentId);
    int insert(KbImageModel image);
    int deleteByDocumentId(@Param("documentId") Long documentId);
}
