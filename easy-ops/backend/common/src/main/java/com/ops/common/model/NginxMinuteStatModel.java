package com.ops.common.model;

import lombok.Data;

import java.io.Serializable;

/**
 * Nginx 访问日志分钟级统计
 */
@Data
public class NginxMinuteStatModel implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long sourceId;
    /** 分钟桶起始时间戳（毫秒） */
    private Long bucketTime;
    private String clientIp;
    private String uri;
    private String method;
    private Integer requestCount;
    private Long sumRequestTimeMs;
    private Long maxRequestTimeMs;
    private Long sumUpstreamTimeMs;
    private Integer status2xx;
    private Integer status4xx;
    private Integer status5xx;
    private Integer slowCount;
    private Long createTime;
}
