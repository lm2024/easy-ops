package com.ops.common.model;

import lombok.Data;

import java.io.Serializable;

/**
 * 站内通知记录模型
 */
@Data
public class NotificationRecordModel implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String type;
    private String level;
    private String title;
    private String content;
    private Long projectId;
    private Long nodeId;
    private String sourceType;
    private Long sourceId;
    private Integer requireAck;
    private Integer broadcast;
    private Long createTime;
    private Long expireTime;

    /** 用户维度状态（关联查询） */
    private Integer readStatus;
    private Integer ackStatus;
}
