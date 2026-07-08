package com.ops.common.model;

import lombok.Data;
import java.io.Serializable;

/**
 * 项目 HTTP 健康探针配置
 */
@Data
public class ProjectHealthProbeModel implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long projectId;
    private Integer enabled;
    private String method;
    private String url;
    private String headers;
    private String body;
    private Integer expectedStatus;
    private String bodyContains;
    private Integer timeoutMs;
    private Long createTime;
    private Long updateTime;
}
