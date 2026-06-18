package com.ops.common.model;

import lombok.Data;
import java.io.Serializable;

/**
 * 文件访问审计模型
 */
@Data
public class FileAccessLogModel implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private Long nodeId;
    private String fileType;
    private String filePath;
    private String action;
    private String contentSummary;
    private String ip;
    private Long createTime;
}
