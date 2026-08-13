package com.ops.server.mapper;

import com.ops.common.model.NginxSourceWhitelistModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface NginxSourceWhitelistMapper {

    /** 按日志源批量查启用白名单（sourceIds 为空时返回空集，不报错） */
    List<NginxSourceWhitelistModel> findEnabledBySourceIds(@Param("sourceIds") List<Long> sourceIds);

    List<NginxSourceWhitelistModel> findBySourceId(@Param("sourceId") Long sourceId);

    int insert(NginxSourceWhitelistModel model);

    int update(NginxSourceWhitelistModel model);

    int deleteBySourceId(@Param("sourceId") Long sourceId, @Param("tenantId") Long tenantId);

    int deleteByIds(@Param("sourceId") Long sourceId, @Param("tenantId") Long tenantId, @Param("ids") List<Long> ids);
}
