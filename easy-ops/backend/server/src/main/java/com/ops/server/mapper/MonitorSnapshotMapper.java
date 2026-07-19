package com.ops.server.mapper;

import com.ops.common.model.MonitorSnapshotModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MonitorSnapshotMapper {
    int insert(MonitorSnapshotModel snapshot);
    MonitorSnapshotModel findLatest(@Param("projectId") Long projectId, @Param("nodeId") Long nodeId);
    List<MonitorSnapshotModel> findLatestByProject(@Param("projectId") Long projectId);
    List<MonitorSnapshotModel> findLatestByNodeIds(@Param("nodeIds") List<Long> nodeIds);
    List<MonitorSnapshotModel> findHistory(@Param("projectId") Long projectId,
                                           @Param("nodeId") Long nodeId,
                                           @Param("startTime") Long startTime,
                                           @Param("endTime") Long endTime,
                                           @Param("limit") Integer limit);
    List<MonitorSnapshotModel> findRecent(@Param("projectId") Long projectId,
                                          @Param("nodeId") Long nodeId,
                                          @Param("limit") Integer limit);
    int countDownInRange(@Param("projectId") Long projectId,
                         @Param("nodeId") Long nodeId,
                         @Param("startTime") Long startTime);
    int deleteBefore(@Param("cutoff") Long cutoff);
}
