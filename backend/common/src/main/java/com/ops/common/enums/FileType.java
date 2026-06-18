package com.ops.common.enums;

/**
 * 文件类型枚举
 */
public enum FileType {
    YML("yml"),
    LOG("log"),
    JAR("jar");

    private final String ext;

    FileType(String ext) { this.ext = ext; }
    public String getExt() { return ext; }
}
