package com.ops.common.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.io.Serializable;

/**
 * 项目管理模型
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectModel implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String nodeIds;
    private String startScript;
    private String stopScript;
    private String restartScript;
    private String jvmOpts;
    private String envVars;
    private String jarName;
    private String deployDir;
    /** 部署后是否执行健康检查，默认 true；置 false 可跳过健康检查直接判定部署成功 */
    private Boolean healthCheckEnabled;
    /** 健康检查端口，默认 8080 */
    private Integer healthCheckPort;
    /** 健康检查路径，默认 /hello */
    private String healthCheckPath;
    /** 健康检查关键字（逗号分隔，响应含任一即视为健康），默认 Hello,DEPLOYED */
    private String healthCheckKeyword;
    private Integer status;
    /** 监控采集间隔（秒），默认 60 */
    private Integer monitorIntervalSec;
    private Long createTime;
    private Long updateTime;
}
