package com.ops.common.model;

import lombok.Data;

import java.io.Serializable;

/**
 * 脚本分发记录
 */
@Data
public class ScriptDistributeRecordModel implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long projectId;
    private Long scriptFileId;
    private Long operatorId;
    private String targetNodeIds;   // 逗号分隔的节点ID列表
    private String contentHash;
    private Integer setExecutable;  // 是否设置可执行权限
    private Integer autoBackup;     // 是否自动备份
    private Integer status;         // 0-进行中 1-成功 2-部分失败 3-失败
    private String resultDetail;
    private Long createTime;
    private Long tenantId;
}
