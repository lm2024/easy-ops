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
}
