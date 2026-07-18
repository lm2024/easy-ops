package com.ops.server.configmgmt.service;

import com.ops.common.constant.ErrorCode;
import com.ops.common.exception.BusinessException;
import com.ops.common.model.NodeConfigSnapshotModel;
import com.ops.common.model.NodeModel;
import com.ops.common.model.ProjectConfigFileModel;
import com.ops.common.model.ProjectModel;
import com.ops.server.client.AgentClient;
import com.ops.server.mapper.NodeConfigSnapshotMapper;
import com.ops.server.mapper.NodeMapper;
import com.ops.server.mapper.ProjectConfigFileMapper;
import com.ops.server.mapper.ProjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 配置文件管理服务
 */
@Service
public class ConfigMgmtService {

    private static final int FETCH_TIMEOUT_SEC = 10;
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(10);

    @Autowired
    private ProjectConfigFileMapper configFileMapper;

    @Autowired
    private NodeConfigSnapshotMapper snapshotMapper;

    @Autowired
    private NodeMapper nodeMapper;

    @Autowired
    private ProjectMapper projectMapper;

    @Autowired
    private AgentClient agentClient;

    @Autowired
    private ConfigDiffService configDiffService;

    @Autowired
    private ConfigDistributeService distributeService;

    public List<ProjectConfigFileModel> listFiles(Long projectId) {
        return configFileMapper.findByProjectId(projectId);
    }

    public ProjectConfigFileModel createFile(ProjectConfigFileModel model) {
        long now = System.currentTimeMillis();
        model.setCreateTime(now);
        model.setUpdateTime(now);
        if (model.getIsPrimary() == null) {
            model.setIsPrimary(0);
        }
        configFileMapper.insert(model);
        return model;
    }

    public ProjectConfigFileModel updateFile(ProjectConfigFileModel model) {
        model.setUpdateTime(System.currentTimeMillis());
        configFileMapper.update(model);
        return model;
    }

    public void deleteFile(Long id) {
        configFileMapper.deleteById(id);
    }

    public ProjectConfigFileModel getFile(Long id) {
        return configFileMapper.findById(id);
    }

    public Map<String, Object> getSnapshot(Long projectId, Long configFileId) {
        ProjectConfigFileModel configFile = requireFile(configFileId, projectId);
        List<NodeConfigSnapshotModel> snapshots = snapshotMapper.findByProjectAndFile(projectId, configFileId);
        Map<Long, NodeConfigSnapshotModel> snapMap = new HashMap<>();
        for (NodeConfigSnapshotModel s : snapshots) {
            snapMap.put(s.getNodeId(), s);
        }

        List<Map<String, Object>> nodes = new ArrayList<>();
        Set<String> hashes = new HashSet<>();
        for (Long nodeId : parseNodeIds(projectMapper.findById(projectId))) {
            NodeModel node = nodeMapper.findById(nodeId);
            if (node == null) {
                continue;
            }
            NodeConfigSnapshotModel snap = snapMap.get(nodeId);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("nodeId", nodeId);
            item.put("nodeName", node.getName());
            if (snap != null) {
                item.put("contentHash", snap.getContentHash());
                item.put("syncStatus", snap.getSyncStatus());
                item.put("syncStatusLabel", syncLabel(snap.getSyncStatus()));
                item.put("lastSyncTime", snap.getLastSyncTime());
                hashes.add(snap.getContentHash());
            } else {
                item.put("contentHash", "");
                item.put("syncStatus", 0);
                item.put("syncStatusLabel", syncLabel(0));
            }
            nodes.add(item);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("configFile", configFile);
        data.put("nodes", nodes);
        data.put("allSame", hashes.size() <= 1 && !nodes.isEmpty());
        return data;
    }

    public String getContent(Long projectId, Long nodeId, Long configFileId) {
        ProjectConfigFileModel file = requireFile(configFileId, projectId);
        ProjectModel project = requireProject(projectId);
        NodeModel node = requireOnlineNode(nodeId);
        String configPath = ConfigDistributeService.resolveConfigPath(project, file);
        Map<String, String> params = new HashMap<>();
        params.put("configPath", configPath);
        return agentClient.extractDataString(agentClient.getForMap(node, "/file/config", params));
    }

    /**
     * 自动选第一个在线节点读取配置内容（不需要指定 nodeId）
     * 返回 { content, nodeId, nodeName } 或抛异常"所有节点离线"
     */
    public Map<String, Object> getContentAuto(Long projectId, Long configFileId) {
        ProjectConfigFileModel file = requireFile(configFileId, projectId);
        ProjectModel project = requireProject(projectId);
        String configPath = ConfigDistributeService.resolveConfigPath(project, file);

        // 遍历项目节点，找第一个在线的
        for (Long nodeId : parseNodeIds(project)) {
            NodeModel node = nodeMapper.findById(nodeId);
            if (node == null || node.getStatus() == null || node.getStatus() != 1) {
                continue;
            }
            try {
                Map<String, String> params = new HashMap<>();
                params.put("configPath", configPath);
                String content = agentClient.extractDataString(agentClient.getForMap(node, "/file/config", params));
                Map<String, Object> result = new HashMap<>();
                result.put("content", content != null ? content : "");
                result.put("nodeId", node.getId());
                result.put("nodeName", node.getName());
                result.put("nodeIp", node.getIp());
                return result;
            } catch (Exception e) {
                // 该节点读取失败，尝试下一个
                continue;
            }
        }
        throw new BusinessException(1002, "所有节点离线或均无法读取配置文件");
    }

    public Map<String, Object> compare(Long projectId, Long configFileId,
                                       Long baseNodeId, List<Long> targetNodeIds) {
        ProjectConfigFileModel file = requireFile(configFileId, projectId);
        String baseContent = getContent(projectId, baseNodeId, configFileId);
        NodeModel baseNode = nodeMapper.findById(baseNodeId);
        String baseLabel = baseNode != null ? baseNode.getName() : "base";

        List<Map<String, Object>> diffs = new ArrayList<>();
        for (Long targetId : targetNodeIds) {
            if (targetId.equals(baseNodeId)) {
                continue;
            }
            try {
                String targetContent = getContent(projectId, targetId, configFileId);
                NodeModel targetNode = nodeMapper.findById(targetId);
                String targetLabel = targetNode != null ? targetNode.getName() : "node-" + targetId;
                Map<String, Object> diffItem = new LinkedHashMap<>();
                diffItem.put("nodeId", targetId);
                diffItem.put("unifiedDiff", configDiffService.unifiedDiff(
                        baseContent, targetContent, baseLabel, targetLabel));
                diffs.add(diffItem);
            } catch (Exception e) {
                Map<String, Object> diffItem = new LinkedHashMap<>();
                diffItem.put("nodeId", targetId);
                diffItem.put("error", e.getMessage());
                diffs.add(diffItem);
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put("baseNodeId", baseNodeId);
        data.put("diffs", diffs);
        return data;
    }

    public Map<String, Object> refreshSnapshots(Long projectId, Long configFileId) {
        ProjectConfigFileModel file = requireFile(configFileId, projectId);
        ProjectModel project = requireProject(projectId);
        String configPath = ConfigDistributeService.resolveConfigPath(project, file);

        List<Long> nodeIds = parseNodeIds(project);
        Map<Long, String> nodeHashes = fetchNodeHashes(nodeIds, configPath);
        String referenceHash = null;
        for (String hash : nodeHashes.values()) {
            if (referenceHash == null) {
                referenceHash = hash;
            } else if (!referenceHash.equals(hash)) {
                referenceHash = null;
                break;
            }
        }
        boolean allSame = referenceHash != null && !nodeHashes.isEmpty();
        long now = System.currentTimeMillis();

        for (Long nodeId : nodeIds) {
            String hash = nodeHashes.get(nodeId);
            if (hash == null) {
                continue;
            }
            int syncStatus = allSame ? 1 : 2;
            upsertSnapshot(projectId, nodeId, configFileId, hash, 0, syncStatus, now);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("refreshed", nodeHashes.size());
        data.put("allSame", allSame);
        return data;
    }

    public Map<String, Object> distribute(Long projectId, Long configFileId, String content,
                                          List<Long> targetNodeIds, String distributeType,
                                          boolean restartAfter, Long operatorId) {
        ProjectConfigFileModel file = requireFile(configFileId, projectId);
        if (content == null || content.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "配置内容不能为空");
        }
        return distributeService.distribute(projectId, configFileId, content, targetNodeIds,
                distributeType, restartAfter, operatorId, file);
    }

    private Map<Long, String> fetchNodeHashes(List<Long> nodeIds, String configPath) {
        Map<Long, String> result = new HashMap<>();
        List<Future<Map.Entry<Long, String>>> futures = new ArrayList<>();
        for (Long nodeId : nodeIds) {
            futures.add(EXECUTOR.submit(new FetchHashTask(nodeId, configPath)));
        }
        for (Future<Map.Entry<Long, String>> future : futures) {
            try {
                Map.Entry<Long, String> entry = future.get(FETCH_TIMEOUT_SEC, TimeUnit.SECONDS);
                if (entry != null && entry.getValue() != null) {
                    result.put(entry.getKey(), entry.getValue());
                }
            } catch (Exception ignored) {
                // 单节点失败不影响其他节点
            }
        }
        return result;
    }

    private class FetchHashTask implements Callable<Map.Entry<Long, String>> {
        private final Long nodeId;
        private final String configPath;

        FetchHashTask(Long nodeId, String configPath) {
            this.nodeId = nodeId;
            this.configPath = configPath;
        }

        @Override
        public Map.Entry<Long, String> call() {
            NodeModel node = nodeMapper.findById(nodeId);
            if (node == null || node.getStatus() == null || node.getStatus() != 1) {
                return null;
            }
            Map<String, String> params = new HashMap<>();
            params.put("configPath", configPath);
            String content = agentClient.extractDataString(agentClient.getForMap(node, "/file/config", params));
            return new java.util.AbstractMap.SimpleEntry<>(nodeId, configDiffService.sha256(content));
        }
    }

    private void upsertSnapshot(Long projectId, Long nodeId, Long configFileId,
                                String hash, int contentSize, int syncStatus, long now) {
        NodeConfigSnapshotModel existing = snapshotMapper.findByNodeAndFile(nodeId, configFileId);
        if (existing == null) {
            NodeConfigSnapshotModel snap = new NodeConfigSnapshotModel();
            snap.setProjectId(projectId);
            snap.setNodeId(nodeId);
            snap.setConfigFileId(configFileId);
            snap.setContentHash(hash);
            snap.setContentSize(contentSize);
            snap.setSyncStatus(syncStatus);
            snap.setLastSyncTime(now);
            snap.setUpdateTime(now);
            snapshotMapper.insert(snap);
        } else {
            existing.setContentHash(hash);
            existing.setContentSize(contentSize);
            existing.setSyncStatus(syncStatus);
            existing.setLastSyncTime(now);
            existing.setUpdateTime(now);
            snapshotMapper.update(existing);
        }
    }

    private ProjectConfigFileModel requireFile(Long configFileId, Long projectId) {
        ProjectConfigFileModel file = configFileMapper.findById(configFileId);
        if (file == null || !projectId.equals(file.getProjectId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "配置文件不存在");
        }
        return file;
    }

    private ProjectModel requireProject(Long projectId) {
        ProjectModel project = projectMapper.findById(projectId);
        if (project == null) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "项目不存在");
        }
        return project;
    }

    private NodeModel requireOnlineNode(Long nodeId) {
        NodeModel node = nodeMapper.findById(nodeId);
        if (node == null || node.getStatus() == null || node.getStatus() != 1) {
            throw new BusinessException(1002, "节点不存在或离线");
        }
        return node;
    }

    private List<Long> parseNodeIds(ProjectModel project) {
        List<Long> ids = new ArrayList<>();
        if (project == null || project.getNodeIds() == null || project.getNodeIds().isEmpty()) {
            return ids;
        }
        for (String part : project.getNodeIds().split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                ids.add(Long.parseLong(trimmed));
            }
        }
        return ids;
    }

    private String syncLabel(Integer status) {
        if (status == null || status == 0) {
            return "未知";
        }
        switch (status) {
            case 1: return "一致";
            case 2: return "差异";
            case 3: return "定制";
            default: return "未知";
        }
    }
}
