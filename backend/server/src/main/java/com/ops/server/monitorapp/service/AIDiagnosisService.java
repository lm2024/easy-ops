package com.ops.server.monitorapp.service;

import com.ops.common.model.AIDiagnosisRecordModel;
import com.ops.common.model.ProjectModel;
import com.ops.server.mapper.AIDiagnosisRecordMapper;
import com.ops.server.mapper.ProjectMapper;
import com.ops.server.service.AIServiceHelper;
import com.ops.server.util.SecurityContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * AI 诊断：构建上下文、调用大模型、保存记录
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class AIDiagnosisService {

    @Autowired
    private ContextBudgetService contextBudgetService;
    @Autowired
    private AIServiceHelper aiServiceHelper;
    @Autowired
    private AIDiagnosisRecordMapper diagnosisMapper;
    @Autowired
    private ProjectMapper projectMapper;
    @Autowired
    private SecurityContext securityContext;

    /**
     * 触发 AI 诊断并保存记录
     */
    public Map<String, Object> diagnose(Long projectId, Long nodeId, String triggerType,
                                        String question, String logPath) {
        ProjectModel project = projectMapper.findById(projectId);
        if (project == null) {
            throw new IllegalArgumentException("项目不存在");
        }

        Map<String, Object> ctxMeta = contextBudgetService.buildContext(projectId, nodeId, logPath);
        String context = ctxMeta.get("context").toString();
        String q = question != null && !question.isEmpty()
                ? question : "请分析当前应用异常原因并给出修复建议";

        String diagnosis = aiServiceHelper.callAI(q, context);
        String severity = inferSeverity(diagnosis, triggerType);
        int tokenUsed = aiServiceHelper.estimateTokens(context + diagnosis);

        AIDiagnosisRecordModel record = new AIDiagnosisRecordModel();
        record.setProjectId(projectId);
        record.setNodeId(nodeId);
        record.setTriggerType(triggerType != null ? triggerType : "MANUAL");
        record.setQuestion(q);
        record.setContextSummary(context.length() > 5000 ? context.substring(0, 5000) : context);
        record.setDiagnosis(diagnosis);
        record.setSeverity(severity);
        record.setSavedToKb(0);
        record.setOperatorId(securityContext.getCurrentUserId());
        record.setTokenUsed(tokenUsed);
        record.setCreateTime(System.currentTimeMillis());
        diagnosisMapper.insert(record);

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("id", record.getId());
        result.put("severity", severity);
        result.put("diagnosis", diagnosis);
        Map<String, Object> summary = new HashMap<String, Object>();
        summary.put("monitorSnapshots", ctxMeta.get("monitorSnapshots"));
        summary.put("errorLogLines", ctxMeta.get("errorLogLines"));
        summary.put("tokenEstimated", ctxMeta.get("tokenEstimated"));
        result.put("contextSummary", summary);
        result.put("savedToKb", false);
        return result;
    }

    /**
     * 获取诊断报告
     */
    public AIDiagnosisRecordModel getById(Long id) {
        return diagnosisMapper.findById(id);
    }

    /**
     * 标记诊断已保存到知识库
     */
    public void markSavedToKb(Long id, Long kbDocumentId) {
        diagnosisMapper.updateKbLink(id, kbDocumentId, 1);
    }

    private String inferSeverity(String diagnosis, String triggerType) {
        if ("DOWN".equals(triggerType) || "SELF_HEAL_FAIL".equals(triggerType)) {
            return "CRITICAL";
        }
        if (diagnosis != null && (diagnosis.contains("严重") || diagnosis.contains("CRITICAL"))) {
            return "CRITICAL";
        }
        if ("SLOW".equals(triggerType)) {
            return "WARNING";
        }
        return "INFO";
    }
}
