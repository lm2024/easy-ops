package com.ops.common.model;

import lombok.Data;

import java.io.Serializable;

/** 节点认领/转移申请记录。租户申请池节点管理权，平台管理员审批后节点转移归属。 */
@Data
public class NodeTransferApplicationModel implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long nodeId;
    private String nodeName;
    private Long applicantId;
    private String applicantUsername;
    private Long targetTenantId;
    private String targetTenantName;
    /** 申请时的节点归属租户（池节点 = default 租户 id） */
    private Long sourceTenantId;
    /** PENDING / APPROVED / REJECTED / CANCELED */
    private String status;
    private String remark;
    private Long createTime;
    private Long updateTime;
    private Long approveTime;
    private Long approverId;
    private String approverUsername;
}
