package com.ops.common.model;

import lombok.Data;
import java.io.Serializable;

/**
 * 系统用户模型
 */
@Data
public class UserModel implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String username;
    private String password;
    private String role;
    private Integer status;
    private Long createTime;
    private Long updateTime;

    /** 租户绑定（transient，用户管理接口使用，非 sys_user 表字段） */
    private Long tenantId;
    /** 租户内角色（transient）：TENANT_ADMIN / OPERATOR / VIEWER */
    private String tenantRole;
    /** 租户名称（transient，展示用） */
    private String tenantName;
}
