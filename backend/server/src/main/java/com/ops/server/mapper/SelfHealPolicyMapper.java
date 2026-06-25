package com.ops.server.mapper;

import com.ops.common.model.SelfHealPolicyModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 自愈策略 Mapper
 */
@Mapper
public interface SelfHealPolicyMapper {

    List<SelfHealPolicyModel> findAll();

    List<SelfHealPolicyModel> findEnabled();

    SelfHealPolicyModel findByProjectId(@Param("projectId") Long projectId);

    int insert(SelfHealPolicyModel policy);

    int update(SelfHealPolicyModel policy);

    int updateCircuitBreaker(@Param("projectId") Long projectId,
                             @Param("circuitBreaker") Integer circuitBreaker,
                             @Param("circuitBreakTime") Long circuitBreakTime,
                             @Param("updateTime") Long updateTime);
}
