package com.ops.server.monitorapp.controller;

import com.ops.common.model.AIDiagnosisRecordModel;
import com.ops.common.response.Result;
import com.ops.server.monitorapp.service.AIDiagnosisService;
import com.ops.server.util.SecurityContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * AI 诊断接口
 */
@RestController
@RequestMapping("/ai")
public class AIDiagnosisController {

    @Autowired
    private AIDiagnosisService diagnosisService;
    @Autowired
    private SecurityContext securityContext;

    /**
     * POST /api/ai/diagnose - 触发 AI 诊断
     */
    @PostMapping("/diagnose")
    public Result<?> diagnose(@RequestBody Map<String, Object> body) {
        Long projectId = toLong(body.get("projectId"));
        if (projectId == null) {
            return Result.paramError("projectId 不能为空");
        }
        if (!securityContext.hasProjectPermission(projectId)) {
            return Result.error(403, "无权限访问该项目");
        }
        Long nodeId = toLong(body.get("nodeId"));
        String triggerType = body.get("triggerType") != null ? body.get("triggerType").toString() : "MANUAL";
        String question = body.get("question") != null ? body.get("question").toString() : null;
        String logPath = body.get("logPath") != null ? body.get("logPath").toString() : null;

        try {
            Map<String, Object> result = diagnosisService.diagnose(projectId, nodeId, triggerType, question, logPath);
            return Result.success(result);
        } catch (IllegalArgumentException e) {
            return Result.error(1005, e.getMessage());
        }
    }

    /**
     * GET /api/ai/diagnose/{id} - 获取诊断报告
     */
    @GetMapping("/diagnose/{id}")
    public Result<?> getDiagnosis(@PathVariable Long id) {
        AIDiagnosisRecordModel record = diagnosisService.getById(id);
        if (record == null) {
            return Result.error(1004, "诊断记录不存在");
        }
        if (!securityContext.hasProjectPermission(record.getProjectId())) {
            return Result.error(403, "无权限访问该项目");
        }
        return Result.success(record);
    }

    /**
     * POST /api/ai/diagnose/{id}/save-kb - 保存诊断到知识库（stub）
     */
    @PostMapping("/diagnose/{id}/save-kb")
    public Result<?> saveToKb(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        AIDiagnosisRecordModel record = diagnosisService.getById(id);
        if (record == null) {
            return Result.error(1004, "诊断记录不存在");
        }
        if (!securityContext.hasProjectPermission(record.getProjectId())) {
            return Result.error(403, "无权限访问该项目");
        }
        Map<String, Object> stub = new HashMap<String, Object>();
        stub.put("diagnosisId", id);
        stub.put("saved", false);
        stub.put("message", "请通过知识库模块创建文档并关联诊断记录");
        if (body != null && body.get("categoryId") != null) {
            stub.put("categoryId", body.get("categoryId"));
        }
        return Result.success(stub);
    }

    private Long toLong(Object val) {
        if (val == null) {
            return null;
        }
        if (val instanceof Number) {
            return ((Number) val).longValue();
        }
        try {
            return Long.parseLong(val.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
