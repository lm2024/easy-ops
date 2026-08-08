package com.ops.server.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserProjectRelationMapper {

    /**
     * 根据 userId 获取用户可访问的所有 projectIds (SEC-004)
     */
    List<Long> findProjectIdsByUserId(@Param("userId") Long userId);
    List<Long> findProjectIdsByUserIdAndTenant(@Param("userId") Long userId, @Param("tenantId") Long tenantId);

    /**
     * 检查用户是否有权限访问指定项目
     */
    Long countByUserIdAndProjectId(@Param("userId") Long userId, @Param("projectId") Long projectId);
    Long countByUserIdAndProjectIdAndTenant(@Param("userId") Long userId, @Param("projectId") Long projectId,
                                            @Param("tenantId") Long tenantId);

    /**
     * 为管理员角色用户：返回所有 projectId (admin 绕过)
     */
    List<Long> findAllProjectIds();
    List<Long> findAllProjectIdsByTenant(@Param("tenantId") Long tenantId);

    /**
     * 插入用户-项目关系
     */
    int insert(@Param("userId") Long userId, @Param("projectId") Long projectId, @Param("createTime") Long createTime);

    /**
     * 删除用户-项目关系
     */
    int deleteByUserIdAndProjectId(@Param("userId") Long userId, @Param("projectId") Long projectId);
}
