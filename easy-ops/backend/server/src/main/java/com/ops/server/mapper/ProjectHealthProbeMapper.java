package com.ops.server.mapper;

import com.ops.common.model.ProjectHealthProbeModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProjectHealthProbeMapper {
    ProjectHealthProbeModel findByProjectId(@Param("projectId") Long projectId);
    int insert(ProjectHealthProbeModel probe);
    int update(ProjectHealthProbeModel probe);
}
