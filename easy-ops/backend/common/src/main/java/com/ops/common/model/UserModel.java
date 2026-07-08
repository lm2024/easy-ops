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
}
