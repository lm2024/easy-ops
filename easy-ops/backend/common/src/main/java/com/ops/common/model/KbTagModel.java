package com.ops.common.model;

import lombok.Data;
import java.io.Serializable;

/**
 * 知识库标签
 */
@Data
public class KbTagModel implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String color;
    private Long createTime;
}
