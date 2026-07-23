package com.ops.server.mapper;

import com.ops.common.model.AgentUpgradeRecordModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AgentUpgradeRecordMapper {

    AgentUpgradeRecordModel findById(@Param("id") Long id);

    List<AgentUpgradeRecordModel> findByBatchId(@Param("batchId") String batchId);

    List<AgentUpgradeRecordModel> findByNodeId(@Param("nodeId") Long nodeId);

    List<AgentUpgradeRecordModel> findAll(@Param("page") Integer page, @Param("pageSize") Integer pageSize);

    Long countAll();

    int insert(AgentUpgradeRecordModel record);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status,
                     @Param("errorMessage") String errorMessage, @Param("endTime") Long endTime);

    int updateStatusByBatchId(@Param("batchId") String batchId, @Param("status") Integer status);

    List<AgentUpgradeRecordModel> findLatestByNodeId(@Param("limit") Integer limit);
}
