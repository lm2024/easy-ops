package com.ops.server.mapper;

import com.ops.common.model.GlobalScriptFileModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 全局脚本文件 Mapper
 */
@Mapper
public interface GlobalScriptFileMapper {

    GlobalScriptFileModel findById(@Param("id") Long id);

    List<GlobalScriptFileModel> findAll();

    GlobalScriptFileModel findByFilePath(@Param("filePath") String filePath);

    int insert(GlobalScriptFileModel model);

    int update(GlobalScriptFileModel model);

    int deleteById(@Param("id") Long id);
}
