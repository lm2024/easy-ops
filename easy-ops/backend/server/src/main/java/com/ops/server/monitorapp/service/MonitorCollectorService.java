package com.ops.server.monitorapp.service;

import com.ops.common.model.MonitorSnapshotModel;
import com.ops.common.model.NodeModel;
import com.ops.common.model.ProjectHealthProbeModel;
import com.ops.common.model.ProjectModel;
import com.ops.server.client.AgentClient;
import com.ops.server.config.GlobalPathProperties;
import com.ops.server.mapper.MonitorSnapshotMapper;
import com.ops.server.mapper.NodeMapper;
import com.ops.server.mapper.ProjectHealthProbeMapper;
import com.ops.server.mapper.ProjectMapper;
import com.alibaba.fastjson2.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 从 Agent 采集应用监控数据并写入快照表。
 * 默认使用 Shell(ps grep) 检测进程存活；配置探针后叠加 HTTP 健康检查。
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class MonitorCollectorService {

    private static final Logger log = LoggerFactory.getLogger(MonitorCollectorService.class);

    @Autowired
    private AgentClient agentClient;
    @Autowired
    private MonitorSnapshotMapper snapshotMapper;
    @Autowired
    private ProjectMapper projectMapper;
    @Autowired
    private NodeMapper nodeMapper;
    @Autowired
    private ProjectHealthProbeMapper probeMapper;
    @Autowired
    private GlobalPathProperties globalPathProperties;

    /**
     * 采集所有在线节点上的项目监控数据
     */
    public void collectAll() {
        List<ProjectModel> projects = projectMapper.findByFilters(null, null, null, 1, 1000);
        if (projects == null) {
            return;
        }
        for (ProjectModel project : projects) {
            if (project.getId() == null || project.getNodeIds() == null) {
                continue;
            }
            for (String nodeIdStr : project.getNodeIds().split(",")) {
                try {
                    Long nodeId = Long.parseLong(nodeIdStr.trim());
                    NodeModel node = nodeMapper.findById(nodeId);
                    if (node == null) {
                        continue;
                    }
                    // 即使心跳标记离线也尝试采集：进程检测以 Agent API 为准
                    collectOne(project, node);
                } catch (NumberFormatException e) {
                    log.warn("Invalid nodeId in project {}: {}", project.getId(), nodeIdStr);
                }
            }
        }
    }

    /**
     * 采集单个项目-节点监控快照
     */
    public MonitorSnapshotModel collectOne(ProjectModel project, NodeModel node) {
        MonitorSnapshotModel snap = new MonitorSnapshotModel();
        snap.setProjectId(project.getId());
        snap.setNodeId(node.getId());
        snap.setCollectTime(System.currentTimeMillis());

        String deployDir = resolveDeployDir(project);
        String jarName = project.getJarName();
        if (!StringUtils.hasText(jarName)) {
            jarName = project.getName() + ".jar";
        }

        Map<String, String> statusParams = new HashMap<String, String>();
        statusParams.put("deployDir", deployDir);
        statusParams.put("jarName", jarName);
        Map<String, Object> statusData = agentClient.get(node.getId(), "/process/status", statusParams);
        if (statusData == null) {
            statusData = new HashMap<String, Object>();
        }

        boolean alive = Boolean.TRUE.equals(statusData.get("alive"));
        Integer pid = toInt(statusData.get("pid"));
        snap.setProcessStatus(alive ? "RUNNING" : "STOPPED");
        snap.setProcessPid(pid);

        List<String> checkMethods = new ArrayList<String>();
        checkMethods.add("PS_GREP");

        String healthStatus = alive ? "UP" : "DOWN";
        String healthDetail = alive ? "Shell检测: 进程运行中" : "Shell检测: 进程未运行";
        Integer responseMs = null;

        ProjectHealthProbeModel probe = resolveProbe(project);
        if (alive && probe != null && probe.getEnabled() != null && probe.getEnabled() == 1
                && StringUtils.hasText(probe.getUrl())) {
            checkMethods.add("HTTP_PROBE");
            Map<String, String> probeParams = new HashMap<String, String>();
            probeParams.put("method", probe.getMethod() != null ? probe.getMethod() : "GET");
            probeParams.put("url", probe.getUrl());
            probeParams.put("expectedStatus", String.valueOf(
                    probe.getExpectedStatus() != null ? probe.getExpectedStatus() : 200));
            probeParams.put("timeoutMs", String.valueOf(
                    probe.getTimeoutMs() != null ? probe.getTimeoutMs() : 3000));
            if (probe.getBodyContains() != null) {
                probeParams.put("bodyContains", probe.getBodyContains());
            }
            Map<String, Object> probeData = agentClient.get(node.getId(), "/process/probe", probeParams);
            if (probeData == null) {
                probeData = new HashMap<String, Object>();
            }
            String probeStatus = probeData.get("status") != null ? probeData.get("status").toString() : "UNKNOWN";
            responseMs = toInt(probeData.get("responseMs"));
            snap.setResponseMs(responseMs);
            if ("UP".equals(probeStatus)) {
                healthStatus = "UP";
                healthDetail = "Shell+HTTP探针: 全部通过";
            } else if ("DOWN".equals(probeStatus)) {
                healthStatus = "DOWN";
                healthDetail = "Shell检测通过，但 HTTP 探针失败";
            } else {
                healthStatus = "DEGRADED";
                healthDetail = "Shell检测通过，HTTP 探针异常";
            }
        }

        snap.setHealthStatus(healthStatus);
        snap.setHealthDetail(healthDetail);
        Map<String, Object> extra = new HashMap<String, Object>();
        extra.put("checkMethods", checkMethods);
        extra.put("deployDir", deployDir);
        extra.put("jarName", jarName);
        snap.setExtraJson(JSON.toJSONString(extra));

        if (alive && StringUtils.hasText(deployDir) && StringUtils.hasText(jarName)) {
            Map<String, String> metricsParams = new HashMap<String, String>();
            metricsParams.put("deployDir", deployDir);
            metricsParams.put("jarName", jarName);
            Map<String, Object> metrics = agentClient.get(node.getId(), "/process/metrics", metricsParams);
            if (metrics != null) {
                snap.setCpuPercent(toDecimal(metrics.get("cpuPercent")));
                Integer memoryMb = toInt(metrics.get("memoryMb"));
                if (memoryMb == null) {
                    Long rssKb = toLong(metrics.get("rssKb"));
                    if (rssKb != null) {
                        memoryMb = (int) Math.round(rssKb / 1024.0);
                    }
                }
                snap.setMemoryMb(memoryMb);
                if (pid == null) {
                    pid = toInt(metrics.get("pid"));
                    snap.setProcessPid(pid);
                }
            }

            if (pid != null && pid > 0) {
                Map<String, String> jvmParams = new HashMap<String, String>();
                jvmParams.put("pid", String.valueOf(pid));
                Map<String, Object> jvm = agentClient.get(node.getId(), "/process/jvm", jvmParams);
                if (jvm != null) {
                    snap.setHeapUsedMb(toInt(jvm.get("heapUsedMb")));
                    snap.setHeapMaxMb(toInt(jvm.get("heapMaxMb")));
                    snap.setGcCount(toInt(jvm.get("gcYoungCount")));
                    snap.setGcTimeMs(toInt(jvm.get("gcTimeMs")));
                }
            }
        }

        Map<String, Object> sysInfo = agentClient.get(node.getId(), "/sys/info", null);
        if (sysInfo != null) {
            snap.setHostCpuPercent(toDecimal(sysInfo.get("cpuPercent")));
            snap.setHostMemoryPercent(toInt(sysInfo.get("memoryPercent")));
            snap.setDiskUsagePercent(toInt(sysInfo.get("diskUsagePercent")));
        }

        snapshotMapper.insert(snap);
        return snap;
    }

    /**
     * 删除过期快照（默认保留 30 天）
     */
    public int purgeOldSnapshots(int retainDays) {
        long cutoff = System.currentTimeMillis() - retainDays * 24L * 3600L * 1000L;
        return snapshotMapper.deleteBefore(cutoff);
    }

    private String resolveDeployDir(ProjectModel project) {
        if (project.getDeployDir() != null && !project.getDeployDir().trim().isEmpty()) {
            return project.getDeployDir().trim();
        }
        // 与 DeployController 默认路径一致
        return globalPathProperties.resolveAgentVersionDir(project.getId(), null);
    }

    private ProjectHealthProbeModel resolveProbe(ProjectModel project) {
        ProjectHealthProbeModel probe = probeMapper.findByProjectId(project.getId());
        if (probe != null && probe.getEnabled() != null && probe.getEnabled() == 1
                && StringUtils.hasText(probe.getUrl())) {
            return probe;
        }
        if (project.getHealthCheckEnabled() != null && project.getHealthCheckEnabled()
                && project.getHealthCheckPort() != null) {
            ProjectHealthProbeModel fallback = new ProjectHealthProbeModel();
            fallback.setProjectId(project.getId());
            fallback.setEnabled(1);
            fallback.setMethod("GET");
            int port = project.getHealthCheckPort();
            String path = project.getHealthCheckPath() != null ? project.getHealthCheckPath() : "/hello";
            if (!path.startsWith("/")) {
                path = "/" + path;
            }
            fallback.setUrl("http://127.0.0.1:" + port + path);
            fallback.setExpectedStatus(200);
            fallback.setTimeoutMs(3000);
            if (StringUtils.hasText(project.getHealthCheckKeyword())) {
                fallback.setBodyContains(project.getHealthCheckKeyword());
            }
            return fallback;
        }
        return probe;
    }

    private Integer toInt(Object val) {
        if (val == null) {
            return null;
        }
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        try {
            return Integer.parseInt(val.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long toLong(Object val) {
        if (val == null) {
            return null;
        }
        if (val instanceof Number) {
            return ((Number) val).longValue();
        }
        try {
            return Long.parseLong(val.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal toDecimal(Object val) {
        if (val == null) {
            return null;
        }
        if (val instanceof BigDecimal) {
            return (BigDecimal) val;
        }
        try {
            return new BigDecimal(val.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
