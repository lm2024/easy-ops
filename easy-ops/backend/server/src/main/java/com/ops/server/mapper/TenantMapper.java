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
    int insert(TenantModel tenant);
    int insertMember(TenantUserModel member);
    TenantUserModel findMember(@Param("tenantId") Long tenantId, @Param("userId") Long userId);
    TenantUserModel findFirstActiveMember(@Param("userId") Long userId);
    List<Long> findTenantIdsByUserId(@Param("userId") Long userId);
}
