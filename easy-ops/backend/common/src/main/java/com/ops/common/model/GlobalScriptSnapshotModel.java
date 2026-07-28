package com.ops.common.model;

import lombok.Data;

import java.io.Serializable;

/**
 * 全局脚本节点快照
 */
@Data
public class GlobalScriptSnapshotModel implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long nodeId;
    private Long scriptFileId;
    private String contentHash;     // 文件内容SHA256哈希
    private Long contentSize;       // 文件大小（字节）
    private Integer fileMode;       // 文件权限（八进制，如 755）
    private Integer syncStatus;     // 同步状态：0-未知 1-一致 2-差异 3-定制
    private Long lastSyncTime;
    private Long updateTime;
}
