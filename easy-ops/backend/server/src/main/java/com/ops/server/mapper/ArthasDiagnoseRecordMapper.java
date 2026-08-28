package com.ops.server.mapper;

import com.ops.common.model.ArthasDiagnoseRecordModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ArthasDiagnoseRecordMapper {
    int insert(ArthasDiagnoseRecordModel record);
    int update(ArthasDiagnoseRecordModel record);
    ArthasDiagnoseRecordModel findById(@Param("id") Long id);
    ArthasDiagnoseRecordModel findBySessionId(@Param("sessionId") String sessionId);
    List<ArthasDiagnoseRecordModel> findByProjectId(@Param("projectId") Long projectId,
                                                       @Param("offset") int offset,
                                                       @Param("limit") int limit);
    int countByProjectId(@Param("projectId") Long projectId);
    List<Long> findExpiredIds(@Param("cutoff") Long cutoff);
    int deleteByIds(@Param("ids") List<Long> ids);
}
