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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(ConfigMgmtService.class);

    private static final int FETCH_TIMEOUT_SEC = 10;
    private static final int SCAN_TIMEOUT_SEC = 15; // 单节点扫描超时
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(20); // 增加线程池大小

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

    /**
     * 扫描项目所有在线节点的 config/ 目录，发现未注册的配置文件并自动导入。
     * 使用并行扫描优化性能，支持 100+ 节点场景。
     */
    public List<ProjectConfigFileModel> scanAndImport(Long projectId) {
        ProjectModel project = requireProject(projectId);
        List<Long> nodeIds = parseNodeIds(project);
        if (nodeIds.isEmpty()) {
            log.info("[ConfigScan] 项目 {} 无关联节点，返回已有记录", projectId);
            return configFileMapper.findByProjectId(projectId);
        }
        String deployDir = project.getDeployDir();
        if (deployDir == null || deployDir.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "项目未配置部署目录，无法扫描");
        }

        // 收集所有在线节点的扫描结果（去重）
        Map<String, ProjectConfigFileModel> discovered = new LinkedHashMap<>();
        int onlineCount = 0;
        int successCount = 0;

        // 预加载所有节点信息，避免循环中重复查询数据库
        Map<Long, NodeModel> nodeMap = new HashMap<>();
        for (Long nodeId : nodeIds) {
            NodeModel node = nodeMapper.findById(nodeId);
            if (node != null) {
                nodeMap.put(nodeId, node);
            }
        }

        // 筛选在线节点
        List<Long> onlineNodeIds = new ArrayList<>();
        for (Long nodeId : nodeIds) {
            NodeModel node = nodeMap.get(nodeId);
            if (node != null && Integer.valueOf(1).equals(node.getStatus())) {
                onlineNodeIds.add(nodeId);
                onlineCount++;
            } else {
                log.debug("[ConfigScan] 节点 {} 离线或不存在，跳过", nodeId);
            }
        }

        if (onlineNodeIds.isEmpty()) {
            log.info("[ConfigScan] 项目 {} 无在线节点，返回已有记录", projectId);
            return configFileMapper.findByProjectId(projectId);
        }

        // 并行扫描所有在线节点
        log.info("[ConfigScan] 项目 {} 开始并行扫描: 在线节点数={}", projectId, onlineCount);
        long startTime = System.currentTimeMillis();

        // 使用线程池并行扫描，限制并发数避免过载
        List<Future<Map<Long, List<Map<String, Object>>>>> futures = new ArrayList<>();
        int batchSize = Math.min(onlineNodeIds.size(), 20); // 每批最多 20 个节点

        for (int i = 0; i < onlineNodeIds.size(); i += batchSize) {
            List<Long> batch = onlineNodeIds.subList(i, Math.min(i + batchSize, onlineNodeIds.size()));
            futures.add(EXECUTOR.submit(new ScanBatchTask(batch, nodeMap, deployDir)));
        }

        // 收集结果
        for (Future<Map<Long, List<Map<String, Object>>>> future : futures) {
            try {
                Map<Long, List<Map<String, Object>>> batchResult = future.get(SCAN_TIMEOUT_SEC, TimeUnit.SECONDS);
                if (batchResult != null) {
                    for (Map.Entry<Long, List<Map<String, Object>>> entry : batchResult.entrySet()) {
                        Long nodeId = entry.getKey();
                        List<Map<String, Object>> files = entry.getValue();
                        if (files != null && !files.isEmpty()) {
                            log.info("[ConfigScan] 节点 {} 发现 {} 个配置文件", nodeId, files.size());
                            for (Map<String, Object> f : files) {
                                String relativePath = f.get("relativePath") != null ? f.get("relativePath").toString() : "";
                                if (relativePath.isEmpty()) {
                                    log.debug("[ConfigScan] 跳过空 relativePath 的文件: {}", f);
                                    continue;
                                }
                                String fileKey = relativePath;
                                if (!discovered.containsKey(fileKey)) {
                                    ProjectConfigFileModel model = new ProjectConfigFileModel();
                                    model.setProjectId(projectId);
                                    model.setFileName(f.get("fileName") != null ? f.get("fileName").toString() : "");
                                    model.setRelativePath(relativePath);
                                    model.setIsPrimary(0);
                                    discovered.put(fileKey, model);
                                }
                            }
                            successCount++;
                        }
                    }
                }
            } catch (Exception e) {
                log.error("[ConfigScan] 批量扫描任务失败: {}", e.getMessage(), e);
            }
        }

        long scanTime = System.currentTimeMillis() - startTime;
        log.info("[ConfigScan] 项目 {} 扫描完成: 在线节点数={}, 成功节点数={}, 发现文件数={}, 耗时={}ms",
                projectId, onlineCount, successCount, discovered.size(), scanTime);

        // 查询已有 DB 记录
        List<ProjectConfigFileModel> existing = configFileMapper.findByProjectId(projectId);
        Set<String> existingPaths = new HashSet<>();
        for (ProjectConfigFileModel e : existing) {
            existingPaths.add(e.getRelativePath());
        }

        // 自动导入新发现的文件
        List<ProjectConfigFileModel> imported = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (ProjectConfigFileModel model : discovered.values()) {
            if (!existingPaths.contains(model.getRelativePath())) {
                model.setCreateTime(now);
                model.setUpdateTime(now);
                configFileMapper.insert(model);
                imported.add(model);
                log.info("[ConfigScan] 自动导入配置文件: projectId={}, path={}", projectId, model.getRelativePath());
            }
        }

        log.info("[ConfigScan] 项目 {} 导入完成: 新导入 {} 个文件", projectId, imported.size());

        // 返回完整列表
        return configFileMapper.findByProjectId(projectId);
    }

    /**
     * 批量扫描任务：并行扫描一批节点
     */
    private class ScanBatchTask implements Callable<Map<Long, List<Map<String, Object>>>> {
        private final List<Long> nodeIds;
        private final Map<Long, NodeModel> nodeMap;
        private final String deployDir;

        ScanBatchTask(List<Long> nodeIds, Map<Long, NodeModel> nodeMap, String deployDir) {
            this.nodeIds = nodeIds;
            this.nodeMap = nodeMap;
            this.deployDir = deployDir;
        }

        @Override
        public Map<Long, List<Map<String, Object>>> call() {
            Map<Long, List<Map<String, Object>>> result = new HashMap<>();
            List<Future<Map.Entry<Long, List<Map<String, Object>>>>> futures = new ArrayList<>();

            for (Long nodeId : nodeIds) {
                futures.add(EXECUTOR.submit(new ScanNodeTask(nodeId, nodeMap.get(nodeId), deployDir)));
            }

            for (Future<Map.Entry<Long, List<Map<String, Object>>>> future : futures) {
                try {
                    Map.Entry<Long, List<Map<String, Object>>> entry = future.get(SCAN_TIMEOUT_SEC, TimeUnit.SECONDS);
                    if (entry != null && entry.getValue() != null) {
                        result.put(entry.getKey(), entry.getValue());
                    }
                } catch (Exception e) {
                    log.warn("[ConfigScan] 节点扫描任务超时或失败: {}", e.getMessage());
                }
            }
            return result;
        }
    }

    /**
     * 单节点扫描任务
     */
    private class ScanNodeTask implements Callable<Map.Entry<Long, List<Map<String, Object>>>> {
        private final Long nodeId;
        private final NodeModel node;
        private final String deployDir;

        ScanNodeTask(Long nodeId, NodeModel node, String deployDir) {
            this.nodeId = nodeId;
            this.node = node;
            this.deployDir = deployDir;
        }

        @Override
        public Map.Entry<Long, List<Map<String, Object>>> call() {
            try {
                Map<String, String> params = new HashMap<>();
                params.put("deployDir", deployDir);
                log.debug("[ConfigScan] 请求节点 {} 扫描配置文件: deployDir={}", nodeId, deployDir);
                Map<String, Object> raw = agentClient.getForMap(node, "/file/config/discover", params);
                if (raw == null) {
                    log.warn("[ConfigScan] 节点 {} 返回空响应", nodeId);
                    return null;
                }
                // 检查 Agent 返回的 Result 包装
                Object codeObj = raw.get("code");
                if (codeObj instanceof Number && ((Number) codeObj).intValue() != 200) {
                    Object message = raw.get("message");
                    log.warn("[ConfigScan] 节点 {} 返回错误: code={}, message={}", nodeId, codeObj, message);
                    return null;
                }
                Object dataObj = raw.get("data");
                if (!(dataObj instanceof List)) {
                    log.warn("[ConfigScan] 节点 {} 返回数据格式异常: data 类型={}", nodeId,
                            dataObj != null ? dataObj.getClass().getSimpleName() : "null");
                    return null;
                }
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> files = (List<Map<String, Object>>) dataObj;
                return new java.util.AbstractMap.SimpleEntry<>(nodeId, files);
            } catch (Exception e) {
                log.error("[ConfigScan] 节点 {} 扫描失败: {}", nodeId, e.getMessage(), e);
                return null;
            }
        }
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
