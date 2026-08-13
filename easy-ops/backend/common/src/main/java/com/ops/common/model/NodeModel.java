package com.ops.common.model;

import lombok.Data;
import java.io.Serializable;

/**
 * 节点信息模型
 */
@Data
public class NodeModel implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long tenantId;
    private String name;
    private String ip;
    private Integer port;
    private String token;
    private Integer status;
    private String osInfo;
    private String javaVersion;
    private Long lastHeartbeat;
    private Long createTime;
    private Long updateTime;

    // ====== 新增：标签 & 系统硬件信息 ======
    /** 标签（逗号分隔，如 "dev,frontend,核心服务"） */
    private String tags;

    /** CPU 逻辑核数 */
    private Integer cpuCores;

    /** 总内存（MB） */
    private Integer totalMemoryMb;

    /** 总磁盘（MB） */
    private Long totalDiskMb;

    /** 系统架构 */
    private String osArch;

    /** Agent 版本号（心跳上报） */
    private String agentVersion;

    /** Agent 进程 PID */
    private Long agentPid;

    /** 磁盘信息JSON（心跳上报，格式：[{mountPoint,totalGB,usedGB,freeGB,usagePercent},...]） */
    private String diskInfoJson;

    /** 主机CPU使用率（心跳上报） */
    private Double hostCpuPercent;

    /** 主机内存使用率（心跳上报） */
    private Integer hostMemoryPercent;

    /** 主机磁盘使用率（心跳上报） */
    private Integer diskUsagePercent;

    /** 是否为可认领池节点（default 租户归属、非当前租户，transient 列表标记） */
    private Boolean claimable;

    /** 所属租户名称（transient，列表展示） */
    private String tenantName;
}
