package com.ops.common.enums;

/**
 * 用户角色枚举
 */
public enum UserRole {
    ADMIN("admin"),
    OPERATOR("operator");

    private final String code;

    UserRole(String code) { this.code = code; }
    public String getCode() { return code; }
}
