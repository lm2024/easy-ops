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
import com.ops.server.util.SecurityContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
     * GET /api/monitor/app/dashboard - 全部应用监控总览（应用管理中的每个项目）
     */
    @GetMapping("/app/dashboard")
    public Result<?> dashboard() {
        List<ProjectModel> projects = projectMapper.findByFilters(null, null, null, 1, 1000);
        List<Map<String, Object>> projectList = new ArrayList<Map<String, Object>>();
        int totalApps = 0, totalUp = 0, totalDown = 0, totalDegraded = 0;

        if (projects != null) {
            for (ProjectModel project : projects) {
                if (project.getId() == null || !securityContext.hasProjectPermission(project.getId())) {
                    continue;
                }
                Map<String, Object> item = buildProjectOverview(project);
                projectList.add(item);
                @SuppressWarnings("unchecked")
                Map<String, Object> summary = (Map<String, Object>) item.get("summary");
                totalApps += ((Number) summary.get("totalNodes")).intValue();
                totalUp += ((Number) summary.get("upCount")).intValue();
                totalDown += ((Number) summary.get("downCount")).intValue();
                totalDegraded += ((Number) summary.get("degradedCount")).intValue();
            }
        }

        Map<String, Object> globalSummary = new HashMap<String, Object>();
        globalSummary.put("totalProjects", projectList.size());
        globalSummary.put("totalInstances", totalApps);
        globalSummary.put("upCount", totalUp);
        globalSummary.put("downCount", totalDown);
        globalSummary.put("degradedCount", totalDegraded);

        Map<String, Object> data = new HashMap<String, Object>();
        data.put("summary", globalSummary);
        data.put("projects", projectList);
        data.put("collectIntervalSec", collectConfigService.getIntervalSec());
        return Result.success(data);
    }

    /**
     * POST /api/monitor/app/collect - 立即采集全部应用监控数据
     */
    @PostMapping("/app/collect")
    public Result<?> collectNow() {
        collectorService.collectAll();
        return Result.success("采集完成");
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

    private int calcStabilityScore(Long projectId, Long nodeId) {
        long sevenDaysAgo = System.currentTimeMillis() - 7L * 24 * 3600 * 1000;
        int downCount = snapshotMapper.countDownInRange(projectId, nodeId, sevenDaysAgo);
        double availability = Math.max(0, 1.0 - downCount / 100.0);
        int score = (int) (availability * 60 + Math.max(0, 20 - downCount * 2) + 20);
        return Math.min(100, Math.max(0, score));
    }
}
