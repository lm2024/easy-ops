package com.ops.server.arthas;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ops.common.model.ArthasDiagnoseRecordModel;
import com.ops.common.model.ArthasDiagnoseResultModel;
import com.ops.common.model.ProjectModel;
import com.ops.server.mapper.ArthasDiagnoseRecordMapper;
import com.ops.server.mapper.ArthasDiagnoseResultMapper;
import com.ops.server.mapper.ProjectMapper;
import com.ops.server.util.SecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Arthas 诊断业务服务（核心）
 * 负责诊断会话生命周期管理、命令透传与结果持久化
 */
@Service
public class ArthasDiagnoseService {
    private static final Logger log = LoggerFactory.getLogger(ArthasDiagnoseService.class);

    @Autowired
    private ArthasAgentProxy agentProxy;

    @Autowired
    private ArthasDiagnoseRecordMapper recordMapper;

    @Autowired
    private ArthasDiagnoseResultMapper resultMapper;

    @Autowired
    private ProjectMapper projectMapper;

    @Autowired
    private SecurityContext securityContext;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 启动诊断会话
     */
    public Map<String, Object> startDiagnose(Long projectId, Long nodeId, long pid) {
        // 权限校验
        if (!securityContext.hasProjectPermission(projectId)) {
            throw new RuntimeException("无权限访问该项目");
        }

        ProjectModel project = projectMapper.findById(projectId);
        if (project == null) {
            throw new RuntimeException("项目不存在");
        }

        // 创建会话记录
        String sessionId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        long now = System.currentTimeMillis();

        ArthasDiagnoseRecordModel record = new ArthasDiagnoseRecordModel();
        record.setSessionId(sessionId);
        record.setProjectId(projectId);
        record.setNodeId(nodeId);
        record.setPid((int) pid);
        record.setJarName(project.getJarName());
        record.setStatus("ATTACHING");
        record.setTriggerBy(securityContext.getCurrentUsername());
        record.setStartTime(now);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        record.setTenantId(securityContext.getCurrentTenantId());
        recordMapper.insert(record);

        // 调用 Agent attach
        try {
            Map<String, Object> attachResult = agentProxy.attach(nodeId, pid, projectId);
            String arthasVersion = attachResult.get("arthasVersion") != null
                    ? attachResult.get("arthasVersion").toString() : "unknown";

            // 更新记录
            record.setStatus("RUNNING");
            record.setArthasVersion(arthasVersion);
            record.setUpdatedAt(System.currentTimeMillis());
            recordMapper.update(record);

            Map<String, Object> data = new HashMap<>();
            data.put("sessionId", sessionId);
            data.put("recordId", record.getId());
            data.put("pid", pid);
            data.put("projectId", projectId);
            data.put("nodeId", nodeId);
            data.put("arthasVersion", arthasVersion);
            data.put("attachTime", now);
            data.put("status", "ATTACHED");
            log.info("Arthas 诊断会话启动成功: sessionId={}, projectId={}, nodeId={}, pid={}",
                    sessionId, projectId, nodeId, pid);
            return data;
        } catch (Exception e) {
            // attach 失败，更新记录
            record.setStatus("FAILED");
            record.setException(e.getMessage());
            record.setEndTime(System.currentTimeMillis());
            record.setUpdatedAt(System.currentTimeMillis());
            recordMapper.update(record);
            log.error("Arthas 诊断会话启动失败: sessionId={}, error={}", sessionId, e.getMessage());
            throw new RuntimeException("attach 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 结束诊断会话
     */
    public Map<String, Object> stopDiagnose(String sessionId) {
        ArthasDiagnoseRecordModel record = recordMapper.findBySessionId(sessionId);
        if (record == null) {
            throw new RuntimeException("诊断会话不存在: sessionId=" + sessionId);
        }

        long now = System.currentTimeMillis();
        try {
            agentProxy.detach(record.getNodeId(), record.getPid());
        } catch (Exception e) {
            log.warn("detach 失败（可能会话已结束）: sessionId={}, error={}", sessionId, e.getMessage());
        }

        // 更新记录
        record.setStatus("FINISHED");
        record.setEndTime(now);
        record.setDurationMs((int) (now - record.getStartTime()));
        record.setUpdatedAt(now);
        recordMapper.update(record);

        Map<String, Object> data = new HashMap<>();
        data.put("sessionId", sessionId);
        data.put("status", "DETACHED");
        data.put("durationMs", record.getDurationMs());
        log.info("Arthas 诊断会话结束: sessionId={}, durationMs={}", sessionId, record.getDurationMs());
        return data;
    }

    /**
     * 执行命令并持久化结果
     */
    public Map<String, Object> execCommand(String sessionId, String command, int timeoutMs) {
        ArthasDiagnoseRecordModel record = recordMapper.findBySessionId(sessionId);
        if (record == null) {
            throw new RuntimeException("诊断会话不存在: sessionId=" + sessionId);
        }
        if (!"RUNNING".equals(record.getStatus())) {
            throw new RuntimeException("诊断会话已结束: status=" + record.getStatus());
        }

        long execStart = System.currentTimeMillis();
        Map<String, Object> result = agentProxy.exec(record.getNodeId(), record.getPid(), command, timeoutMs);
        int durationMs = (int) (System.currentTimeMillis() - execStart);

        // 持久化结果
        boolean success = result.get("success") != null && Boolean.TRUE.equals(result.get("success"));
        String resultJson = null;
        try {
            Object results = result.get("results");
            if (results != null) {
                resultJson = MAPPER.writeValueAsString(results);
            }
        } catch (Exception e) {
            log.warn("序列化命令结果失败: {}", e.getMessage());
        }

        ArthasDiagnoseResultModel resultModel = new ArthasDiagnoseResultModel();
        resultModel.setRecordId(record.getId());
        resultModel.setCommand(command);
        resultModel.setCommandType(result.get("commandType") != null ? result.get("commandType").toString() : "unknown");
        resultModel.setResultJson(resultJson);
        resultModel.setResultSizeKb(resultJson != null ? resultJson.getBytes().length / 1024 : 0);
        resultModel.setExecTime(execStart);
        resultModel.setDurationMs(durationMs);
        resultModel.setSuccess(success);
        resultModel.setErrorMsg(!success && result.get("errorMsg") != null ? result.get("errorMsg").toString() : null);
        resultModel.setTenantId(record.getTenantId());
        resultMapper.insert(resultModel);

        // 更新记录的 updatedAt
        record.setUpdatedAt(System.currentTimeMillis());
        recordMapper.update(record);

        return result;
    }

    /**
     * 查询会话状态
     */
    public Map<String, Object> getStatus(String sessionId) {
        ArthasDiagnoseRecordModel record = recordMapper.findBySessionId(sessionId);
        if (record == null) {
            throw new RuntimeException("诊断会话不存在: sessionId=" + sessionId);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("sessionId", sessionId);
        data.put("recordId", record.getId());
        data.put("status", record.getStatus());
        data.put("pid", record.getPid());
        data.put("projectId", record.getProjectId());
        data.put("nodeId", record.getNodeId());
        data.put("arthasVersion", record.getArthasVersion());
        data.put("startTime", record.getStartTime());
        data.put("endTime", record.getEndTime());
        data.put("durationMs", record.getDurationMs());
        return data;
    }

    /**
     * 诊断历史列表
     */
    public Map<String, Object> getHistory(Long projectId, int page, int pageSize) {
        if (!securityContext.hasProjectPermission(projectId)) {
            throw new RuntimeException("无权限访问该项目");
        }
        int offset = (page - 1) * pageSize;
        List<ArthasDiagnoseRecordModel> list = recordMapper.findByProjectId(projectId, offset, pageSize);
        int total = recordMapper.countByProjectId(projectId);

        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", total);
        data.put("page", page);
        data.put("pageSize", pageSize);
        return data;
    }

    /**
     * 诊断详情
     */
    public Map<String, Object> getDetail(Long id) {
        ArthasDiagnoseRecordModel record = recordMapper.findById(id);
        if (record == null) {
            throw new RuntimeException("诊断记录不存在: id=" + id);
        }
        if (!securityContext.hasProjectPermission(record.getProjectId())) {
            throw new RuntimeException("无权限访问该项目");
        }
        List<ArthasDiagnoseResultModel> results = resultMapper.findByRecordId(id);

        Map<String, Object> data = new HashMap<>();
        data.put("record", record);
        data.put("results", results);
        return data;
    }

    /**
     * 获取火焰图历史文件列表
     */
    public Map<String, Object> getFlamegraphList(String sessionId) {
        ArthasDiagnoseRecordModel record = recordMapper.findBySessionId(sessionId);
        if (record == null) {
            throw new RuntimeException("诊断会话不存在: sessionId=" + sessionId);
        }
        List<Map<String, Object>> list = agentProxy.flamegraphList(record.getNodeId(), record.getPid());
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", list.size());
        data.put("downloadBaseUrl", "/api/arthas/diagnose/flamegraph/download?sessionId=" + sessionId + "&fileName=");
        return data;
    }

    /**
     * 获取火焰图下载 URL
     */
    public String getFlamegraphDownloadUrl(String sessionId, String fileName) {
        ArthasDiagnoseRecordModel record = recordMapper.findBySessionId(sessionId);
        if (record == null) {
            throw new RuntimeException("诊断会话不存在: sessionId=" + sessionId);
        }
        return agentProxy.buildFlamegraphDownloadUrl(record.getNodeId(), record.getPid(), fileName);
    }
}
