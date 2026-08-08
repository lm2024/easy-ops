package com.ops.common.model;

import lombok.Data;

import java.io.Serializable;

/** 租户模型。租户 code 是稳定的外部标识，id 仅用于内部关联。 */
@Data
public class TenantModel implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String code;
    private String name;
    private Integer status;
    private Long createTime;
    private Long updateTime;
}
