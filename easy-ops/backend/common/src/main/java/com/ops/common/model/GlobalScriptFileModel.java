package com.ops.common.model;

import lombok.Data;

import java.io.Serializable;

/**
 * 全局脚本文件定义（管理所有 Agent 节点的脚本/配置文件）
 * 不绑定项目，用于管理 Agent 自身的脚本（如 start.sh、stop.sh）
 */
@Data
public class GlobalScriptFileModel implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String fileName;
    private String filePath;        // 文件路径（Agent 节点上的绝对路径）
    private String fileType;        // 文件类型：sh/conf/cron/service/yaml/yml/properties/other
    private String description;     // 文件描述/用途说明
    private Integer isExecutable;   // 是否需要可执行权限：0-否 1-是
    private Integer autoBackup;     // 分发前是否自动备份：0-否 1-是
    private Long createTime;
    private Long updateTime;
}
