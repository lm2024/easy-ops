package com.ops.common.model;

import lombok.Data;
import java.io.Serializable;

/**
 * 部署记录模型
 */
@Data
public class DeployModel implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long projectId;
    private Long versionId;
    private Long nodeId;
    private Integer status;
    private String jarName;
    private String log;
    private Long startTime;
    private Long endTime;
    private Long createTime;
    private Long scheduleTime;
}
