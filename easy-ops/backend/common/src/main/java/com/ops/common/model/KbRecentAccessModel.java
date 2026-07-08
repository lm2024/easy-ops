package com.ops.common.model;

import lombok.Data;
import java.io.Serializable;

/**
 * 知识库最近访问
 */
@Data
public class KbRecentAccessModel implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long documentId;
    private Long userId;
    private String accessType;
    private Long createTime;
}
