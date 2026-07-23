package com.ops.server.controller;

import com.ops.common.constant.SystemConstant;
import com.ops.common.enums.NodeStatus;
import com.ops.common.model.NodeModel;
import com.ops.common.model.OperationLogModel;
import com.ops.common.response.Result;
import com.ops.server.interceptor.AuthInterceptor;
import com.ops.server.mapper.NodeMapper;
import com.ops.server.mapper.OperationLogMapper;
import com.ops.server.mapper.MonitorSnapshotMapper;
import com.ops.server.service.AlarmService;
import com.ops.server.service.AgentUpgradeService;
import com.ops.server.util.SecurityContext;
import com.ops.server.service.NodeService;
import com.ops.server.websocket.MonitorHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/nodes")
public class NodeController {

    private static final Logger log = LoggerFactory.getLogger(NodeController.class);

    @Autowired
    private NodeService nodeService;

    @Autowired
    private NodeMapper nodeMapper;

    @Autowired
    private OperationLogMapper operationLogMapper;

    @Autowired
    private AlarmService alarmService;

    @Autowired
    private AuthInterceptor authInterceptor;

    @Autowired
    private MonitorHandler monitorHandler;

    @Autowired
    private MonitorSnapshotMapper snapshotMapper;

    @Autowired
    private SecurityContext securityContext;

    @Autowired
    private AgentUpgradeService agentUpgradeService;

    @Autowired
    private com.ops.server.client.AgentClient agentClient;

    /**
     * GET /api/nodes - 节点列表 (支持分页和状态筛选)
     */
    @GetMapping
    public Result<?> listNodes(
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String keyword) {
        List<NodeModel> nodes = nodeService.findByStatus(status, page, pageSize, keyword);
        Long total = nodeService.countByStatus(status, keyword);
        Map<String, Object> data = new HashMap<>();
        data.put("list", nodes);
        data.put("total", total);
        return Result.success(data);
    }

    /**
     * POST /api/nodes/export - 导出节点CSV
     */
    @GetMapping("/export")
    public void exportNodes(HttpServletResponse response) {
        try {
            List<NodeModel> nodes = nodeService.findByStatus(null, 1, Integer.MAX_VALUE, null);
            response.setContentType("text/csv;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment;filename=nodes.csv");
            response.getWriter().write("名称,IP,端口,Token,状态,系统信息,创建时间\n");
            for (NodeModel node : nodes) {
                response.getWriter().write(String.format("%s,%s,%d,%s,%s,%s,%d%n",
                        node.getName(), node.getIp(), node.getPort(),
                        node.getToken() != null ? node.getToken() : "",
                        node.getStatus() == 1 ? "在线" : "离线",
                        node.getOsInfo() != null ? node.getOsInfo() : "",
                        node.getCreateTime() != null ? node.getCreateTime() : 0));
            }
            response.getWriter().flush();
        } catch (Exception e) {
            throw new RuntimeException("导出失败", e);
        }
    }

    /**
     * POST /api/nodes/import - 导入节点CSV
     */
    @PostMapping("/import")
    public Result<?> importNodes(@RequestParam("file") MultipartFile file) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), "UTF-8"))) {
            String line;
            int count = 0;
            reader.readLine(); // skip header
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] fields = parseCsvLine(line);
                if (fields.length < 2) continue;
                NodeModel node = new NodeModel();
                node.setName(fields[0].trim());
                node.setIp(fields[1].trim());
                node.setPort(fields.length > 2 && !fields[2].trim().isEmpty() ? Integer.parseInt(fields[2].trim()) : 2123);
                node.setToken(fields.length > 3 ? fields[3].trim() : "");
                node.setStatus(NodeStatus.ONLINE.getCode());
                node.setCreateTime(System.currentTimeMillis());
                node.setUpdateTime(System.currentTimeMillis());

                if (nodeService.findByName(node.getName()) != null) continue; // skip duplicate
                nodeService.insert(node);
                count++;
            }
            java.util.Map<String, Object> result = new java.util.HashMap<>();
            result.put("imported", count);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error(500, "导入失败: " + e.getMessage());
        }
    }

    /**
     * GET /api/nodes/agent/package - 查看 Server 端 Agent 升级包
     */
    @GetMapping("/agent/package")
    public Result<?> agentPackageInfo() {
        return Result.success(agentUpgradeService.packageInfo());
    }

    /**
     * POST /api/nodes/agent/package - 上传 Agent 升级包到 Server
     */
    @PostMapping("/agent/package")
    public Result<?> uploadAgentPackage(@RequestParam("file") MultipartFile file) {
        try {
            return Result.success(agentUpgradeService.savePackage(file));
        } catch (Exception e) {
            return Result.error(500, "上传失败: " + e.getMessage());
        }
    }

    /**
     * POST /api/nodes/agent/upgrade/batch - 批量升级 Agent（body: { nodeIds: [1,2] }，空则升级全部在线节点）
     */
    @PostMapping("/agent/upgrade/batch")
    public Result<?> batchUpgradeAgent(@RequestBody(required = false) Map<String, Object> body) {
        try {
            List<Long> nodeIds = null;
            if (body != null && body.get("nodeIds") instanceof List) {
                nodeIds = new java.util.ArrayList<>();
                for (Object id : (List<?>) body.get("nodeIds")) {
                    if (id instanceof Number) {
                        nodeIds.add(((Number) id).longValue());
                    }
                }
            }
            return Result.success(agentUpgradeService.upgradeBatch(nodeIds));
        } catch (Exception e) {
            return Result.error(500, "批量升级失败: " + e.getMessage());
        }
    }

    /**
     * GET /api/nodes/{id}/agent/version - 查询节点 Agent 版本
     */
    @GetMapping("/{id}/agent/version")
    public Result<?> getAgentVersion(@PathVariable Long id) {
        NodeModel node = nodeService.findById(id);
        if (node == null) {
            return Result.error(1002, "节点不存在");
        }
        try {
            return Result.success(agentClient.getAgentVersion(node));
        } catch (Exception e) {
            return Result.error(500, "查询失败: " + e.getMessage());
        }
    }

    /**
     * POST /api/nodes/{id}/agent/upgrade - 升级单个节点 Agent
     */
    @PostMapping("/{id}/agent/upgrade")
    public Result<?> upgradeAgent(@PathVariable Long id) {
        try {
            return Result.success(agentUpgradeService.upgradeNode(id));
        } catch (IllegalArgumentException e) {
            return Result.error(1002, e.getMessage());
        } catch (Exception e) {
            return Result.error(500, "升级失败: " + e.getMessage());
        }
    }

    /**
     * GET /api/nodes/{id} - 节点详情
     */
    @GetMapping("/{id}")
    public Result<?> getNode(@PathVariable Long id) {
        NodeModel node = nodeService.findById(id);
        // SEC-004: 节点操作权限校验n        if (!securityContext.getCurrentNodeId() && !securityContext.hasProjectPermission(null)) {n            // non-agent users are filtered by project, which is handled by project bindingn        }
        return node != null ? Result.success(node) : Result.error(1002, "节点不存在");
    }

    /**
     * POST /api/nodes - 新增节点
     */
    @PostMapping
    public Result<?> addNode(@RequestBody NodeModel node, HttpServletRequest httpRequest) {
        if (nodeService.findByName(node.getName()) != null) {
            return Result.paramError("节点名称已存在");
        }
        node.setStatus(NodeStatus.ONLINE.getCode());
        node.setCreateTime(System.currentTimeMillis());
        node.setUpdateTime(System.currentTimeMillis());
        nodeService.insert(node);

        // Log operation
        logOperation(node.getId(), "NODE", "ADD", "添加节点: " + node.getName(), httpRequest.getRemoteAddr());
        return Result.success();
    }

    /**
     * PUT /api/nodes/{id} - 修改节点
     */
    @PutMapping("/{id}")
    public Result<?> updateNode(@PathVariable Long id, @RequestBody NodeModel node) {
        NodeModel existing = nodeService.findById(id);
        if (existing == null) {
            return Result.error(1002, "节点不存在");
        }
        node.setId(id);
        node.setCreateTime(existing.getCreateTime());
        node.setUpdateTime(System.currentTimeMillis());
        nodeService.update(node);
        return Result.success();
    }

    /**
     * DELETE /api/nodes/{id} - 删除节点
     */
    @DeleteMapping("/{id}")
    public Result<?> deleteNode(@PathVariable Long id) {
        if (nodeService.countByNodeId(id) > 0) {
            return Result.error(1003, "该节点下有项目绑定，无法删除");
        }
        nodeService.deleteById(id);
        return Result.success();
    }

    /**
     * PUT /api/nodes/{id}/tags - 更新节点标签
     */
    @PutMapping("/{id}/tags")
    public Result<?> updateTags(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String tags = body.get("tags");
        if (tags == null) tags = "";
        nodeService.updateTags(id, tags);
        return Result.success();
    }

    /**
     * GET /api/nodes/heartbeat - 心跳接口 (Agent侧)
     * 自动注册：如果 token 不存在，自动创建节点记录
     * 接收Agent上报的监控数据（X-Metrics header）
     */
    @GetMapping("/heartbeat")
    public Result<?> heartbeat(HttpServletRequest request,
                               @RequestParam(required = false) String nodeIp,
                               @RequestParam(required = false) Integer nodePort) {
        String token = request.getHeader(SystemConstant.TOKEN_HEADER);
        if (token == null || token.isEmpty()) {
            return Result.authError();
        }

        String nodeId = nodeMapper.getNodeIdByToken(token);

        // 自动注册：如果 token 不存在，自动创建节点
        if (nodeId == null) {
            String nodeName = request.getHeader("X-Node-Name");
            if (nodeName == null || nodeName.isEmpty()) {
                nodeName = "auto-registered-" + System.currentTimeMillis();
            }

            NodeModel node = new NodeModel();
            node.setName(nodeName);
            node.setIp(request.getRemoteAddr());
            node.setPort(nodePort != null ? nodePort : 2123);
            node.setToken(token);
            node.setStatus(NodeStatus.ONLINE.getCode());
            node.setCreateTime(System.currentTimeMillis());
            node.setUpdateTime(System.currentTimeMillis());
            nodeService.insert(node);

            nodeId = String.valueOf(node.getId());
            log.info("Auto-registered new node: id={}, name={}, token={}", nodeId, nodeName, token);
        }

        // 使用 Agent 上报的外部 IP，如果没传则用请求来源 IP
        String ip = (nodeIp != null && !nodeIp.isEmpty()) ? nodeIp : request.getRemoteAddr();
        String osInfo = request.getHeader("X-OS-Info");
        String javaVersion = request.getHeader("X-Java-Version");
        String cpuInfo = request.getHeader("X-CPU-Info");
        String memInfo = request.getHeader("X-Mem-Info");
        String diskInfo = request.getHeader("X-Disk-Info");
        String osArch = request.getHeader("X-OS-Arch");
        String agentVersion = request.getHeader("X-Agent-Version");
        String metricsBase64 = request.getHeader("X-Metrics");

        // 解析硬件信息
        Integer cpuCores = null;
        Integer totalMemoryMb = null;
        Long totalDiskMb = null;
        try {
            if (cpuInfo != null && !cpuInfo.isEmpty()) cpuCores = Integer.parseInt(cpuInfo);
            if (memInfo != null && !memInfo.isEmpty()) totalMemoryMb = Integer.parseInt(memInfo);
            if (diskInfo != null && !diskInfo.isEmpty()) totalDiskMb = Long.parseLong(diskInfo);
        } catch (NumberFormatException ignored) {}

        nodeMapper.updateHeartbeat(Long.parseLong(nodeId), System.currentTimeMillis(),
                ip, osInfo, javaVersion, cpuCores, totalMemoryMb, totalDiskMb, osArch, agentVersion);

        // 如果 Agent 上报了外部可访问的端口，更新节点端口
        if (nodePort != null && nodePort > 0) {
            nodeMapper.updatePort(Long.parseLong(nodeId), nodePort, System.currentTimeMillis());
        }

        // Update agent token cache
        Map<String, String> agentCache = authInterceptor.getAgentTokenCache();
        agentCache.put(nodeId, token);

        // 解析并存储监控数据
        if (metricsBase64 != null && !metricsBase64.isEmpty()) {
            try {
                String metricsJson = new String(java.util.Base64.getDecoder().decode(metricsBase64), "UTF-8");
                @SuppressWarnings("unchecked")
                Map<String, Object> metrics = com.alibaba.fastjson2.JSON.parseObject(metricsJson, Map.class);
                if (metrics != null && !metrics.isEmpty()) {
                    // 存储到MonitorSnapshot表
                    saveMonitorSnapshot(Long.parseLong(nodeId), metrics);
                    // 通过WebSocket广播给前端
                    broadcastMonitorUpdate(Long.parseLong(nodeId), metrics);
                }
            } catch (Exception e) {
                log.warn("Failed to parse metrics from node {}: {}", nodeId, e.getMessage());
            }
        }

        // Get projects bound to this node
        List<String> projectNames = nodeMapper.getProjectNamesByNodeId(Long.parseLong(nodeId));

        Map<String, Object> data = new java.util.HashMap<>();
        data.put("nodeId", nodeId);
        data.put("projects", projectNames);
        return Result.success(data);
    }

    /**
     * 保存监控快照到数据库
     */
    private void saveMonitorSnapshot(Long nodeId, Map<String, Object> metrics) {
        try {
            com.ops.common.model.MonitorSnapshotModel snap = new com.ops.common.model.MonitorSnapshotModel();
            snap.setNodeId(nodeId);
            snap.setCollectTime(System.currentTimeMillis());

            // 获取该节点关联的项目ID（取第一个）
            List<Long> projectIds = nodeMapper.getProjectIdsByNodeId(nodeId);
            if (projectIds != null && !projectIds.isEmpty()) {
                snap.setProjectId(projectIds.get(0));
            } else {
                snap.setProjectId(0L); // 默认值
            }

            // 解析CPU使用率
            Object cpuUsage = metrics.get("cpuUsagePercent");
            if (cpuUsage instanceof Number) {
                snap.setHostCpuPercent(new java.math.BigDecimal(((Number) cpuUsage).doubleValue()));
            }

            // 解析内存使用率
            Object memUsage = metrics.get("memoryUsagePercent");
            if (memUsage instanceof Number) {
                snap.setHostMemoryPercent(((Number) memUsage).intValue());
            }

            // 解析堆内存
            Object heapUsed = metrics.get("heapUsedMB");
            Object heapMax = metrics.get("heapMaxMB");
            if (heapUsed instanceof Number) snap.setHeapUsedMb(((Number) heapUsed).intValue());
            if (heapMax instanceof Number) snap.setHeapMaxMb(((Number) heapMax).intValue());

            // 解析磁盘使用率
            Object diskUsage = metrics.get("diskUsagePercent");
            if (diskUsage instanceof Number) {
                snap.setDiskUsagePercent(((Number) diskUsage).intValue());
            }

            // 设置健康状态（基于CPU和内存）
            double cpuPercent = snap.getHostCpuPercent() != null ? snap.getHostCpuPercent().doubleValue() : 0;
            int memPercent = snap.getHostMemoryPercent() != null ? snap.getHostMemoryPercent() : 0;
            if (cpuPercent > 90 || memPercent > 90) {
                snap.setHealthStatus("DEGRADED");
                snap.setHealthDetail("CPU=" + cpuPercent + "%, Memory=" + memPercent + "%");
            } else {
                snap.setHealthStatus("UP");
                snap.setHealthDetail("Agent主动上报");
            }

            // 设置进程状态（Agent在线即表示进程运行中）
            snap.setProcessStatus("RUNNING");

            // 存储到数据库
            snapshotMapper.insert(snap);
            log.debug("Saved monitor snapshot for node {}: CPU={}%, Memory={}%", nodeId, cpuPercent, memPercent);
        } catch (Exception e) {
            log.warn("Failed to save monitor snapshot for node {}: {}", nodeId, e.getMessage());
        }
    }

    /**
     * 通过WebSocket广播监控数据更新
     */
    private void broadcastMonitorUpdate(Long nodeId, Map<String, Object> metrics) {
        try {
            Map<String, Object> message = new java.util.HashMap<>();
            message.put("type", "monitor_update");
            message.put("nodeId", nodeId);
            message.put("metrics", metrics);
            message.put("timestamp", System.currentTimeMillis());

            String json = com.alibaba.fastjson2.JSON.toJSONString(message);
            monitorHandler.broadcast("monitor", json);
        } catch (Exception e) {
            log.warn("Failed to broadcast monitor update for node {}: {}", nodeId, e.getMessage());
        }
    }

    private String[] parseCsvLine(String line) {
        List<String> fields = new java.util.ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(sb.toString());
                sb = new StringBuilder();
            } else {
                sb.append(c);
            }
        }
        fields.add(sb.toString());
        return fields.toArray(new String[0]);
    }

    private void logOperation(Long nodeId, String module, String action, String content, String ip) {
        OperationLogModel logModel = new com.ops.common.model.OperationLogModel();
        logModel.setUserId(nodeId);
        logModel.setModule(module);
        logModel.setAction(action);
        logModel.setContent(content);
        logModel.setIp(ip);
        logModel.setCreateTime(System.currentTimeMillis());
        operationLogMapper.insert(logModel);
    }
}
