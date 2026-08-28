package com.ops.server.mapper;

import com.ops.common.model.ArthasDiagnoseResultModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ArthasDiagnoseResultMapper {
    int insert(ArthasDiagnoseResultModel result);
    List<ArthasDiagnoseResultModel> findByRecordId(@Param("recordId") Long recordId);
    List<ArthasDiagnoseResultModel> findByRecordIdAndType(@Param("recordId") Long recordId,
                                                             @Param("commandType") String commandType);
    int deleteByRecordIds(@Param("recordIds") List<Long> recordIds);
}
