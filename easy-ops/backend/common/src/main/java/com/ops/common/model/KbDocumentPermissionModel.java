package com.ops.common.model;

import lombok.Data;
import java.io.Serializable;

/**
 * 文档权限
 */
@Data
public class KbDocumentPermissionModel implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long targetId;
    private String targetType;
    private Long userId;
    private String permissionLevel;
    private Long createTime;
}
