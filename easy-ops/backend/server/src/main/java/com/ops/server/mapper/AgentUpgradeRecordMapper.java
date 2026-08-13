package com.ops.server.mapper;

import com.ops.common.model.AgentUpgradeRecordModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AgentUpgradeRecordMapper {

    AgentUpgradeRecordModel findById(@Param("id") Long id);

    List<AgentUpgradeRecordModel> findByBatchId(@Param("batchId") String batchId, @Param("tenantId") Long tenantId);

    List<AgentUpgradeRecordModel> findByNodeId(@Param("nodeId") Long nodeId, @Param("tenantId") Long tenantId);

    List<AgentUpgradeRecordModel> findAll(@Param("page") Integer page, @Param("pageSize") Integer pageSize, @Param("tenantId") Long tenantId);

    Long countAll(@Param("tenantId") Long tenantId);

    int insert(AgentUpgradeRecordModel record);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status,
                     @Param("errorMessage") String errorMessage, @Param("endTime") Long endTime,
                     @Param("tenantId") Long tenantId);

    int updateStatusByBatchId(@Param("batchId") String batchId, @Param("status") Integer status,
                              @Param("tenantId") Long tenantId);

    List<AgentUpgradeRecordModel> findLatestByNodeId(@Param("limit") Integer limit);

    int deleteBefore(@Param("cutoff") Long cutoff);
}
