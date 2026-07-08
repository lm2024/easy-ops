package com.ops.server.monitorapp.service;

import com.ops.common.enums.NodeStatus;
import com.ops.common.model.MonitorSnapshotModel;
import com.ops.common.model.NodeModel;
import com.ops.common.model.ProjectHealthProbeModel;
import com.ops.common.model.ProjectModel;
import com.ops.server.client.AgentClient;
import com.ops.server.mapper.MonitorSnapshotMapper;
import com.ops.server.mapper.NodeMapper;
import com.ops.server.mapper.ProjectHealthProbeMapper;
import com.ops.server.mapper.ProjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 从 Agent 采集应用监控数据并写入快照表
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

    /**
     * 采集所有在线节点上的项目监控数据
     */
    public void collectAll() {
        List<ProjectModel> projects = projectMapper.findByFilters(null, null, 1, 1000);
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
                    if (node == null || node.getStatus() == null
                            || node.getStatus() != NodeStatus.ONLINE.getCode()) {
                        continue;
                    }
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

        Map<String, String> statusParams = new HashMap<String, String>();
        statusParams.put("deployDir", project.getDeployDir());
        statusParams.put("jarName", project.getJarName());
        Map<String, Object> statusData = agentClient.get(node.getId(), "/process/status", statusParams);

        boolean alive = Boolean.TRUE.equals(statusData.get("alive"));
        Integer pid = toInt(statusData.get("pid"));
        snap.setProcessStatus(alive ? "RUNNING" : "STOPPED");
        snap.setProcessPid(pid);

        ProjectHealthProbeModel probe = probeMapper.findByProjectId(project.getId());
        String healthStatus = alive ? "UP" : "DOWN";
        String healthDetail = alive ? "进程运行中" : "进程未运行";
        Integer responseMs = null;

        if (alive && probe != null && probe.getEnabled() != null && probe.getEnabled() == 1) {
            Map<String, String> probeParams = new HashMap<String, String>();
            probeParams.put("method", probe.getMethod());
            probeParams.put("url", probe.getUrl());
            probeParams.put("expectedStatus", String.valueOf(probe.getExpectedStatus()));
            probeParams.put("timeoutMs", String.valueOf(probe.getTimeoutMs()));
            if (probe.getBodyContains() != null) {
                probeParams.put("bodyContains", probe.getBodyContains());
            }
            Map<String, Object> probeData = agentClient.get(node.getId(), "/process/probe", probeParams);
            String probeStatus = probeData.get("status") != null ? probeData.get("status").toString() : "UNKNOWN";
            responseMs = toInt(probeData.get("responseMs"));
            snap.setResponseMs(responseMs);
            if ("UP".equals(probeStatus)) {
                healthStatus = "UP";
                healthDetail = "HTTP 探针通过";
            } else if ("DOWN".equals(probeStatus)) {
                healthStatus = "DOWN";
                healthDetail = "HTTP 探针失败";
            } else {
                healthStatus = "DEGRADED";
                healthDetail = "HTTP 探针异常";
            }
        } else if (alive && responseMs != null && responseMs > 3000) {
            healthStatus = "DEGRADED";
            healthDetail = "响应过慢";
        }

        snap.setHealthStatus(healthStatus);
        snap.setHealthDetail(healthDetail);

        if (alive && pid != null) {
            Map<String, String> metricsParams = new HashMap<String, String>();
            metricsParams.put("pid", String.valueOf(pid));
            Map<String, Object> metrics = agentClient.get(node.getId(), "/process/metrics", metricsParams);
            snap.setCpuPercent(toDecimal(metrics.get("cpuPercent")));
            snap.setMemoryMb(toInt(metrics.get("memoryMb")));

            Map<String, String> jvmParams = new HashMap<String, String>();
            jvmParams.put("pid", String.valueOf(pid));
            Map<String, Object> jvm = agentClient.get(node.getId(), "/process/jvm", jvmParams);
            snap.setHeapUsedMb(toInt(jvm.get("heapUsedMb")));
            snap.setHeapMaxMb(toInt(jvm.get("heapMaxMb")));
            snap.setGcCount(toInt(jvm.get("gcYoungCount")));
            snap.setGcTimeMs(toInt(jvm.get("gcTimeMs")));
        }

        Map<String, Object> sysInfo = agentClient.get(node.getId(), "/sys/info", null);
        snap.setHostCpuPercent(toDecimal(sysInfo.get("cpuPercent")));
        snap.setHostMemoryPercent(toInt(sysInfo.get("memoryPercent")));
        snap.setDiskUsagePercent(toInt(sysInfo.get("diskUsagePercent")));

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
