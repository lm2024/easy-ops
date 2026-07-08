package com.ops.server.mapper;

import com.ops.common.model.SelfHealEventModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 自愈事件 Mapper
 */
@Mapper
public interface SelfHealEventMapper {

    List<SelfHealEventModel> findByFilters(@Param("projectId") Long projectId,
                                         @Param("page") Integer page,
                                         @Param("pageSize") Integer pageSize);

    Long countByFilters(@Param("projectId") Long projectId);

    SelfHealEventModel findLatest(@Param("projectId") Long projectId,
                                  @Param("nodeId") Long nodeId);

    int insert(SelfHealEventModel event);
}
