package com.ops.common.model;

import lombok.Data;

import java.io.Serializable;

/**
 * Nginx 访问日志采集源配置
 */
@Data
public class NginxAccessSourceModel implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long nodeId;
    private String name;
    private String logPath;
    /** 日志格式：main / combined */
    private String logFormat;
    /** 1=启用 0=停用 */
    private Integer enabled;
    /** 慢请求阈值（秒） */
    private Double slowThresholdSec;
    private Integer maxKeysPerMinute;
    private Long lastOffset;
    private Long lastInode;
    private Long lastReportTime;
    private String lastError;
    private Long createTime;
    private Long updateTime;
}
