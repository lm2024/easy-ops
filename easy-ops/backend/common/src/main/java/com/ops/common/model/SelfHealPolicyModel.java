package com.ops.common.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;

/**
 * 自愈策略模型
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SelfHealPolicyModel implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long projectId;
    private Integer enabled;
    private Integer maxRetries;
    private Integer retryIntervalSec;
    private Integer checkIntervalSec;
    private Integer circuitBreaker;
    private Long circuitBreakTime;
    private Integer notifyEmail;
    private Integer notifyPopup;
    private Integer autoAiDiagnose;
    private Long createTime;
    private Long updateTime;
}
