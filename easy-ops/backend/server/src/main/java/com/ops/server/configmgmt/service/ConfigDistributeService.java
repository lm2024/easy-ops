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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * 分发配置到目标节点
     */
    public Map<String, Object> distribute(Long projectId, Long configFileId, String content,
                                          List<Long> targetNodeIds, String distributeType,
                                          boolean restartAfter, Long operatorId,
                                          ProjectConfigFileModel configFile) {
        String hash = configDiffService.sha256(content);
        String configPath = resolveConfigPath(projectMapper.findById(projectId), configFile);

        ConfigDistributeRecordModel record = new ConfigDistributeRecordModel();
        record.setProjectId(projectId);
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
        int successCount = 0;
        for (Long nodeId : targetNodeIds) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("nodeId", nodeId);
            NodeModel node = nodeMapper.findById(nodeId);
            if (node == null || node.getStatus() == null || node.getStatus() != 1) {
                item.put("success", false);
                item.put("error", "节点不存在或离线");
                results.add(item);
                continue;
            }
            try {
                Map<String, Object> body = new HashMap<>();
                body.put("configPath", configPath);
                body.put("content", content);
                body.put("backup", true);
                agentClient.postForMap(node, "/file/config", body);
                upsertSnapshot(projectId, nodeId, configFileId, content, hash, 1);
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
                successCount++;
            } catch (Exception e) {
                item.put("success", false);
                item.put("error", e.getMessage());
            }
            results.add(item);
        }

        int status = successCount == targetNodeIds.size() ? 1
                : successCount == 0 ? 3 : 2;
        String detail = JSON.toJSONString(results);
        distributeRecordMapper.updateStatus(record.getId(), status, detail);

        Map<String, Object> data = new HashMap<>();
        data.put("recordId", record.getId());
        data.put("status", status);
        data.put("results", results);
        return data;
    }

    private void upsertSnapshot(Long projectId, Long nodeId, Long configFileId,
                                String content, String hash, int syncStatus) {
        long now = System.currentTimeMillis();
        NodeConfigSnapshotModel existing = snapshotMapper.findByNodeAndFile(nodeId, configFileId);
        if (existing == null) {
            NodeConfigSnapshotModel snap = new NodeConfigSnapshotModel();
            snap.setProjectId(projectId);
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
        String url = agentClient.getAgentBase(node) + "/process/" + projectId + "/restart";
        restTemplate.postForObject(url, null, Map.class);
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
