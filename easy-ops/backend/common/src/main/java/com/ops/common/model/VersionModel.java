package com.ops.common.model;

import lombok.Data;
import java.io.Serializable;

/**
 * 版本包模型
 */
@Data
public class VersionModel implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long projectId;
    private String jarName;
    private String filePath;
    private Long fileSize;
    private String version;
    private String sha256;
    private String remark;
    private Long createTime;
}
