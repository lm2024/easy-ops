package com.ops.common.model;

import lombok.Data;

import java.io.Serializable;

/**
 * 项目配置文件定义
 */
@Data
public class ProjectConfigFileModel implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long projectId;
    private String fileName;
    private String relativePath;
    private Integer isPrimary;
    private String remark;
    private Long createTime;
    private Long updateTime;
}
