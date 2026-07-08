package com.ops.common.model;

import lombok.Data;
import java.io.Serializable;

/**
 * 知识库收藏
 */
@Data
public class KbFavoriteModel implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long documentId;
    private Long userId;
    private Long createTime;
}
