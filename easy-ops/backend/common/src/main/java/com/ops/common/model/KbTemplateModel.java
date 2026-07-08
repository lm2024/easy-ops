package com.ops.common.model;

import lombok.Data;
import java.io.Serializable;

/**
 * 知识库模板
 */
@Data
public class KbTemplateModel implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String description;
    private String content;
    private String icon;
    private String category;
    private Long userId;
    private Integer isSystem;
    private Long createTime;
    private Long updateTime;
}
