package com.ops.server.mapper;

import com.ops.common.model.KbDocumentLockModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface KbDocumentLockMapper {
    KbDocumentLockModel findByDocumentId(@Param("documentId") Long documentId);
    int upsert(KbDocumentLockModel lock);
    int deleteByDocumentId(@Param("documentId") Long documentId);
    int deleteExpired(@Param("now") Long now);
}
