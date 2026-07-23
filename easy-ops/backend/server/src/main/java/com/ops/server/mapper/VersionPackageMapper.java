package com.ops.server.mapper;

import com.ops.common.model.VersionModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface VersionPackageMapper {
    VersionModel findById(@Param("id") Long id);
    String getProjectNameByProjectId(@Param("projectId") Long projectId);
    List<VersionModel> findByProjectId(@Param("projectId") Long projectId, @Param("page") Integer page,
                                       @Param("pageSize") Integer pageSize);
    Long countByProjectId(@Param("projectId") Long projectId);
    int insert(VersionModel version);
    int deleteById(@Param("id") Long id);
    Long getLastId();
    List<VersionModel> findOldVersions(@Param("projectId") Long projectId, @Param("keepCount") int keepCount);
    VersionModel findByProjectIdAndVersion(@Param("projectId") Long projectId, @Param("version") String version);
}
