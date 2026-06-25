package com.ops.server.mapper;

import com.ops.common.model.ProjectConfigFileModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 项目配置文件 Mapper
 */
@Mapper
public interface ProjectConfigFileMapper {

    ProjectConfigFileModel findById(@Param("id") Long id);

    List<ProjectConfigFileModel> findByProjectId(@Param("projectId") Long projectId);

    int insert(ProjectConfigFileModel model);

    int update(ProjectConfigFileModel model);

    int deleteById(@Param("id") Long id);
}
