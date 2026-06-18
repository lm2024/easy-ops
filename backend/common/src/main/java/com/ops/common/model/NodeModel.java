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
}
