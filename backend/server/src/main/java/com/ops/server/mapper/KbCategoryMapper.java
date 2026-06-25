package com.ops.server.mapper;

import com.ops.common.model.KbCategoryModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KbCategoryMapper {
    List<KbCategoryModel> findAll(@Param("projectId") Long projectId);
    KbCategoryModel findById(@Param("id") Long id);
    int insert(KbCategoryModel category);
    int update(KbCategoryModel category);
    int deleteById(@Param("id") Long id);
    int countDocuments(@Param("categoryId") Long categoryId);
}
