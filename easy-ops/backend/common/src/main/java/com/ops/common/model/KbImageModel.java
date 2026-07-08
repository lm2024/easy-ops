package com.ops.common.model;

import lombok.Data;
import java.io.Serializable;

/**
 * 知识库文档图片
 */
@Data
public class KbImageModel implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long documentId;
    private String fileName;
    private String filePath;
    private Integer fileSize;
    private String mimeType;
    private Long uploaderId;
    private Long createTime;
}
