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
import com.ops.server.selfheal.service.NotificationService;
import com.ops.server.controller.AlarmController;
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
import java.util.concurrent.ConcurrentHashMap;

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
    @Autowired
    private NotificationService notificationService;

    /** 告警去重：projectId-nodeId-condition → 上次告警时间 */
    private final ConcurrentHashMap<String, Long> alarmCooldown = new ConcurrentHashMap<>();

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
     * 按筛选条件采集指定项目和节点的监控数据。
     * 只采集在线的节点，降低网络开销。
     */
    public void collectFiltered(List<Long> projectIds, List<Long> nodeIds) {
        if ((projectIds == null || projectIds.isEmpty()) && (nodeIds == null || nodeIds.isEmpty())) {
            collectAll();
            return;
        }
        List<ProjectModel> projects;
        if (projectIds != null && !projectIds.isEmpty()) {
            projects = new ArrayList<ProjectModel>();
            for (Long pid : projectIds) {
                ProjectModel p = projectMapper.findById(pid);
                if (p != null) projects.add(p);
            }
        } else {
            projects = projectMapper.findByFilters(null, null, null, 1, 1000);
        }
        for (ProjectModel project : projects) {
            if (project.getId() == null || project.getNodeIds() == null) continue;
            for (String nodeIdStr : project.getNodeIds().split(",")) {
                try {
                    Long nid = Long.parseLong(nodeIdStr.trim());
                    if (nodeIds != null && !nodeIds.isEmpty() && !nodeIds.contains(nid)) continue;
                    NodeModel node = nodeMapper.findById(nid);
                    if (node != null) collectOne(project, node);
                } catch (NumberFormatException ignored) {}
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

        // 进程在运行 → 健康；进程不在 → 异常
        String healthStatus = alive ? "UP" : "DOWN";
        String healthDetail = alive ? "Shell检测: 进程运行中" : "Shell检测: 进程未运行";
        Integer responseMs = null;

        // HTTP 探针：只补充信息，不改变健康状态（进程活着就是健康）
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
            String probeDetail = probeData.get("detail") != null ? probeData.get("detail").toString() : "";
            responseMs = toInt(probeData.get("responseMs"));
            snap.setResponseMs(responseMs);
            if ("UP".equals(probeStatus)) {
                healthDetail = "Shell+HTTP探针: 全部通过";
            } else {
                // 探针失败不影响健康状态，但记录原因供排查
                healthDetail = "进程运行中，HTTP探针未通过: " + diagnoseProbeFailure(probeDetail, probe);
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
            // Agent 返回 cpuUsagePercent / memoryUsagePercent，不是 cpuPercent / memoryPercent
            snap.setHostCpuPercent(toDecimal(sysInfo.get("cpuUsagePercent")));
            snap.setHostMemoryPercent(toInt(sysInfo.get("memoryUsagePercent")));
            // diskUsagePercent 取根分区的使用率
            Object disks = sysInfo.get("disks");
            if (disks instanceof java.util.List) {
                for (Object d : (java.util.List<?>) disks) {
                    if (d instanceof Map) {
                        Object mount = ((Map<?, ?>) d).get("mountPoint");
                        if ("/".equals(mount)) {
                            snap.setDiskUsagePercent(toInt(((Map<?, ?>) d).get("usagePercent")));
                            break;
                        }
                    }
                }
            }
        }

        snapshotMapper.insert(snap);
        // 检查异常并生成通知
        checkAndAlarm(snap, project, node);
        return snap;
    }

    /**
     * 检查监控快照中的异常条件，自动生成平台通知（所有用户可见）
     * 阈值从告警配置中读取，可通过 /api/alarms/config 动态调整
     */
    private void checkAndAlarm(MonitorSnapshotModel snap, ProjectModel project, NodeModel node) {
        Map<String, Object> config = AlarmController.getConfig();
        String projectName = project.getName();
        String nodeName = node.getName();

        // 冷却时间
        int cooldownMinutes = config.containsKey("cooldownMinutes") ? ((Number) config.get("cooldownMinutes")).intValue() : 30;
        long cooldownMs = cooldownMinutes * 60L * 1000;

        // 1. 健康检查失败
        if (isTrue(config, "healthCheckEnabled") && "DOWN".equals(snap.getHealthStatus())) {
            String key = project.getId() + "-" + node.getId() + "-DOWN";
            if (shouldAlarm(key, cooldownMs)) {
                notificationService.createBroadcastNotification(
                        "ALERT", "CRITICAL",
                        "【" + projectName + "】" + nodeName + " 异常",
                        "健康状态: DOWN\n" + snap.getHealthDetail(),
                        project.getId(), node.getId(), "MONITOR");
            }
        }

        // 2. 健康降级
        if (isTrue(config, "healthCheckEnabled") && "DEGRADED".equals(snap.getHealthStatus())) {
            String key = project.getId() + "-" + node.getId() + "-DEGRADED";
            if (shouldAlarm(key, cooldownMs)) {
                notificationService.createBroadcastNotification(
                        "ALERT", "WARNING",
                        "【" + projectName + "】" + nodeName + " 降级",
                        "健康状态: DEGRADED\n" + snap.getHealthDetail(),
                        project.getId(), node.getId(), "MONITOR");
            }
        }

        // 3. CPU 过高
        int cpuThreshold = config.containsKey("cpuThreshold") ? ((Number) config.get("cpuThreshold")).intValue() : 90;
        if (isTrue(config, "cpuEnabled") && snap.getHostCpuPercent() != null
                && snap.getHostCpuPercent().compareTo(new BigDecimal(cpuThreshold)) > 0) {
            String key = project.getId() + "-" + node.getId() + "-CPU";
            if (shouldAlarm(key, cooldownMs)) {
                notificationService.createBroadcastNotification(
                        "ALERT", "WARNING",
                        "【" + projectName + "】" + nodeName + " CPU 过高",
                        "主机 CPU 使用率: " + snap.getHostCpuPercent() + "% (阈值: " + cpuThreshold + "%)",
                        project.getId(), node.getId(), "MONITOR");
            }
        }

        // 4. 响应超时
        int responseThreshold = config.containsKey("responseThreshold") ? ((Number) config.get("responseThreshold")).intValue() : 5000;
        if (isTrue(config, "responseEnabled") && snap.getResponseMs() != null
                && snap.getResponseMs() > responseThreshold) {
            String key = project.getId() + "-" + node.getId() + "-TIMEOUT";
            if (shouldAlarm(key, cooldownMs)) {
                notificationService.createBroadcastNotification(
                        "ALERT", "WARNING",
                        "【" + projectName + "】" + nodeName + " 响应超时",
                        "响应时间: " + snap.getResponseMs() + "ms (阈值: " + responseThreshold + "ms)",
                        project.getId(), node.getId(), "MONITOR");
            }
        }
    }

    private boolean isTrue(Map<String, Object> config, String key) {
        Object val = config.get(key);
        return val instanceof Boolean ? (Boolean) val : Boolean.parseBoolean(String.valueOf(val));
    }

    /**
     * 将探针原始错误信息转换为用户可读的诊断描述
     */
    private String diagnoseProbeFailure(String rawDetail, ProjectHealthProbeModel probe) {
        if (rawDetail == null || rawDetail.isEmpty()) {
            return "未知原因";
        }
        String lower = rawDetail.toLowerCase();
        String probeUrl = (probe != null && probe.getUrl() != null) ? probe.getUrl() : "";

        // 连接被拒绝 — 应用未监听该端口
        if (lower.contains("connection refused") || lower.contains("connectexception")) {
            return "连接被拒绝 — 应用可能未监听该端口 (" + probeUrl + ")";
        }
        // 连接超时
        if (lower.contains("connect timed out") || lower.contains("sockettimeoutexception") && lower.contains("connect")) {
            return "连接超时 — 网络不通或防火墙拦截 (" + probeUrl + ")";
        }
        // 读取超时
        if (lower.contains("read timed out") || (lower.contains("sockettimeoutexception") && lower.contains("read"))) {
            return "读取超时 — 应用响应过慢 (>3s)";
        }
        // HTTP 状态码不匹配
        if (lower.contains("期望状态码") || lower.contains("expectedstatus")) {
            return rawDetail;
        }
        // 关键字不匹配
        if (lower.contains("bodycontains") || lower.contains("关键字")) {
            return "响应内容未匹配预期关键字";
        }
        // 主机不可达
        if (lower.contains("no route to host") || lower.contains("unreachable")) {
            return "主机不可达 — 网络不通";
        }
        // DNS 解析失败
        if (lower.contains("unknownhostexception")) {
            return "域名解析失败 — 请检查 URL 配置";
        }
        // URL 格式错误
        if (lower.contains("malformedurlexception") || lower.contains("invalid url")) {
            return "URL 格式错误 (" + probeUrl + ")";
        }
        // SSL/TLS 错误
        if (lower.contains("ssl") || lower.contains("certificate") || lower.contains("handshake")) {
            return "SSL/TLS 握手失败 — 证书问题";
        }
        // 其他
        return rawDetail.length() > 80 ? rawDetail.substring(0, 80) + "..." : rawDetail;
    }

    /** 检查是否在冷却期内，避免重复告警 */
    private boolean shouldAlarm(String key, long cooldownMs) {
        long now = System.currentTimeMillis();
        Long lastAlarm = alarmCooldown.get(key);
        if (lastAlarm != null && (now - lastAlarm) < cooldownMs) {
            return false;
        }
        alarmCooldown.put(key, now);
        return true;
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
