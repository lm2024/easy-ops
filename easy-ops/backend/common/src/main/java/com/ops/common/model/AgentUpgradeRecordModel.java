package com.ops.common.model;

import lombok.Data;
import java.io.Serializable;

/**
 * Agent 升级记录模型
 */
@Data
public class AgentUpgradeRecordModel implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String upgradeBatchId;
    private String targetVersion;
    private Long nodeId;
    private String nodeName;
    private String oldVersion;
    /** 0-待升级 1-升级中 2-成功 3-失败 4-已回滚 */
    private Integer status;
    private String errorMessage;
    private Long startTime;
    private Long endTime;
    private Long createTime;
    private Long tenantId;
}
