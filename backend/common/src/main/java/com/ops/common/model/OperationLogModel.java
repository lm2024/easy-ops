package com.ops.common.model;

import lombok.Data;
import java.io.Serializable;

/**
 * 操作审计日志模型
 */
@Data
public class OperationLogModel implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private String module;
    private String action;
    private String content;
    private String ip;
    private Long createTime;
}
