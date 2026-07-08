package com.ops.common.model;

import lombok.Data;

import java.io.Serializable;

/**
 * 配置分发记录
 */
@Data
public class ConfigDistributeRecordModel implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long projectId;
    private Long configFileId;
    private Long operatorId;
    private String targetNodeIds;
    private String distributeType;
    private String contentHash;
    private Integer restartAfter;
    /** 0=进行中 1=成功 2=部分失败 3=失败 */
    private Integer status;
    private String resultDetail;
    private Long createTime;
}
