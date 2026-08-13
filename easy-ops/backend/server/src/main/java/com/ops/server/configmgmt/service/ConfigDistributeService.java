package com.ops.server.configmgmt.service;

import com.alibaba.fastjson2.JSON;
import com.ops.common.constant.ErrorCode;
import com.ops.common.exception.BusinessException;
import com.ops.common.model.ConfigDistributeRecordModel;
import com.ops.common.model.NodeConfigSnapshotModel;
import com.ops.common.model.NodeModel;
import com.ops.common.model.ProjectConfigFileModel;
import com.ops.common.model.ProjectModel;
import com.ops.server.client.AgentClient;
import com.ops.server.mapper.ConfigDistributeRecordMapper;
import com.ops.server.mapper.NodeConfigSnapshotMapper;
import com.ops.server.mapper.NodeMapper;
import com.ops.server.mapper.ProjectMapper;
import com.ops.server.util.SecurityContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 配置分发服务
 */
@Service
public class ConfigDistributeService {

    @Autowired
    private AgentClient agentClient;

    @Autowired
    private ConfigDiffService configDiffService;

    @Autowired
    private NodeMapper nodeMapper;

    @Autowired
    private NodeConfigSnapshotMapper snapshotMapper;

    @Autowired
    private ConfigDistributeRecordMapper distributeRecordMapper;

    @Autowired
    private ProjectMapper projectMapper;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private SecurityContext securityContext;

    /**
     * 配置分发线程池，避免串行分发导致长时间阻塞
     */
    private static final ExecutorService DISTRIBUTE_EXECUTOR = Executors.newFixedThreadPool(
            Math.min(Runtime.getRuntime().availableProcessors() * 2, 16));

    /**
     * 分发配置到目标节点
     */
    public Map<String, Object> distribute(Long projectId, Long configFileId, String content,
                                          List<Long> targetNodeIds, String distributeType,
                                          boolean restartAfter, Long operatorId,
                                          ProjectConfigFileModel configFile) {
        String hash = configDiffService.sha256(content);
        String configPath = resolveConfigPath(projectMapper.findById(projectId), configFile);
        Long tenantId = securityContext.getCurrentTenantId();

        ConfigDistributeRecordModel record = new ConfigDistributeRecordModel();
        record.setProjectId(projectId);
        record.setTenantId(tenantId);
        record.setConfigFileId(configFileId);
        record.setOperatorId(operatorId != null ? operatorId : 0L);
        record.setTargetNodeIds(joinIds(targetNodeIds));
        record.setDistributeType(distributeType);
        record.setContentHash(hash);
        record.setRestartAfter(restartAfter ? 1 : 0);
        record.setStatus(0);
        record.setCreateTime(System.currentTimeMillis());
        distributeRecordMapper.insert(record);

        List<Map<String, Object>> results = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);

        // 并行分发到所有目标节点，避免串行阻塞
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (Long nodeId : targetNodeIds) {
            futures.add(CompletableFuture.runAsync(() -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("nodeId", nodeId);
                NodeModel node = nodeMapper.findById(nodeId);
                if (node == null || node.getStatus() == null || node.getStatus() != 1) {
                    item.put("success", false);
                    item.put("error", "节点不存在或离线");
                    synchronized (results) { results.add(item); }
                    return;
                }
                try {
                    Map<String, Object> body = new HashMap<>();
                    body.put("configPath", configPath);
                    body.put("content", content);
                    body.put("backup", true);
                    agentClient.postForMap(node, "/file/config", body);
                    upsertSnapshot(projectId, nodeId, configFileId, content, hash, 1, tenantId);
                    item.put("restarted", restartAfter);
                    if (restartAfter) {
                        try {
                            restartProject(projectId, node);
                            item.put("restartSuccess", true);
                        } catch (Exception re) {
                            item.put("restartSuccess", false);
                            item.put("restartError", re.getMessage());
                        }
                    }
                    item.put("success", true);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    item.put("success", false);
                    item.put("error", e.getMessage());
                }
                synchronized (results) { results.add(item); }
            }, DISTRIBUTE_EXECUTOR));
        }

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(60, TimeUnit.SECONDS);
        } catch (Exception e) {
            // 超时或异常，记录已完成的节点结果
        }

        int status = successCount.get() == targetNodeIds.size() ? 1
                : successCount.get() == 0 ? 3 : 2;
        String detail = JSON.toJSONString(results);
        distributeRecordMapper.updateStatus(record.getId(), status, detail, tenantId);

        Map<String, Object> data = new HashMap<>();
        data.put("recordId", record.getId());
        data.put("status", status);
        data.put("results", results);
        return data;
    }

    private void upsertSnapshot(Long projectId, Long nodeId, Long configFileId,
                                String content, String hash, int syncStatus, Long tenantId) {
        long now = System.currentTimeMillis();
        NodeConfigSnapshotModel existing = snapshotMapper.findByNodeAndFile(nodeId, configFileId);
        if (existing == null) {
            NodeConfigSnapshotModel snap = new NodeConfigSnapshotModel();
            snap.setProjectId(projectId);
            snap.setTenantId(tenantId);
            snap.setNodeId(nodeId);
            snap.setConfigFileId(configFileId);
            snap.setContentHash(hash);
            snap.setContentSize(content != null ? content.getBytes().length : 0);
            snap.setSyncStatus(syncStatus);
            snap.setLastSyncTime(now);
            snap.setUpdateTime(now);
            snapshotMapper.insert(snap);
        } else {
            existing.setContentHash(hash);
            existing.setContentSize(content != null ? content.getBytes().length : 0);
            existing.setSyncStatus(syncStatus);
            existing.setLastSyncTime(now);
            existing.setUpdateTime(now);
            snapshotMapper.update(existing);
        }
    }

    private void restartProject(Long projectId, NodeModel node) {
        ProjectModel project = projectMapper.findById(projectId);
        if (project == null) return;
        String url = agentClient.getAgentBase(node) + "/process/" + projectId + "/restart";
        Map<String, String> body = new HashMap<>();
        body.put("startScript", project.getStartScript() != null ? project.getStartScript() : "");
        body.put("stopScript", project.getStopScript() != null ? project.getStopScript() : "");
        body.put("deployDir", project.getDeployDir() != null ? project.getDeployDir() : "");
        restTemplate.postForObject(url, body, Map.class);
    }

    static String resolveConfigPath(ProjectModel project, ProjectConfigFileModel file) {
        if (project == null || file == null) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "项目或配置文件不存在");
        }
        String deployDir = project.getDeployDir();
        if (deployDir == null || deployDir.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "项目部署目录未配置");
        }
        String relative = file.getRelativePath();
        if (relative.startsWith("/")) {
            return deployDir + relative;
        }
        return deployDir.endsWith("/") ? deployDir + relative : deployDir + "/" + relative;
    }

    private String joinIds(List<Long> ids) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(ids.get(i));
        }
        return sb.toString();
    }
}
