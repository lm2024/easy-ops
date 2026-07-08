package com.ops.server.mapper;

import com.ops.common.model.ConfigDistributeRecordModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 配置分发记录 Mapper
 */
@Mapper
public interface ConfigDistributeRecordMapper {

    ConfigDistributeRecordModel findById(@Param("id") Long id);

    int insert(ConfigDistributeRecordModel model);

    int updateStatus(@Param("id") Long id,
                     @Param("status") Integer status,
                     @Param("resultDetail") String resultDetail);
}
