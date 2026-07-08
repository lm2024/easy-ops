package com.ops.server.mapper;

import com.ops.common.model.KbDocumentTagModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KbDocumentTagMapper {
    List<KbDocumentTagModel> selectAll();
    KbDocumentTagModel selectById(@Param("id") Long id);
    int insert(KbDocumentTagModel documentTag);
    int update(KbDocumentTagModel documentTag);
    int delete(@Param("id") Long id);

    /** 查询文档的标签关联列表 */
    List<KbDocumentTagModel> findByDocumentId(@Param("documentId") Long documentId);

    /** 删除特定文档-标签关联 */
    int deleteByDocumentIdAndTagId(@Param("documentId") Long documentId, @Param("tagId") Long tagId);

    /** 删除文档所有标签关联 */
    int deleteByDocumentId(@Param("documentId") Long documentId);
}
