package com.ops.common.enums;

/**
 * 文件操作类型枚举
 */
public enum FileAction {
    VIEW("view"),
    EDIT("edit"),
    DOWNLOAD("download");

    private final String code;

    FileAction(String code) { this.code = code; }
    public String getCode() { return code; }
}
