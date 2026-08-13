package com.ops.server.mapper;

import com.ops.common.model.TenantModel;
import com.ops.common.model.TenantUserModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TenantMapper {
    TenantModel findByCode(@Param("code") String code);
    TenantModel findById(@Param("id") Long id);
    TenantModel findDefault();
    List<TenantModel> findByIds(@Param("ids") List<Long> ids);
    int insert(TenantModel tenant);
    int update(TenantModel tenant);
    int deleteById(@Param("id") Long id);
    List<TenantModel> findAll(@Param("status") Integer status);
    Long countAll();
    int insertMember(TenantUserModel member);
    TenantUserModel findMember(@Param("tenantId") Long tenantId, @Param("userId") Long userId);
    TenantUserModel findFirstActiveMember(@Param("userId") Long userId);
    List<Long> findTenantIdsByUserId(@Param("userId") Long userId);
    List<TenantUserModel> listMembers(@Param("tenantId") Long tenantId);
    int updateMember(TenantUserModel member);
    int deleteMember(@Param("tenantId") Long tenantId, @Param("userId") Long userId);
    int deleteMembersByTenant(@Param("tenantId") Long tenantId);
    int deleteMembersByUser(@Param("userId") Long userId);
    int countMembers(@Param("tenantId") Long tenantId);
    long countNodes(@Param("tenantId") Long tenantId);
    long countProjects(@Param("tenantId") Long tenantId);
}
