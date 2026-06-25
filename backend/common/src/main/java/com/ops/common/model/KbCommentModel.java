package com.ops.common.model;

import lombok.Data;
import java.io.Serializable;

/**
 * 知识库评论
 */
@Data
public class KbCommentModel implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long documentId;
    private Long parentId;
    private Long userId;
    private String content;
    private Integer rating;
    private Long createTime;
    private Long updateTime;
}
