package com.ops.server.monitorapp.controller;

import com.ops.common.model.MonitorSnapshotModel;
import com.ops.common.model.NodeModel;
import com.ops.common.model.ProjectHealthProbeModel;
import com.ops.common.model.ProjectModel;
import com.ops.common.response.Result;
import com.ops.server.mapper.MonitorSnapshotMapper;
import com.ops.server.mapper.NodeMapper;
import com.ops.server.mapper.ProjectMapper;
import com.ops.server.monitorapp.service.HealthProbeService;
import com.ops.server.monitorapp.service.MonitorCollectConfigService;
import com.ops.server.monitorapp.service.MonitorCollectorService;
import com.ops.server.service.NodeService;

import com.ops.server.util.SecurityContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 应用级监控接口
 */
@RestController
@RequestMapping("/monitor")
public class AppMonitorController {

    @Autowired
    private ProjectMapper projectMapper;
    @Autowired
    private NodeMapper nodeMapper;
    @Autowired
    private MonitorSnapshotMapper snapshotMapper;
    @Autowired
    private HealthProbeService healthProbeService;
    @Autowired
    private SecurityContext securityContext;
    @Autowired
    private MonitorCollectorService collectorService;
    @Autowired
    private MonitorCollectConfigService collectConfigService;

    @Autowired
    private NodeService nodeService;



    /**
     * GET /api/monitor/app/config - 获取监控采集配置
     */
    @GetMapping("/app/config")
    public Result<?> getCollectConfig() {
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("collectIntervalSec", collectConfigService.getIntervalSec());
        data.put("minIntervalSec", MonitorCollectConfigService.MIN_INTERVAL_SEC);
        data.put("maxIntervalSec", MonitorCollectConfigService.MAX_INTERVAL_SEC);
        return Result.success(data);
    }

    /**
     * POST /api/monitor/app/config - 保存监控采集频率（秒）
     */
    @PostMapping("/app/config")
    public Result<?> saveCollectConfig(@RequestBody Map<String, Object> body) {
        Object raw = body.get("collectIntervalSec");
        if (raw == null) {
            return Result.paramError("collectIntervalSec 不能为空");
        }
        int interval;
        try {
            interval = Integer.parseInt(raw.toString());
        } catch (NumberFormatException e) {
            return Result.paramError("collectIntervalSec 必须为整数");
        }
        int saved = collectConfigService.saveIntervalSec(interval);
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("collectIntervalSec", saved);
        return Result.success(data);
    }

    /**
     * GET /api/monitor/app/dashboard - 全部应用监控总览（优化：批量查询避免N+1）
     */
    @GetMapping("/app/dashboard")
    public Result<?> dashboard() {
        List<ProjectModel> projects = projectMapper.findByFilters(null, null, null, 1, 1000);
        if (projects == null) projects = new ArrayList<>();

        // 收集所有需要查询的节点ID
        Set<Long> allNodeIds = new HashSet<>();
        Map<Long, List<Long>> projectNodeMap = new HashMap<>();
        for (ProjectModel project : projects) {
            if (project.getId() == null || !securityContext.hasProjectPermission(project.getId())) continue;
            List<Long> nodeIds = parseNodeIds(project);
            projectNodeMap.put(project.getId(), nodeIds);
            allNodeIds.addAll(nodeIds);
        }

        // 批量查询所有节点
        Map<Long, NodeModel> nodeMap = new HashMap<>();
        for (Long nodeId : allNodeIds) {
            NodeModel node = nodeMapper.findById(nodeId);
            if (node != null) nodeMap.put(nodeId, node);
        }

        // 批量查询所有最新快照（一次查询代替N次）
        Map<Long, MonitorSnapshotModel> snapMap = new HashMap<>();
        if (!allNodeIds.isEmpty()) {
            List<MonitorSnapshotModel> snapshots = snapshotMapper.findLatestByNodeIds(new ArrayList<>(allNodeIds));
            if (snapshots != null) {
                for (MonitorSnapshotModel snap : snapshots) {
                    snapMap.put(snap.getNodeId(), snap);
                }
            }
        }

        // 组装结果
        List<Map<String, Object>> projectList = new ArrayList<>();
        int totalApps = 0, totalUp = 0, totalDown = 0, totalDegraded = 0;

        for (ProjectModel project : projects) {
            if (project.getId() == null || !securityContext.hasProjectPermission(project.getId())) continue;
            List<Long> nodeIds = projectNodeMap.getOrDefault(project.getId(), Collections.emptyList());

            int up = 0, down = 0, degraded = 0;
            List<Map<String, Object>> nodes = new ArrayList<>();
            for (Long nodeId : nodeIds) {
                NodeModel node = nodeMap.get(nodeId);
                MonitorSnapshotModel snap = snapMap.get(nodeId);
                if (snap != null) {
                    if ("UP".equals(snap.getHealthStatus())) up++;
                    else if ("DOWN".equals(snap.getHealthStatus())) down++;
                    else if ("DEGRADED".equals(snap.getHealthStatus())) degraded++;
                    nodes.add(buildNodeInfo(snap, node));
                } else {
                    Map<String, Object> placeholder = new HashMap<>();
                    placeholder.put("nodeId", nodeId);
                    placeholder.put("nodeName", node != null ? node.getName() : "");
                    placeholder.put("healthStatus", "UNKNOWN");
                    placeholder.put("processStatus", "UNKNOWN");
                    nodes.add(placeholder);
                }
            }

            Map<String, Object> summary = new HashMap<>();
            summary.put("totalNodes", nodes.size());
            summary.put("upCount", up);
            summary.put("downCount", down);
            summary.put("degradedCount", degraded);

            Map<String, Object> item = new HashMap<>();
            item.put("projectId", project.getId());
            item.put("projectName", project.getName());
            item.put("jarName", project.getJarName());
            item.put("summary", summary);
            item.put("nodes", nodes);
            projectList.add(item);

            totalApps += nodes.size();
            totalUp += up;
            totalDown += down;
            totalDegraded += degraded;
        }

        Map<String, Object> globalSummary = new HashMap<>();
        globalSummary.put("totalProjects", projectList.size());
        globalSummary.put("totalInstances", totalApps);
        globalSummary.put("upCount", totalUp);
        globalSummary.put("downCount", totalDown);
        globalSummary.put("degradedCount", totalDegraded);

        Map<String, Object> data = new HashMap<>();
        data.put("summary", globalSummary);
        data.put("projects", projectList);
        data.put("collectIntervalSec", collectConfigService.getIntervalSec());
        return Result.success(data);
    }

    private List<Long> parseNodeIds(ProjectModel project) {
        List<Long> ids = new ArrayList<>();
        if (project.getNodeIds() != null && !project.getNodeIds().trim().isEmpty()) {
            for (String s : project.getNodeIds().split(",")) {
                String trimmed = s.trim();
                if (!trimmed.isEmpty()) {
                    try { ids.add(Long.parseLong(trimmed)); } catch (NumberFormatException ignored) {}
                }
            }
        }
        return ids;
    }

    /**
     * POST /api/monitor/app/collect - 立即采集全部应用监控数据（异步）
     * 返回 taskId，前端轮询 /api/monitor/app/collect/status 查询进度
     */
    @PostMapping("/app/collect")
    public Result<?> collectNow() {
        String taskId = collectorService.collectAllAsync();
        Map<String, Object> data = new HashMap<>();
        data.put("taskId", taskId);
        return Result.success(data);
    }

    /**
     * POST /api/monitor/app/collect-filtered - 选择性采集监控数据（异步）
     * Body: { projectIds: [1,2], nodeIds: [3,4] }
     */
    @PostMapping("/app/collect-filtered")
    public Result<?> collectFiltered(@RequestBody Map<String, Object> body) {
        List<Long> projectIds = null;
        List<Long> nodeIds = null;
        if (body.containsKey("projectIds") && body.get("projectIds") != null) {
            projectIds = ((List<Integer>) body.get("projectIds")).stream()
                .map(Long::valueOf).collect(java.util.stream.Collectors.toList());
        }
        if (body.containsKey("nodeIds") && body.get("nodeIds") != null) {
            nodeIds = ((List<Integer>) body.get("nodeIds")).stream()
                .map(Long::valueOf).collect(java.util.stream.Collectors.toList());
        }
        String taskId = collectorService.collectFilteredAsync(projectIds, nodeIds);
        Map<String, Object> data = new HashMap<>();
        data.put("taskId", taskId);
        return Result.success(data);
    }

    /**
     * GET /api/monitor/app/collect/status - 查询采集任务进度
     */
    @GetMapping("/app/collect/status")
    public Result<?> collectStatus(@RequestParam String taskId) {
        Map<String, Object> status = collectorService.getCollectTaskStatus(taskId);
        if (status == null) {
            return Result.error(404, "任务不存在或已过期");
        }
        return Result.success(status);
    }

    /**
     * GET /api/monitor/app/overview - 项目应用监控总览
     */
    @GetMapping("/app/overview")
    public Result<?> overview(@RequestParam Long projectId) {
        if (!securityContext.hasProjectPermission(projectId)) {
            return Result.error(403, "无权限访问该项目");
        }
        ProjectModel project = projectMapper.findById(projectId);
        if (project == null) {
            return Result.error(1005, "项目不存在");
        }
        return Result.success(buildProjectOverview(project));
    }

    private Map<String, Object> buildProjectOverview(ProjectModel project) {
        Long projectId = project.getId();
        List<MonitorSnapshotModel> snapshots = snapshotMapper.findLatestByProject(projectId);
        Map<Long, MonitorSnapshotModel> snapByNode = new HashMap<Long, MonitorSnapshotModel>();
        if (snapshots != null) {
            for (MonitorSnapshotModel snap : snapshots) {
                snapByNode.put(snap.getNodeId(), snap);
            }
        }

        int up = 0, down = 0, degraded = 0, totalResponse = 0, responseCount = 0;
        List<Map<String, Object>> nodes = new ArrayList<Map<String, Object>>();

        if (project.getNodeIds() != null && !project.getNodeIds().trim().isEmpty()) {
            for (String nodeIdStr : project.getNodeIds().split(",")) {
                String trimmed = nodeIdStr.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                try {
                    Long nodeId = Long.parseLong(trimmed);
                    NodeModel node = nodeMapper.findById(nodeId);
                    MonitorSnapshotModel snap = snapByNode.get(nodeId);
                    if (snap != null) {
                        if ("UP".equals(snap.getHealthStatus())) {
                            up++;
                        } else if ("DOWN".equals(snap.getHealthStatus())) {
                            down++;
                        } else if ("DEGRADED".equals(snap.getHealthStatus())) {
                            degraded++;
                        }
                        if (snap.getResponseMs() != null) {
                            totalResponse += snap.getResponseMs();
                            responseCount++;
                        }
                        nodes.add(buildNodeInfo(snap, node));
                    } else {
                        Map<String, Object> placeholder = new HashMap<String, Object>();
                        placeholder.put("nodeId", nodeId);
                        placeholder.put("nodeName", node != null ? node.getName() : "");
                        placeholder.put("healthStatus", "UNKNOWN");
                        placeholder.put("processStatus", "UNKNOWN");
                        placeholder.put("healthDetail", "尚未采集，请点击刷新");
                        nodes.add(placeholder);
                    }
                } catch (NumberFormatException ignored) {
                    // skip invalid node id
                }
            }
        }

        Map<String, Object> summary = new HashMap<String, Object>();
        summary.put("totalNodes", nodes.size());
        summary.put("upCount", up);
        summary.put("downCount", down);
        summary.put("degradedCount", degraded);
        summary.put("avgResponseMs", responseCount > 0 ? totalResponse / responseCount : 0);
        summary.put("stabilityScore", calcStabilityScore(projectId, null));

        Map<String, Object> data = new HashMap<String, Object>();
        data.put("projectId", projectId);
        data.put("projectName", project.getName());
        data.put("jarName", project.getJarName());
        data.put("summary", summary);
        data.put("nodes", nodes);
        return data;
    }

    /**
     * GET /api/monitor/app/node - 单节点详细指标
     */
    @GetMapping("/app/node")
    public Result<?> nodeDetail(@RequestParam Long projectId, @RequestParam Long nodeId) {
        if (!securityContext.hasProjectPermission(projectId)) {
            return Result.error(403, "无权限访问该项目");
        }
        MonitorSnapshotModel snap = snapshotMapper.findLatest(projectId, nodeId);
        if (snap == null) {
            return Result.success(null);
        }
        NodeModel node = nodeMapper.findById(nodeId);
        return Result.success(buildNodeInfo(snap, node));
    }

    /**
     * GET /api/monitor/app/history - 指标历史曲线
     */
    @GetMapping("/app/history")
    public Result<?> history(@RequestParam Long projectId,
                             @RequestParam(required = false) Long nodeId,
                             @RequestParam(required = false) Long startTime,
                             @RequestParam(required = false) Long endTime,
                             @RequestParam(defaultValue = "500") Integer limit) {
        if (!securityContext.hasProjectPermission(projectId)) {
            return Result.error(403, "无权限访问该项目");
        }
        List<MonitorSnapshotModel> history = snapshotMapper.findHistory(projectId, nodeId, startTime, endTime, limit);
        return Result.success(history);
    }

    /**
     * GET /api/monitor/app/stability - 7 天稳定性评分
     */
    @GetMapping("/app/stability")
    public Result<?> stability(@RequestParam Long projectId,
                               @RequestParam(required = false) Long nodeId) {
        if (!securityContext.hasProjectPermission(projectId)) {
            return Result.error(403, "无权限访问该项目");
        }
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("projectId", projectId);
        data.put("nodeId", nodeId);
        data.put("stabilityScore", calcStabilityScore(projectId, nodeId));
        data.put("periodDays", 7);
        return Result.success(data);
    }

    /**
     * GET /api/monitor/health-probe - 获取探针配置
     */
    @GetMapping("/health-probe")
    public Result<?> getHealthProbe(@RequestParam Long projectId) {
        if (!securityContext.hasProjectPermission(projectId)) {
            return Result.error(403, "无权限访问该项目");
        }
        return Result.success(healthProbeService.getByProjectId(projectId));
    }

    /**
     * POST /api/monitor/health-probe - 保存探针配置
     */
    @PostMapping("/health-probe")
    public Result<?> saveHealthProbe(@RequestBody ProjectHealthProbeModel probe) {
        if (probe.getProjectId() == null) {
            return Result.paramError("projectId 不能为空");
        }
        if (!securityContext.hasProjectPermission(probe.getProjectId())) {
            return Result.error(403, "无权限访问该项目");
        }
        return Result.success(healthProbeService.save(probe));
    }

    private Map<String, Object> buildNodeInfo(MonitorSnapshotModel snap, NodeModel node) {
        Map<String, Object> info = new HashMap<String, Object>();
        info.put("nodeId", snap.getNodeId());
        info.put("nodeName", node != null ? node.getName() : "");
        info.put("healthStatus", snap.getHealthStatus());
        info.put("healthDetail", snap.getHealthDetail());
        info.put("processStatus", snap.getProcessStatus());
        info.put("processPid", snap.getProcessPid());
        info.put("cpuPercent", snap.getCpuPercent());
        info.put("memoryMb", snap.getMemoryMb());
        info.put("heapUsedMb", snap.getHeapUsedMb());
        info.put("heapMaxMb", snap.getHeapMaxMb());
        info.put("hostCpuPercent", snap.getHostCpuPercent());
        info.put("hostMemoryPercent", snap.getHostMemoryPercent());
        info.put("diskUsagePercent", snap.getDiskUsagePercent());
        info.put("responseMs", snap.getResponseMs());
        info.put("collectTime", snap.getCollectTime());
        if ("DOWN".equals(snap.getHealthStatus()) || "DEGRADED".equals(snap.getHealthStatus())) {
            info.put("lastError", snap.getHealthDetail());
        } else if (snap.getHealthDetail() != null) {
            info.put("healthDetail", snap.getHealthDetail());
        }
        if (snap.getExtraJson() != null) {
            info.put("extraJson", snap.getExtraJson());
        }
        return info;
    }

    /**
     * GET /api/monitor/agent/status - Agent 状态列表（分页，从 DB 读取 Agent 上报的快照数据）
     * 不再逐个 HTTP 拉取 Agent，而是读取心跳上报时存入 monitor_snapshot 的最新数据。
     */
    @GetMapping("/agent/status")
    public Result<?> agentStatus(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String keyword) {
        List<NodeModel> nodes = nodeService.findByStatus(null, page, pageSize, keyword);
        Long total = nodeService.countByStatus(null, keyword);

        // 批量查询所有节点的最新快照（Agent 主动上报数据）
        Map<Long, MonitorSnapshotModel> snapMap = new HashMap<>();
        if (nodes != null && !nodes.isEmpty()) {
            List<Long> nodeIds = new ArrayList<>();
            for (NodeModel n : nodes) {
                if (n.getId() != null) nodeIds.add(n.getId());
            }
            if (!nodeIds.isEmpty()) {
                List<MonitorSnapshotModel> snapshots = snapshotMapper.findLatestByNodeIds(nodeIds);
                if (snapshots != null) {
                    for (MonitorSnapshotModel snap : snapshots) {
                        snapMap.put(snap.getNodeId(), snap);
                    }
                }
            }
        }

        List<Map<String, Object>> agentList = new ArrayList<>();
        if (nodes != null && !nodes.isEmpty()) {
            for (NodeModel n : nodes) {
                Map<String, Object> item = new HashMap<>();
                item.put("nodeId", n.getId());
                item.put("nodeName", n.getName());
                item.put("ip", n.getIp());
                item.put("port", n.getPort());
                item.put("osInfo", n.getOsInfo());
                item.put("cpuCores", n.getCpuCores());
                item.put("totalMemoryMb", n.getTotalMemoryMb());
                item.put("totalDiskMb", n.getTotalDiskMb());
                item.put("agentVersion", n.getAgentVersion());
                item.put("status", n.getStatus() != null ? n.getStatus() : 0);
                item.put("lastHeartbeat", n.getLastHeartbeat());

                // 从 Agent 上报的快照中读取实时指标
                MonitorSnapshotModel snap = snapMap.get(n.getId());
                if (snap != null) {
                    item.put("hostCpuPercent", snap.getHostCpuPercent());
                    item.put("hostMemoryPercent", snap.getHostMemoryPercent());
                    item.put("diskUsagePercent", snap.getDiskUsagePercent());
                    item.put("collectTime", snap.getCollectTime());
                }
                agentList.add(item);
            }
        }
        Map<String, Object> data = new HashMap<>();
        data.put("list", agentList);
        data.put("total", total);
        return Result.success(data);
    }

    private int calcStabilityScore(Long projectId, Long nodeId) {
        long sevenDaysAgo = System.currentTimeMillis() - 7L * 24 * 3600 * 1000;
        int downCount = snapshotMapper.countDownInRange(projectId, nodeId, sevenDaysAgo);
        double availability = Math.max(0, 1.0 - downCount / 100.0);
        int score = (int) (availability * 60 + Math.max(0, 20 - downCount * 2) + 20);
        return Math.min(100, Math.max(0, score));
    }
}
