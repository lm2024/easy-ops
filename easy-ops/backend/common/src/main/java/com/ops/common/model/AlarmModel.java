package com.ops.common.model;

import lombok.Data;
import java.io.Serializable;

/**
 * 告警记录模型
 */
@Data
public class AlarmModel implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long projectId;
    private Long nodeId;
    private String type;
    private String content;
    private Integer sendResult;
    private Long sendTime;
    private Long createTime;
}
