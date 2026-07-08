package com.ops.server.mapper;

import com.ops.common.model.ProjectLogProfileModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 项目日志配置 Mapper
 */
@Mapper
public interface ProjectLogProfileMapper {

    ProjectLogProfileModel findByProjectId(@Param("projectId") Long projectId);

    int insert(ProjectLogProfileModel model);

    int update(ProjectLogProfileModel model);

    int deleteByProjectId(@Param("projectId") Long projectId);
}
