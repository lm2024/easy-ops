package com.ops.common.model;

import lombok.Data;
import java.io.Serializable;

/**
 * 知识库文档编辑锁
 */
@Data
public class KbDocumentLockModel implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long documentId;
    private Long userId;
    private String userName;
    private Long lockTime;
    private Long expireTime;
}
