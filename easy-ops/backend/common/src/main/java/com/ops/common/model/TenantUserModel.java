package com.ops.common.model;

import lombok.Data;

import java.io.Serializable;

/** 用户在租户中的成员关系。 */
@Data
public class TenantUserModel implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long tenantId;
    private Long userId;
    private String role;
    private Integer status;
    private Long createTime;
    private Long updateTime;
}
