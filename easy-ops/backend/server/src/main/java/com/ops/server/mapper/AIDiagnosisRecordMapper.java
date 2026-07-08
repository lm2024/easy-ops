package com.ops.server.mapper;

import com.ops.common.model.AIDiagnosisRecordModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AIDiagnosisRecordMapper {
    int insert(AIDiagnosisRecordModel record);
    AIDiagnosisRecordModel findById(@Param("id") Long id);
    int updateKbLink(@Param("id") Long id,
                     @Param("kbDocumentId") Long kbDocumentId,
                     @Param("savedToKb") Integer savedToKb);
}
