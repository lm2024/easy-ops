package com.ops.common.model;

import lombok.Data;
import java.io.Serializable;

/**
 * 文档-标签关联
 */
@Data
public class KbDocumentTagModel implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long documentId;
    private Long tagId;
    private Long createTime;
}
