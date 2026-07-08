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

        List<MonitorSnapshotModel> snapshots = snapshotMapper.findLatestByProject(projectId);
        int up = 0, down = 0, degraded = 0, totalResponse = 0, responseCount = 0;
        List<Map<String, Object>> nodes = new ArrayList<Map<String, Object>>();

        if (snapshots != null) {
            for (MonitorSnapshotModel snap : snapshots) {
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
                NodeModel node = nodeMapper.findById(snap.getNodeId());
                Map<String, Object> nodeInfo = buildNodeInfo(snap, node);
                nodes.add(nodeInfo);
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
        data.put("summary", summary);
        data.put("nodes", nodes);
        return Result.success(data);
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
        info.put("processStatus", snap.getProcessStatus());
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
