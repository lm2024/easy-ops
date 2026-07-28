package com.ops.server.configmgmt.service;

import com.ops.common.constant.ErrorCode;
import com.ops.common.exception.BusinessException;
import com.ops.common.model.GlobalScriptFileModel;
import com.ops.common.model.GlobalScriptSnapshotModel;
import com.ops.common.model.GlobalScriptDistributeRecordModel;
import com.ops.common.model.NodeModel;
import com.ops.server.client.AgentClient;
import com.ops.server.mapper.GlobalScriptFileMapper;
import com.ops.server.mapper.GlobalScriptSnapshotMapper;
import com.ops.server.mapper.GlobalScriptDistributeRecordMapper;
import com.ops.server.mapper.NodeMapper;
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
 * 全局脚本文件管理服务
 * 管理所有 Agent 节点的脚本/配置文件，不绑定项目
 * 用于管理 Agent 自身的脚本（如 start.sh、stop.sh）
 */
@Service
public class GlobalScriptMgmtService {

    private static final Logger log = LoggerFactory.getLogger(GlobalScriptMgmtService.class);

    private static final int SCAN_TIMEOUT_SEC = 15;
    private static final int DISTRIBUTE_TIMEOUT_SEC = 30;
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(20);

    @Autowired
    private GlobalScriptFileMapper scriptFileMapper;

    @Autowired
    private GlobalScriptSnapshotMapper snapshotMapper;

    @Autowired
    private NodeMapper nodeMapper;

    @Autowired
    private AgentClient agentClient;

    @Autowired
    private GlobalScriptDistributeRecordMapper distributeRecordMapper;

    /**
     * 查询全局脚本文件列表
     */
    public List<GlobalScriptFileModel> listFiles() {
        return scriptFileMapper.findAll();
    }

    /**
     * 创建脚本文件定义
     */
    public GlobalScriptFileModel createFile(GlobalScriptFileModel model) {
        // 检查是否已存在
        GlobalScriptFileModel existing = scriptFileMapper.findByFilePath(model.getFilePath());
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
    public GlobalScriptFileModel updateFile(GlobalScriptFileModel model) {
        model.setUpdateTime(System.currentTimeMillis());
        scriptFileMapper.update(model);
        return model;
    }

    /**
     * 删除脚本文件定义
     */
    public void deleteFile(Long id) {
        // 同时删除快照记录
        snapshotMapper.deleteByScriptFileId(id);
        scriptFileMapper.deleteById(id);
    }

    /**
     * 获取脚本文件详情
     */
    public GlobalScriptFileModel getFile(Long id) {
        return scriptFileMapper.findById(id);
    }

    /**
     * 扫描指定目录下的脚本文件并导入（并行扫描优化）
     * 扫描所有在线 Agent 节点的指定目录
     */
    public List<GlobalScriptFileModel> scanAndImport(String scanDir) {
        // 获取所有节点（使用 findByStatus，status 传空获取全部）
        List<NodeModel> allNodes = nodeMapper.findByStatus("", 1, 1000, "", null, null);
        if (allNodes == null || allNodes.isEmpty()) {
            log.info("[GlobalScriptScan] 无节点，返回已有记录");
            return scriptFileMapper.findAll();
        }

        // 筛选在线节点
        List<NodeModel> onlineNodes = new ArrayList<>();
        for (NodeModel node : allNodes) {
            if (Integer.valueOf(1).equals(node.getStatus())) {
                onlineNodes.add(node);
            }
        }

        if (onlineNodes.isEmpty()) {
            log.info("[GlobalScriptScan] 无在线节点，返回已有记录");
            return scriptFileMapper.findAll();
        }

        // 收集所有在线节点的扫描结果（去重）
        Map<String, GlobalScriptFileModel> discovered = new LinkedHashMap<>();
        int successCount = 0;

        // 并行扫描所有在线节点
        log.info("[GlobalScriptScan] 开始并行扫描: scanDir={}, 在线节点数={}", scanDir, onlineNodes.size());
        long startTime = System.currentTimeMillis();

        // 分批提交扫描任务
        List<Future<Map<Long, List<Map<String, Object>>>>> futures = new ArrayList<>();
        int batchSize = Math.min(onlineNodes.size(), 20);

        for (int i = 0; i < onlineNodes.size(); i += batchSize) {
            List<NodeModel> batch = onlineNodes.subList(i, Math.min(i + batchSize, onlineNodes.size()));
            futures.add(EXECUTOR.submit(new ScanBatchTask(batch, scanDir)));
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
                            log.info("[GlobalScriptScan] 节点 {} 发现 {} 个脚本文件", nodeId, files.size());
                            for (Map<String, Object> f : files) {
                                String filePath = f.get("filePath") != null ? f.get("filePath").toString() : "";
                                if (filePath.isEmpty()) continue;

                                String fileKey = filePath;
                                if (!discovered.containsKey(fileKey)) {
                                    GlobalScriptFileModel model = new GlobalScriptFileModel();
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
                log.error("[GlobalScriptScan] 批量扫描任务失败: {}", e.getMessage(), e);
            }
        }

        long scanTime = System.currentTimeMillis() - startTime;
        log.info("[GlobalScriptScan] 扫描完成: 在线节点数={}, 成功节点数={}, 发现文件数={}, 耗时={}ms",
                onlineNodes.size(), successCount, discovered.size(), scanTime);

        // 查询已有 DB 记录
        List<GlobalScriptFileModel> existing = scriptFileMapper.findAll();
        Set<String> existingPaths = new HashSet<>();
        for (GlobalScriptFileModel e : existing) {
            existingPaths.add(e.getFilePath());
        }

        // 自动导入新发现的文件
        List<GlobalScriptFileModel> imported = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (GlobalScriptFileModel model : discovered.values()) {
            if (!existingPaths.contains(model.getFilePath())) {
                model.setCreateTime(now);
                model.setUpdateTime(now);
                scriptFileMapper.insert(model);
                imported.add(model);
                log.info("[GlobalScriptScan] 自动导入脚本文件: path={}", model.getFilePath());
            }
        }

        log.info("[GlobalScriptScan] 导入完成: 新导入 {} 个文件", imported.size());
        return scriptFileMapper.findAll();
    }

    /**
     * 获取各节点脚本快照
     */
    public Map<String, Object> getSnapshot(Long scriptFileId) {
        GlobalScriptFileModel scriptFile = requireFile(scriptFileId);
        List<GlobalScriptSnapshotModel> snapshots = snapshotMapper.findByScriptFileId(scriptFileId);
        Map<Long, GlobalScriptSnapshotModel> snapMap = new HashMap<>();
        for (GlobalScriptSnapshotModel s : snapshots) {
            snapMap.put(s.getNodeId(), s);
        }

        List<Map<String, Object>> nodes = new ArrayList<>();
        Set<String> hashes = new HashSet<>();
        
        // 获取所有节点
        List<NodeModel> allNodes = nodeMapper.findByStatus("", 1, 1000, "", null, null);
        if (allNodes == null) allNodes = new ArrayList<>();
        for (NodeModel node : allNodes) {
            GlobalScriptSnapshotModel snap = snapMap.get(node.getId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("nodeId", node.getId());
            item.put("nodeName", node.getName());
            item.put("nodeIp", node.getIp());
            item.put("nodeStatus", node.getStatus());
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
    public Map<String, Object> getContentAuto(Long scriptFileId) {
        GlobalScriptFileModel file = requireFile(scriptFileId);
        String filePath = file.getFilePath();

        // 获取所有在线节点
        List<NodeModel> allNodes = nodeMapper.findByStatus("", 1, 1000, "", null, null);
        if (allNodes == null) allNodes = new ArrayList<>();
        for (NodeModel node : allNodes) {
            if (!Integer.valueOf(1).equals(node.getStatus())) continue;
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
    public String getContent(Long nodeId, Long scriptFileId) {
        GlobalScriptFileModel file = requireFile(scriptFileId);
        NodeModel node = requireOnlineNode(nodeId);
        String filePath = file.getFilePath();
        Map<String, String> params = new HashMap<>();
        params.put("filePath", filePath);
        return agentClient.extractDataString(agentClient.getForMap(node, "/file/script", params));
    }

    /**
     * 分发脚本文件到指定节点（并行分发优化）
     */
    public Map<String, Object> distribute(Long scriptFileId, String content,
                                          List<Long> targetNodeIds, boolean setExecutable,
                                          boolean autoBackup, Long operatorId) {
        GlobalScriptFileModel file = requireFile(scriptFileId);
        if (content == null || content.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "脚本内容不能为空");
        }

        String filePath = file.getFilePath();
        String hash = sha256(content);

        // 创建分发记录
        GlobalScriptDistributeRecordModel record = new GlobalScriptDistributeRecordModel();
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
                        upsertSnapshot(nodeId, scriptFileId, hash, content.length(), setExecutable ? 755 : 644, 1, now);
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
        distributeRecordMapper.updateStatus(record.getId(), status, "成功=" + successCount + ", 失败=" + failCount);

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
    public Map<String, Object> refreshSnapshots(Long scriptFileId) {
        GlobalScriptFileModel file = requireFile(scriptFileId);
        String filePath = file.getFilePath();

        // 获取所有节点
        List<NodeModel> allNodes = nodeMapper.findByStatus("", 1, 1000, "", null, null);
        if (allNodes == null) allNodes = new ArrayList<>();
        List<Long> nodeIds = new ArrayList<>();
        for (NodeModel node : allNodes) {
            nodeIds.add(node.getId());
        }

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
            upsertSnapshot(nodeId, scriptFileId, hash, 0, 0, syncStatus, now);
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

    private void upsertSnapshot(Long nodeId, Long scriptFileId,
                                String hash, int contentSize, int fileMode, int syncStatus, long now) {
        GlobalScriptSnapshotModel existing = snapshotMapper.findByNodeAndFile(nodeId, scriptFileId);
        if (existing == null) {
            GlobalScriptSnapshotModel snap = new GlobalScriptSnapshotModel();
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

    private GlobalScriptFileModel requireFile(Long scriptFileId) {
        GlobalScriptFileModel file = scriptFileMapper.findById(scriptFileId);
        if (file == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "脚本文件不存在");
        }
        return file;
    }

    private NodeModel requireOnlineNode(Long nodeId) {
        NodeModel node = nodeMapper.findById(nodeId);
        if (node == null || node.getStatus() == null || node.getStatus() != 1) {
            throw new BusinessException(1002, "节点不存在或离线");
        }
        return node;
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
    private class ScanBatchTask implements Callable<Map<Long, List<Map<String, Object>>>> {
        private final List<NodeModel> nodes;
        private final String scanDir;

        ScanBatchTask(List<NodeModel> nodes, String scanDir) {
            this.nodes = nodes;
            this.scanDir = scanDir;
        }

        @Override
        public Map<Long, List<Map<String, Object>>> call() {
            Map<Long, List<Map<String, Object>>> result = new HashMap<>();
            List<Future<Map.Entry<Long, List<Map<String, Object>>>>> futures = new ArrayList<>();

            for (NodeModel node : nodes) {
                futures.add(EXECUTOR.submit(new ScanNodeTask(node, scanDir)));
            }

            for (Future<Map.Entry<Long, List<Map<String, Object>>>> future : futures) {
                try {
                    Map.Entry<Long, List<Map<String, Object>>> entry = future.get(SCAN_TIMEOUT_SEC, TimeUnit.SECONDS);
                    if (entry != null && entry.getValue() != null) {
                        result.put(entry.getKey(), entry.getValue());
                    }
                } catch (Exception e) {
                    log.warn("[GlobalScriptScan] 节点扫描任务超时或失败: {}", e.getMessage());
                }
            }
            return result;
        }
    }

    /**
     * 单节点扫描脚本文件任务
     */
    private class ScanNodeTask implements Callable<Map.Entry<Long, List<Map<String, Object>>>> {
        private final NodeModel node;
        private final String scanDir;

        ScanNodeTask(NodeModel node, String scanDir) {
            this.node = node;
            this.scanDir = scanDir;
        }

        @Override
        public Map.Entry<Long, List<Map<String, Object>>> call() {
            try {
                Map<String, String> params = new HashMap<>();
                params.put("scanDir", scanDir);
                log.debug("[GlobalScriptScan] 请求节点 {} 扫描脚本文件: scanDir={}", node.getId(), scanDir);
                Map<String, Object> raw = agentClient.getForMap(node, "/file/script/discover", params);
                if (raw == null) {
                    log.warn("[GlobalScriptScan] 节点 {} 返回空响应", node.getId());
                    return null;
                }
                Object codeObj = raw.get("code");
                if (codeObj instanceof Number && ((Number) codeObj).intValue() != 200) {
                    Object message = raw.get("message");
                    log.warn("[GlobalScriptScan] 节点 {} 返回错误: code={}, message={}", node.getId(), codeObj, message);
                    return null;
                }
                Object dataObj = raw.get("data");
                if (!(dataObj instanceof List)) {
                    log.warn("[GlobalScriptScan] 节点 {} 返回数据格式异常", node.getId());
                    return null;
                }
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> files = (List<Map<String, Object>>) dataObj;
                return new java.util.AbstractMap.SimpleEntry<>(node.getId(), files);
            } catch (Exception e) {
                log.error("[GlobalScriptScan] 节点 {} 扫描失败: {}", node.getId(), e.getMessage(), e);
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
