package com.ops.common.model;

import lombok.Data;
import java.io.Serializable;

/**
 * Arthas 诊断命令执行结果
 */
@Data
public class ArthasDiagnoseResultModel implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long recordId;
    private String command;
    private String commandType;
    private String resultJson;
    private String resultFile;
    private Integer resultSizeKb;
    private Long execTime;
    private Integer durationMs;
    private Boolean success;
    private String errorMsg;
    private Long tenantId;
}
