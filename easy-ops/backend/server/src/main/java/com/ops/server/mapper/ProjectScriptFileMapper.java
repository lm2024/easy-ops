package com.ops.server.mapper;

import com.ops.common.model.ProjectScriptFileModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 项目脚本文件 Mapper
 */
@Mapper
public interface ProjectScriptFileMapper {

    ProjectScriptFileModel findById(@Param("id") Long id);

    List<ProjectScriptFileModel> findByProjectId(@Param("projectId") Long projectId);

    ProjectScriptFileModel findByProjectAndPath(@Param("projectId") Long projectId, @Param("filePath") String filePath);

    int insert(ProjectScriptFileModel model);

    int update(ProjectScriptFileModel model);

    int deleteById(@Param("id") Long id, @Param("tenantId") Long tenantId);

    int deleteByProjectId(@Param("projectId") Long projectId, @Param("tenantId") Long tenantId);
}
