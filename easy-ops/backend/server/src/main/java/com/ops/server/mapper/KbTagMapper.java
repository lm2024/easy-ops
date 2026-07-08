package com.ops.server.mapper;

import com.ops.common.model.KbTagModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KbTagMapper {
    List<KbTagModel> selectAll();
    KbTagModel selectById(@Param("id") Long id);
    int insert(KbTagModel tag);
    int update(KbTagModel tag);
    int delete(@Param("id") Long id);

    /** 查询文档关联的标签列表 */
    List<KbTagModel> findByDocumentId(@Param("documentId") Long documentId);

    /** 按名称查询标签（唯一校验） */
    KbTagModel findByName(@Param("name") String name);
}
