package com.ops.server.configmgmt.service;

import com.ops.common.constant.ErrorCode;
import com.ops.common.exception.BusinessException;
import com.ops.common.model.NodeModel;
import com.ops.common.model.NodeScriptSnapshotModel;
import com.ops.common.model.ProjectModel;
import com.ops.common.model.ProjectScriptFileModel;
import com.ops.common.model.ScriptDistributeRecordModel;
import com.ops.server.client.AgentClient;
import com.ops.server.mapper.NodeMapper;
import com.ops.server.mapper.NodeScriptSnapshotMapper;
import com.ops.server.mapper.ProjectMapper;
import com.ops.server.mapper.ProjectScriptFileMapper;
import com.ops.server.mapper.ScriptDistributeRecordMapper;
import com.ops.server.util.SecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * 脚本文件管理服务
 * 支持任意目录的脚本/配置文件管理，性能优化支持 100+ 节点
 */
@Service
public class ScriptMgmtService {

    private static final Logger log = LoggerFactory.getLogger(ScriptMgmtService.class);

    private static final int SCAN_TIMEOUT_SEC = 15;
    private static final int DISTRIBUTE_TIMEOUT_SEC = 30;
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(20);

    @Autowired
    private ProjectScriptFileMapper scriptFileMapper;

    @Autowired
    private NodeScriptSnapshotMapper snapshotMapper;

    @Autowired
    private NodeMapper nodeMapper;

    @Autowired
    private ProjectMapper projectMapper;

    @Autowired
    private AgentClient agentClient;

    @Autowired
    private ScriptDistributeRecordMapper distributeRecordMapper;

    @Autowired
    private SecurityContext securityContext;

    /**
     * 查询项目脚本文件列表
     */
    public List<ProjectScriptFileModel> listFiles(Long projectId) {
        return scriptFileMapper.findByProjectId(projectId);
    }

    /**
     * 创建脚本文件定义
     */
    public ProjectScriptFileModel createFile(ProjectScriptFileModel model) {
        // 检查是否已存在
        ProjectScriptFileModel existing = scriptFileMapper.findByProjectAndPath(model.getProjectId(), model.getFilePath());
        if (existing != null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "该路径的脚本文件已存在");
        }
        long now = System.currentTimeMillis();
        model.setCreateTime(now);
        model.setUpdateTime(now);
        if (model.getIsExecutable() == null) {
            model.setIsExecutable(0);
        }
        if (model.getAutoBackup() == null) {
            model.setAutoBackup(1);
        }
        scriptFileMapper.insert(model);
        return model;
    }

    /**
     * 更新脚本文件定义
     */
    public ProjectScriptFileModel updateFile(ProjectScriptFileModel model) {
        model.setUpdateTime(System.currentTimeMillis());
        scriptFileMapper.update(model);
        return model;
    }

    /**
     * 删除脚本文件定义
     */
    public void deleteFile(Long id) {
        // 同时删除快照记录
        snapshotMapper.deleteByScriptFileId(id, securityContext.getCurrentTenantId());
        scriptFileMapper.deleteById(id, securityContext.getCurrentTenantId());
    }

    /**
     * 获取脚本文件详情
     */
    public ProjectScriptFileModel getFile(Long id) {
        return scriptFileMapper.findById(id);
    }

    /**
     * 扫描指定目录下的脚本文件并导入（并行扫描优化）
     * 支持 100+ 节点场景
     */
    public List<ProjectScriptFileModel> scanAndImport(Long projectId, String scanDir) {
        ProjectModel project = requireProject(projectId);
        List<Long> nodeIds = parseNodeIds(project);
        if (nodeIds.isEmpty()) {
            log.info("[ScriptScan] 项目 {} 无关联节点，返回已有记录", projectId);
            return scriptFileMapper.findByProjectId(projectId);
        }

        // 收集所有在线节点的扫描结果（去重）
        Map<String, ProjectScriptFileModel> discovered = new LinkedHashMap<>();
        int onlineCount = 0;
        int successCount = 0;

        // 预加载所有节点信息
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
            }
        }

        if (onlineNodeIds.isEmpty()) {
            log.info("[ScriptScan] 项目 {} 无在线节点，返回已有记录", projectId);
            return scriptFileMapper.findByProjectId(projectId);
        }

        // 并行扫描所有在线节点
        log.info("[ScriptScan] 项目 {} 开始并行扫描: scanDir={}, 在线节点数={}", projectId, scanDir, onlineCount);
        long startTime = System.currentTimeMillis();

        // 分批提交扫描任务
        List<Future<Map<Long, List<Map<String, Object>>>>> futures = new ArrayList<>();
        int batchSize = Math.min(onlineNodeIds.size(), 20);

        for (int i = 0; i < onlineNodeIds.size(); i += batchSize) {
            List<Long> batch = onlineNodeIds.subList(i, Math.min(i + batchSize, onlineNodeIds.size()));
            futures.add(EXECUTOR.submit(new ScanScriptBatchTask(batch, nodeMap, scanDir)));
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
                            log.info("[ScriptScan] 节点 {} 发现 {} 个脚本文件", nodeId, files.size());
                            for (Map<String, Object> f : files) {
                                String filePath = f.get("filePath") != null ? f.get("filePath").toString() : "";
                                if (filePath.isEmpty()) continue;

                                String fileKey = filePath;
                                if (!discovered.containsKey(fileKey)) {
                                    ProjectScriptFileModel model = new ProjectScriptFileModel();
                                    model.setProjectId(projectId);
                                    model.setTenantId(project.getTenantId());
                                    model.setFileName(f.get("fileName") != null ? f.get("fileName").toString() : "");
                                    model.setFilePath(filePath);
                                    model.setFileType(detectFileType(model.getFileName()));
                                    model.setIsExecutable(0);
                                    model.setAutoBackup(1);
                                    discovered.put(fileKey, model);
                                }
                            }
                            successCount++;
                        }
                    }
                }
            } catch (Exception e) {
                log.error("[ScriptScan] 批量扫描任务失败: {}", e.getMessage(), e);
            }
        }

        long scanTime = System.currentTimeMillis() - startTime;
        log.info("[ScriptScan] 项目 {} 扫描完成: 在线节点数={}, 成功节点数={}, 发现文件数={}, 耗时={}ms",
                projectId, onlineCount, successCount, discovered.size(), scanTime);

        // 查询已有 DB 记录
        List<ProjectScriptFileModel> existing = scriptFileMapper.findByProjectId(projectId);
        Set<String> existingPaths = new HashSet<>();
        for (ProjectScriptFileModel e : existing) {
            existingPaths.add(e.getFilePath());
        }

        // 自动导入新发现的文件
        List<ProjectScriptFileModel> imported = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (ProjectScriptFileModel model : discovered.values()) {
            if (!existingPaths.contains(model.getFilePath())) {
                model.setCreateTime(now);
                model.setUpdateTime(now);
                scriptFileMapper.insert(model);
                imported.add(model);
                log.info("[ScriptScan] 自动导入脚本文件: projectId={}, path={}", projectId, model.getFilePath());
            }
        }

        log.info("[ScriptScan] 项目 {} 导入完成: 新导入 {} 个文件", projectId, imported.size());
        return scriptFileMapper.findByProjectId(projectId);
    }

    /**
     * 获取各节点脚本快照
     */
    public Map<String, Object> getSnapshot(Long projectId, Long scriptFileId) {
        ProjectScriptFileModel scriptFile = requireFile(scriptFileId, projectId);
        List<NodeScriptSnapshotModel> snapshots = snapshotMapper.findByProjectAndFile(projectId, scriptFileId);
        Map<Long, NodeScriptSnapshotModel> snapMap = new HashMap<>();
        for (NodeScriptSnapshotModel s : snapshots) {
            snapMap.put(s.getNodeId(), s);
        }

        List<Map<String, Object>> nodes = new ArrayList<>();
        Set<String> hashes = new HashSet<>();
        for (Long nodeId : parseNodeIds(projectMapper.findById(projectId))) {
            NodeModel node = nodeMapper.findById(nodeId);
            if (node == null) continue;

            NodeScriptSnapshotModel snap = snapMap.get(nodeId);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("nodeId", nodeId);
            item.put("nodeName", node.getName());
            if (snap != null) {
                item.put("contentHash", snap.getContentHash());
                item.put("contentSize", snap.getContentSize());
                item.put("fileMode", snap.getFileMode());
                item.put("syncStatus", snap.getSyncStatus());
                item.put("syncStatusLabel", syncLabel(snap.getSyncStatus()));
                item.put("lastSyncTime", snap.getLastSyncTime());
                hashes.add(snap.getContentHash());
            } else {
                item.put("contentHash", "");
                item.put("contentSize", 0);
                item.put("fileMode", 0);
                item.put("syncStatus", 0);
                item.put("syncStatusLabel", syncLabel(0));
            }
            nodes.add(item);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("scriptFile", scriptFile);
        data.put("nodes", nodes);
        data.put("allSame", hashes.size() <= 1 && !nodes.isEmpty());
        return data;
    }

    /**
     * 自动选第一个在线节点读取脚本内容
     */
    public Map<String, Object> getContentAuto(Long projectId, Long scriptFileId) {
        ProjectScriptFileModel file = requireFile(scriptFileId, projectId);
        String filePath = resolveFilePath(file);

        for (Long nodeId : parseNodeIds(projectMapper.findById(projectId))) {
            NodeModel node = nodeMapper.findById(nodeId);
            if (node == null || node.getStatus() == null || node.getStatus() != 1) continue;
            try {
                Map<String, String> params = new HashMap<>();
                params.put("filePath", filePath);
                String content = agentClient.extractDataString(agentClient.getForMap(node, "/file/script", params));
                Map<String, Object> result = new HashMap<>();
                result.put("content", content != null ? content : "");
                result.put("nodeId", node.getId());
                result.put("nodeName", node.getName());
                result.put("nodeIp", node.getIp());
                return result;
            } catch (Exception e) {
                continue;
            }
        }
        throw new BusinessException(1002, "所有节点离线或均无法读取脚本文件");
    }

    /**
     * 从指定节点读取脚本内容
     */
    public String getContent(Long projectId, Long nodeId, Long scriptFileId) {
        ProjectScriptFileModel file = requireFile(scriptFileId, projectId);
        NodeModel node = requireOnlineNode(nodeId);
        String filePath = resolveFilePath(file);
        Map<String, String> params = new HashMap<>();
        params.put("filePath", filePath);
        return agentClient.extractDataString(agentClient.getForMap(node, "/file/script", params));
    }

    /**
     * 分发脚本文件到指定节点（并行分发优化）
     */
    public Map<String, Object> distribute(Long projectId, Long scriptFileId, String content,
                                          List<Long> targetNodeIds, boolean setExecutable,
                                          boolean autoBackup, Long operatorId) {
        ProjectScriptFileModel file = requireFile(scriptFileId, projectId);
        if (content == null || content.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "脚本内容不能为空");
        }

        String filePath = resolveFilePath(file);
        String hash = sha256(content);

        // 创建分发记录
        ScriptDistributeRecordModel record = new ScriptDistributeRecordModel();
        record.setProjectId(projectId);
        record.setTenantId(securityContext.getCurrentTenantId());
        record.setScriptFileId(scriptFileId);
        record.setOperatorId(operatorId);
        record.setTargetNodeIds(joinIds(targetNodeIds));
        record.setContentHash(hash);
        record.setSetExecutable(setExecutable ? 1 : 0);
        record.setAutoBackup(autoBackup ? 1 : 0);
        record.setStatus(0);
        record.setCreateTime(System.currentTimeMillis());
        distributeRecordMapper.insert(record);

        // 并行分发到目标节点
        List<Map<String, Object>> results = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;
        long now = System.currentTimeMillis();

        List<Future<Map<String, Object>>> futures = new ArrayList<>();
        for (Long nodeId : targetNodeIds) {
            futures.add(EXECUTOR.submit(new DistributeScriptTask(nodeId, filePath, content, setExecutable, autoBackup)));
        }

        for (Future<Map<String, Object>> future : futures) {
            try {
                Map<String, Object> nodeResult = future.get(DISTRIBUTE_TIMEOUT_SEC, TimeUnit.SECONDS);
                if (nodeResult != null) {
                    results.add(nodeResult);
                    if (Boolean.TRUE.equals(nodeResult.get("success"))) {
                        successCount++;
                        // 更新快照
                        Long nodeId = (Long) nodeResult.get("nodeId");
                        upsertSnapshot(projectId, nodeId, scriptFileId, hash, content.length(), setExecutable ? 755 : 644, 1, now, securityContext.getCurrentTenantId());
                    } else {
                        failCount++;
                    }
                }
            } catch (Exception e) {
                failCount++;
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("success", false);
                errorResult.put("error", e.getMessage());
                results.add(errorResult);
            }
        }

        // 更新分发记录状态
        int status = failCount == 0 ? 1 : (successCount == 0 ? 3 : 2);
        distributeRecordMapper.updateStatus(record.getId(), status, "成功=" + successCount + ", 失败=" + failCount, securityContext.getCurrentTenantId());

        Map<String, Object> data = new HashMap<>();
        data.put("recordId", record.getId());
        data.put("totalNodes", targetNodeIds.size());
        data.put("successCount", successCount);
        data.put("failCount", failCount);
        data.put("results", results);
        return data;
    }

    /**
     * 刷新所有节点快照哈希（并行优化）
     */
    public Map<String, Object> refreshSnapshots(Long projectId, Long scriptFileId) {
        ProjectScriptFileModel file = requireFile(scriptFileId, projectId);
        String filePath = resolveFilePath(file);

        List<Long> nodeIds = parseNodeIds(projectMapper.findById(projectId));
        Map<Long, String> nodeHashes = fetchNodeHashes(nodeIds, filePath);
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
            if (hash == null) continue;
            int syncStatus = allSame ? 1 : 2;
            upsertSnapshot(projectId, nodeId, scriptFileId, hash, 0, 0, syncStatus, now, securityContext.getCurrentTenantId());
        }

        Map<String, Object> data = new HashMap<>();
        data.put("refreshed", nodeHashes.size());
        data.put("allSame", allSame);
        return data;
    }

    // ==================== 内部方法 ====================

    private Map<Long, String> fetchNodeHashes(List<Long> nodeIds, String filePath) {
        Map<Long, String> result = new HashMap<>();
        List<Future<Map.Entry<Long, String>>> futures = new ArrayList<>();
        for (Long nodeId : nodeIds) {
            futures.add(EXECUTOR.submit(new FetchScriptHashTask(nodeId, filePath)));
        }
        for (Future<Map.Entry<Long, String>> future : futures) {
            try {
                Map.Entry<Long, String> entry = future.get(SCAN_TIMEOUT_SEC, TimeUnit.SECONDS);
                if (entry != null && entry.getValue() != null) {
                    result.put(entry.getKey(), entry.getValue());
                }
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    private void upsertSnapshot(Long projectId, Long nodeId, Long scriptFileId,
                                String hash, int contentSize, int fileMode, int syncStatus, long now, Long tenantId) {
        NodeScriptSnapshotModel existing = snapshotMapper.findByNodeAndFile(nodeId, scriptFileId);
        if (existing == null) {
            NodeScriptSnapshotModel snap = new NodeScriptSnapshotModel();
            snap.setProjectId(projectId);
            snap.setTenantId(tenantId);
            snap.setNodeId(nodeId);
            snap.setScriptFileId(scriptFileId);
            snap.setContentHash(hash);
            snap.setContentSize((long) contentSize);
            snap.setFileMode(fileMode);
            snap.setSyncStatus(syncStatus);
            snap.setLastSyncTime(now);
            snap.setUpdateTime(now);
            snapshotMapper.insert(snap);
        } else {
            existing.setContentHash(hash);
            existing.setContentSize((long) contentSize);
            existing.setFileMode(fileMode);
            existing.setSyncStatus(syncStatus);
            existing.setLastSyncTime(now);
            existing.setUpdateTime(now);
            snapshotMapper.update(existing);
        }
    }

    private String resolveFilePath(ProjectScriptFileModel file) {
        String filePath = file.getFilePath();
        // 如果是相对路径，需要结合项目部署目录
        if (!filePath.startsWith("/") && !filePath.startsWith("\\") && !filePath.contains(":\\")) {
            ProjectModel project = projectMapper.findById(file.getProjectId());
            if (project != null && project.getDeployDir() != null && !project.getDeployDir().isEmpty()) {
                filePath = project.getDeployDir() + "/" + filePath;
            }
        }
        return filePath;
    }

    private String detectFileType(String fileName) {
        if (fileName == null) return "other";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".sh") || lower.endsWith(".bash")) return "sh";
        if (lower.endsWith(".conf") || lower.endsWith(".cfg")) return "conf";
        if (lower.endsWith(".yml") || lower.endsWith(".yaml")) return "yaml";
        if (lower.endsWith(".properties")) return "properties";
        if (lower.endsWith(".service")) return "service";
        if (lower.endsWith(".cron") || lower.endsWith(".crontab")) return "cron";
        if (lower.endsWith(".xml")) return "xml";
        if (lower.endsWith(".json")) return "json";
        if (lower.endsWith(".py")) return "python";
        if (lower.endsWith(".js")) return "javascript";
        return "other";
    }

    private String syncLabel(Integer status) {
        if (status == null || status == 0) return "未知";
        switch (status) {
            case 1: return "一致";
            case 2: return "差异";
            case 3: return "定制";
            default: return "未知";
        }
    }

    private ProjectScriptFileModel requireFile(Long scriptFileId, Long projectId) {
        ProjectScriptFileModel file = scriptFileMapper.findById(scriptFileId);
        if (file == null || !projectId.equals(file.getProjectId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "脚本文件不存在");
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

    private String joinIds(List<Long> ids) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(ids.get(i));
        }
        return sb.toString();
    }

    private String sha256(String content) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    // ==================== 内部任务类 ====================

    /**
     * 批量扫描脚本文件任务
     */
    private class ScanScriptBatchTask implements Callable<Map<Long, List<Map<String, Object>>>> {
        private final List<Long> nodeIds;
        private final Map<Long, NodeModel> nodeMap;
        private final String scanDir;

        ScanScriptBatchTask(List<Long> nodeIds, Map<Long, NodeModel> nodeMap, String scanDir) {
            this.nodeIds = nodeIds;
            this.nodeMap = nodeMap;
            this.scanDir = scanDir;
        }

        @Override
        public Map<Long, List<Map<String, Object>>> call() {
            Map<Long, List<Map<String, Object>>> result = new HashMap<>();
            List<Future<Map.Entry<Long, List<Map<String, Object>>>>> futures = new ArrayList<>();

            for (Long nodeId : nodeIds) {
                futures.add(EXECUTOR.submit(new ScanScriptNodeTask(nodeId, nodeMap.get(nodeId), scanDir)));
            }

            for (Future<Map.Entry<Long, List<Map<String, Object>>>> future : futures) {
                try {
                    Map.Entry<Long, List<Map<String, Object>>> entry = future.get(SCAN_TIMEOUT_SEC, TimeUnit.SECONDS);
                    if (entry != null && entry.getValue() != null) {
                        result.put(entry.getKey(), entry.getValue());
                    }
                } catch (Exception e) {
                    log.warn("[ScriptScan] 节点扫描任务超时或失败: {}", e.getMessage());
                }
            }
            return result;
        }
    }

    /**
     * 单节点扫描脚本文件任务
     */
    private class ScanScriptNodeTask implements Callable<Map.Entry<Long, List<Map<String, Object>>>> {
        private final Long nodeId;
        private final NodeModel node;
        private final String scanDir;

        ScanScriptNodeTask(Long nodeId, NodeModel node, String scanDir) {
            this.nodeId = nodeId;
            this.node = node;
            this.scanDir = scanDir;
        }

        @Override
        public Map.Entry<Long, List<Map<String, Object>>> call() {
            try {
                Map<String, String> params = new HashMap<>();
                params.put("scanDir", scanDir);
                log.debug("[ScriptScan] 请求节点 {} 扫描脚本文件: scanDir={}", nodeId, scanDir);
                Map<String, Object> raw = agentClient.getForMap(node, "/file/script/discover", params);
                if (raw == null) {
                    log.warn("[ScriptScan] 节点 {} 返回空响应", nodeId);
                    return null;
                }
                Object codeObj = raw.get("code");
                if (codeObj instanceof Number && ((Number) codeObj).intValue() != 200) {
                    Object message = raw.get("message");
                    log.warn("[ScriptScan] 节点 {} 返回错误: code={}, message={}", nodeId, codeObj, message);
                    return null;
                }
                Object dataObj = raw.get("data");
                if (!(dataObj instanceof List)) {
                    log.warn("[ScriptScan] 节点 {} 返回数据格式异常", nodeId);
                    return null;
                }
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> files = (List<Map<String, Object>>) dataObj;
                return new java.util.AbstractMap.SimpleEntry<>(nodeId, files);
            } catch (Exception e) {
                log.error("[ScriptScan] 节点 {} 扫描失败: {}", nodeId, e.getMessage(), e);
                return null;
            }
        }
    }

    /**
     * 分发脚本文件到单个节点的任务
     */
    private class DistributeScriptTask implements Callable<Map<String, Object>> {
        private final Long nodeId;
        private final String filePath;
        private final String content;
        private final boolean setExecutable;
        private final boolean autoBackup;

        DistributeScriptTask(Long nodeId, String filePath, String content, boolean setExecutable, boolean autoBackup) {
            this.nodeId = nodeId;
            this.filePath = filePath;
            this.content = content;
            this.setExecutable = setExecutable;
            this.autoBackup = autoBackup;
        }

        @Override
        public Map<String, Object> call() {
            Map<String, Object> result = new HashMap<>();
            result.put("nodeId", nodeId);
            try {
                NodeModel node = nodeMapper.findById(nodeId);
                if (node == null || node.getStatus() == null || node.getStatus() != 1) {
                    result.put("success", false);
                    result.put("error", "节点离线或不存在");
                    return result;
                }

                Map<String, Object> body = new HashMap<>();
                body.put("filePath", filePath);
                body.put("content", content);
                body.put("backup", autoBackup);
                body.put("setExecutable", setExecutable);

                Map<String, Object> response = agentClient.postForMap(node, "/file/script", body);
                if (response != null) {
                    Object codeObj = response.get("code");
                    if (codeObj instanceof Number && ((Number) codeObj).intValue() == 200) {
                        result.put("success", true);
                        result.put("nodeName", node.getName());
                    } else {
                        result.put("success", false);
                        result.put("error", response.get("message"));
                    }
                } else {
                    result.put("success", false);
                    result.put("error", "Agent 无响应");
                }
            } catch (Exception e) {
                result.put("success", false);
                result.put("error", e.getMessage());
            }
            return result;
        }
    }

    /**
     * 获取脚本文件哈希的任务
     */
    private class FetchScriptHashTask implements Callable<Map.Entry<Long, String>> {
        private final Long nodeId;
        private final String filePath;

        FetchScriptHashTask(Long nodeId, String filePath) {
            this.nodeId = nodeId;
            this.filePath = filePath;
        }

        @Override
        public Map.Entry<Long, String> call() {
            try {
                NodeModel node = nodeMapper.findById(nodeId);
                if (node == null || node.getStatus() == null || node.getStatus() != 1) {
                    return null;
                }
                Map<String, String> params = new HashMap<>();
                params.put("filePath", filePath);
                String content = agentClient.extractDataString(agentClient.getForMap(node, "/file/script", params));
                return new java.util.AbstractMap.SimpleEntry<>(nodeId, sha256(content));
            } catch (Exception e) {
                return null;
            }
        }
    }
}
