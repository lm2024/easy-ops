package com.ops.server.mapper;

import com.ops.common.model.KbDocumentVersionModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KbDocumentVersionMapper {
    int insert(KbDocumentVersionModel version);
    List<KbDocumentVersionModel> findByDocumentId(@Param("documentId") Long documentId);
    KbDocumentVersionModel findByDocAndVersion(@Param("documentId") Long documentId,
                                               @Param("versionNo") Integer versionNo);
    int deleteByDocumentId(@Param("documentId") Long documentId);
    int deleteOldestBeyond(@Param("documentId") Long documentId, @Param("keepCount") Integer keepCount);
}
