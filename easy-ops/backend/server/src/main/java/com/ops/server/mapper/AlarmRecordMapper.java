package com.ops.server.mapper;

import com.ops.common.model.AlarmModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AlarmRecordMapper {
    List<AlarmModel> findByFilters(@Param("projectId") Long projectId, @Param("type") String type,
                                   @Param("page") Integer page, @Param("pageSize") Integer pageSize);
    Long countByFilters(@Param("projectId") Long projectId, @Param("type") String type);
    int insert(AlarmModel alarm);
    int deleteAll();

    int deleteBefore(@Param("cutoff") Long cutoff);
}
