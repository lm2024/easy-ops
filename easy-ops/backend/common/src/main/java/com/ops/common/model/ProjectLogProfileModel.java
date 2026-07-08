package com.ops.common.model;

import lombok.Data;

import java.io.Serializable;

/**
 * 项目日志配置
 */
@Data
public class ProjectLogProfileModel implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long projectId;
    private String logDir;
    private String mainLogFile;
    private String rollingPattern;
    private String timestampRegex;
    private String timestampFormat;
    private Integer maxLineLength;
    private Long createTime;
    private Long updateTime;
}
