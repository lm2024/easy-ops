package com.ops.common.model;

import lombok.Data;

import java.io.Serializable;

/**
 * 项目脚本文件定义（支持任意目录的脚本/配置文件）
 */
@Data
public class ProjectScriptFileModel implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long projectId;
    private String fileName;
    private String filePath;        // 文件路径（绝对路径或相对路径）
    private String fileType;        // 文件类型：sh/conf/cron/service/yaml/yml/properties/other
    private String description;     // 文件描述
    private Integer isExecutable;   // 是否需要可执行权限：0-否 1-是
    private Integer autoBackup;     // 分发前是否自动备份：0-否 1-是
    private Long createTime;
    private Long updateTime;
}
