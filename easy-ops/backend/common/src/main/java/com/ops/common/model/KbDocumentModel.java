package com.ops.common.model;

import lombok.Data;
import java.io.Serializable;

/**
 * 知识库文档
 */
@Data
public class KbDocumentModel implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long categoryId;
    private String title;
    private String summary;
    private String content;
    private Integer contentSize;
    private String sourceType;
    private Long sourceId;
    private Long projectId;
    private Long authorId;
    private Long lastEditorId;
    private Integer versionNo;
    private Integer status;
    private Integer viewCount;
    private byte[] yjsState;
    private Long createTime;
    private Long updateTime;
}
