package com.ops.common.model;

import lombok.Data;
import java.io.Serializable;

/**
 * AI 诊断记录
 */
@Data
public class AIDiagnosisRecordModel implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long projectId;
    private Long nodeId;
    private String triggerType;
    private String question;
    private String contextSummary;
    private String diagnosis;
    private String severity;
    private Integer savedToKb;
    private Long kbDocumentId;
    private Long operatorId;
    private Integer tokenUsed;
    private Long createTime;
}
