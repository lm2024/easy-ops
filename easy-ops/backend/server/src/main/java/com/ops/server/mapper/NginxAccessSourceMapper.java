package com.ops.server.mapper;

import com.ops.common.model.NginxAccessSourceModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface NginxAccessSourceMapper {

    List<NginxAccessSourceModel> findAll();

    NginxAccessSourceModel findById(@Param("id") Long id);

    List<NginxAccessSourceModel> findByNodeId(@Param("nodeId") Long nodeId);

    List<NginxAccessSourceModel> findEnabledByNodeId(@Param("nodeId") Long nodeId);

    int insert(NginxAccessSourceModel model);

    int update(NginxAccessSourceModel model);

    int deleteById(@Param("id") Long id);

    int updateReportStatus(@Param("id") Long id,
                           @Param("lastReportTime") Long lastReportTime,
                           @Param("lastError") String lastError);
}
