package com.ops.server.mapper;

import com.ops.common.model.KbDocumentModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KbDocumentMapper {
    KbDocumentModel findById(@Param("id") Long id);
    List<KbDocumentModel> findByCategory(@Param("categoryId") Long categoryId,
                                         @Param("page") Integer page,
                                         @Param("pageSize") Integer pageSize);
    Long countByCategory(@Param("categoryId") Long categoryId);
    List<KbDocumentModel> search(@Param("keyword") String keyword,
                                 @Param("page") Integer page,
                                 @Param("pageSize") Integer pageSize);
    Long countSearch(@Param("keyword") String keyword);
    int insert(KbDocumentModel document);
    int update(KbDocumentModel document);
    int updateCategory(@Param("id") Long id, @Param("categoryId") Long categoryId, @Param("updateTime") Long updateTime);
    int incrementViewCount(@Param("id") Long id);
    int deleteById(@Param("id") Long id);
}
