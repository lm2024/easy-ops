package com.ops.common.model;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 应用监控快照
 */
@Data
public class MonitorSnapshotModel implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long projectId;
    private Long nodeId;
    private String healthStatus;
    private String healthDetail;
    private String processStatus;
    private Integer processPid;
    private BigDecimal cpuPercent;
    private Integer memoryMb;
    private Integer heapUsedMb;
    private Integer heapMaxMb;
    private Integer gcCount;
    private Integer gcTimeMs;
    private Integer responseMs;
    private BigDecimal hostCpuPercent;
    private Integer hostMemoryPercent;
    private Integer diskUsagePercent;
    private String extraJson;
    private Long collectTime;
}
