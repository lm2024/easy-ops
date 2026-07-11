package com.ops.server.mapper;

import com.ops.common.model.ProjectModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProjectMapper {
    ProjectModel findById(@Param("id") Long id);
    ProjectModel findByName(@Param("name") String name);
    List<ProjectModel> findByFilters(@Param("status") String status, @Param("nodeId") Long nodeId,
                                     @Param("keyword") String keyword,
                                     @Param("page") Integer page, @Param("pageSize") Integer pageSize);
    Long countByFilters(@Param("status") String status, @Param("nodeId") Long nodeId,
                        @Param("keyword") String keyword);
    int insert(ProjectModel project);
    int update(ProjectModel project);
    int deleteById(@Param("id") Long id);
    String getNodeIdsById(@Param("id") Long id);
}
