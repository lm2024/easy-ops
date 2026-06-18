package com.ops.server.mapper;

import com.ops.common.model.DeployModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DeployRecordMapper {
    DeployModel findById(@Param("id") Long id);
    List<DeployModel> findByProjectId(@Param("projectId") Long projectId, @Param("page") Integer page,
                                      @Param("pageSize") Integer pageSize);
    Long countByProjectId(@Param("projectId") Long projectId);
    int insert(DeployModel deploy);
}
