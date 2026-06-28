package com.ops.server.mapper;

import com.ops.common.model.KbTemplateModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KbTemplateMapper {
    List<KbTemplateModel> selectAll();
    KbTemplateModel selectById(@Param("id") Long id);
    int insert(KbTemplateModel template);
    int update(KbTemplateModel template);
    int delete(@Param("id") Long id);

    /** 按分类查询模板 */
    List<KbTemplateModel> findByCategory(@Param("category") String category);

    /** 查询系统预置模板 */
    List<KbTemplateModel> findSystemTemplates();
}
