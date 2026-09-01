package com.ops.server.filemgr;

import com.ops.common.exception.BusinessException;
import com.ops.common.model.NodeModel;
import com.ops.server.client.AgentClient;
import com.ops.server.mapper.DownloadTaskMapper;
import com.ops.server.mapper.NodeMapper;
import com.ops.server.util.SecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件管理 / 压缩下载服务（Server 侧）。
 * 负责：节点校验、代理 Agent 文件浏览、下载任务协调与审计记录、3 小时记录清理。
 * 真实文件操作与压缩都在 Agent 侧执行，Server 仅中转，不占用 Server 磁盘。
 */
@Service
public class FileMgrService {

    private static final Logger log = LoggerFactory.getLogger(FileMgrService.class);

    @Autowired
    private AgentClient agentClient;
    @Autowired
    private NodeMapper nodeMapper;
    @Autowired
    private DownloadTaskMapper downloadTaskMapper;
    @Autowired
    private SecurityContext securityContext;

    @Value("${easyops.filemgr.record-ttl-hours:3}")
    private int recordTtlHours;

    // ==================== 节点 ====================

    public NodeModel requireOnlineNode(Long nodeId) {
        NodeModel node = nodeMapper.findById(nodeId);
        if (node == null || node.getStatus() == null || node.getStatus() != 1) {
            throw new BusinessException(1002, "节点不存在或离线");
        }
        return node;
    }

    // ==================== 文件浏览 ====================

    @SuppressWarnings("unchecked")
    public List<String> roots(Long nodeId) {
        NodeModel node = requireOnlineNode(nodeId);
        Map<String, Object> resp = agentClient.getForMap(node, "/filemgr/roots", null);
        agentClient.ensureAgentSuccess(resp);
        Object data = resp.get("data");
        if (data instanceof List) {
            return (List<String>) data;
        }
        return new ArrayList<>();
    }

    public Map<String, Object> list(Long nodeId, String path) {
        NodeModel node = requireOnlineNode(nodeId);
        Map<String, String> params = new HashMap<>();
        if (path != null && !path.trim().isEmpty()) {
            params.put("path", path);
        }
        Map<String, Object> resp = agentClient.getForMap(node, "/filemgr/list", params);
        agentClient.ensureAgentSuccess(resp);
        return agentClient.extractDataMap(resp);
    }

    public Map<String, Object> info(Long nodeId, String path) {
        NodeModel node = requireOnlineNode(nodeId);
        Map<String, String> params = new HashMap<>();
        params.put("path", path);
        Map<String, Object> resp = agentClient.getForMap(node, "/filemgr/info", params);
        agentClient.ensureAgentSuccess(resp);
        return agentClient.extractDataMap(resp);
    }

    // ==================== 下载任务 ====================

    @SuppressWarnings("unchecked")
    public Map<String, Object> createTask(Long nodeId, List<String> paths, String baseName) {
        NodeModel node = requireOnlineNode(nodeId);
        Map<String, Object> body = new HashMap<>();
        body.put("paths", paths);
        body.put("baseName", baseName);
        Map<String, Object> resp = agentClient.postForMap(node, "/filemgr/task/create", body);
        agentClient.ensureAgentSuccess(resp);
        Map<String, Object> data = agentClient.extractDataMap(resp);
        // 审计记录（Agent 重启后仍可查）
        try {
            Map<String, Object> record = new HashMap<>();
            record.put("taskId", data.get("id"));
            record.put("nodeId", nodeId);
            record.put("nodeName", node.getName());
            record.put("userId", securityContext.getCurrentUserId());
            record.put("tenantId", securityContext.getCurrentTenantId());
            record.put("name", data.get("name"));
            record.put("paths", paths == null ? "[]" : paths.toString());
            record.put("status", data.get("status"));
            record.put("totalSize", data.get("totalSize"));
            long now = System.currentTimeMillis();
            record.put("createTime", now);
            record.put("updateTime", now);
            downloadTaskMapper.insert(record);
        } catch (Exception e) {
            log.warn("记录下载任务失败: {}", e.getMessage());
        }
        return data;
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listTasks(Long nodeId) {
        NodeModel node = requireOnlineNode(nodeId);
        Map<String, Object> resp = agentClient.getForMap(node, "/filemgr/task/list", null);
        agentClient.ensureAgentSuccess(resp);
        return extractList(resp);
    }

    public Map<String, Object> getTask(Long nodeId, String taskId) {
        NodeModel node = requireOnlineNode(nodeId);
        Map<String, Object> resp = agentClient.getForMap(node, "/filemgr/task/" + taskId, null);
        agentClient.ensureAgentSuccess(resp);
        return agentClient.extractDataMap(resp);
    }

    public Map<String, Object> cancelTask(Long nodeId, String taskId) {
        NodeModel node = requireOnlineNode(nodeId);
        Map<String, Object> resp = agentClient.postForMap(node, "/filemgr/task/" + taskId + "/cancel", new HashMap<>());
        agentClient.ensureAgentSuccess(resp);
        try {
            downloadTaskMapper.updateStatus(taskId, "CANCELLED", System.currentTimeMillis());
        } catch (Exception ignored) {
        }
        return agentClient.extractDataMap(resp);
    }

    public void deleteTask(Long nodeId, String taskId) {
        NodeModel node = requireOnlineNode(nodeId);
        Map<String, Object> resp = agentClient.postForMap(node, "/filemgr/task/" + taskId + "/delete", new HashMap<>());
        agentClient.ensureAgentSuccess(resp);
        try {
            downloadTaskMapper.deleteByTaskId(taskId);
        } catch (Exception ignored) {
        }
    }

    /** 分卷文件（供流式下载定位）。 */
    public Map<String, Object> taskPartMeta(Long nodeId, String taskId, int index) {
        Map<String, Object> task = getTask(nodeId, taskId);
        Object partsObj = task.get("parts");
        if (partsObj instanceof List) {
            for (Object o : (List<?>) partsObj) {
                Map<String, Object> part = (Map<String, Object>) o;
                if (Number.class.isInstance(part.get("index")) && ((Number) part.get("index")).intValue() == index) {
                    return part;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractList(Map<String, Object> resp) {
        Object data = resp.get("data");
        if (data instanceof List) {
            return (List<Map<String, Object>>) data;
        }
        return new ArrayList<>();
    }

    // ==================== 记录清理（不占 Server 空间） ====================

    /** 每 10 分钟删除 3 小时前的下载任务审计记录。 */
    @Scheduled(fixedDelay = 600000)
    public void cleanupExpiredRecords() {
        try {
            long cutoff = System.currentTimeMillis()
                    - Math.max(1, recordTtlHours) * 3600L * 1000L;
            int deleted = downloadTaskMapper.deleteBefore(cutoff);
            if (deleted > 0) {
                log.info("清理过期下载任务记录 {} 条", deleted);
            }
        } catch (Exception e) {
            log.warn("清理下载任务记录异常: {}", e.getMessage());
        }
    }
}
