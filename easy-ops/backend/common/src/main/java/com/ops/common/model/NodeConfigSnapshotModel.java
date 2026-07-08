package com.ops.common.model;

import lombok.Data;

import java.io.Serializable;

/**
 * 节点配置快照
 */
@Data
public class NodeConfigSnapshotModel implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long projectId;
    private Long nodeId;
    private Long configFileId;
    private String contentHash;
    private Integer contentSize;
    /** 0=未知 1=一致 2=差异 3=定制 */
    private Integer syncStatus;
    private Long lastSyncTime;
    private Long updateTime;
}
