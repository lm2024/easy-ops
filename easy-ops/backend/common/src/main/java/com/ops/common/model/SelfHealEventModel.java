package com.ops.common.model;

import lombok.Data;

import java.io.Serializable;

/**
 * 自愈事件模型
 */
@Data
public class SelfHealEventModel implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long projectId;
    private Long nodeId;
    private String eventType;
    private Integer retryCount;
    private Integer maxRetries;
    private String detail;
    private Integer processPid;
    private Long createTime;

    /** 关联查询：节点名称 */
    private String nodeName;
}
