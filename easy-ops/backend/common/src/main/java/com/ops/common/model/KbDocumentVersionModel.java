package com.ops.common.model;

import lombok.Data;
import java.io.Serializable;

/**
 * 知识库文档版本历史
 */
@Data
public class KbDocumentVersionModel implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long documentId;
    private Integer versionNo;
    private String title;
    private String content;
    private Long editorId;
    private String changeNote;
    private Long createTime;
}
