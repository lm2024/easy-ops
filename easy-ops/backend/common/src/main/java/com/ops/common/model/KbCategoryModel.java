package com.ops.common.model;

import lombok.Data;
import java.io.Serializable;

/**
 * 知识库分类
 */
@Data
public class KbCategoryModel implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long parentId;
    private String name;
    private String icon;
    private String color;
    private Integer sortOrder;
    private Long projectId;
    private Long createTime;
    private Long updateTime;
}
