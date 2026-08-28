package com.ops.common.model;

import lombok.Data;
import java.io.Serializable;

/**
 * Arthas 诊断会话记录
 */
@Data
public class ArthasDiagnoseRecordModel implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String sessionId;
    private Long projectId;
    private Long nodeId;
    private Integer pid;
    private String jarName;
    private String status;
    private String triggerBy;
    private String arthasVersion;
    private Long startTime;
    private Long endTime;
    private Integer durationMs;
    private String summary;
    private String exception;
    private Long tenantId;
    private Long createdAt;
    private Long updatedAt;
}
