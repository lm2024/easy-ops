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
import com.ops.server.config.GlobalPathProperties;
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
    private com.ops.server.mapper.ProjectMapper projectMapper;

    @Autowired
    private SecurityContext securityContext;

    @Autowired
    private AgentUpgradeService agentUpgradeService;

    @Autowired
    private com.ops.server.client.AgentClient agentClient;

    @Autowired
    private GlobalPathProperties globalPathProperties;

    /**
     * GET /api/nodes - 节点列表 (支持分页和状态筛选)
     */
    @GetMapping
    public Result<?> listNodes(
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortOrder) {
        List<NodeModel> nodes = nodeService.findByStatus(status, page, pageSize, keyword, sortField, sortOrder);
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
            List<NodeModel> nodes = nodeService.findByStatus(null, 1, Integer.MAX_VALUE, null, null, null);
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
        String agentPidStr = request.getHeader("X-Agent-PID");
        String metricsBase64 = request.getHeader("X-Metrics");

        // 解析硬件信息
        Integer cpuCores = null;
        Integer totalMemoryMb = null;
        Long totalDiskMb = null;
        Long agentPid = null;
        try {
            if (cpuInfo != null && !cpuInfo.isEmpty()) cpuCores = Integer.parseInt(cpuInfo);
            if (memInfo != null && !memInfo.isEmpty()) totalMemoryMb = Integer.parseInt(memInfo);
            if (diskInfo != null && !diskInfo.isEmpty()) totalDiskMb = Long.parseLong(diskInfo);
            if (agentPidStr != null && !agentPidStr.isEmpty()) agentPid = Long.parseLong(agentPidStr);
        } catch (NumberFormatException ignored) {}

        nodeMapper.updateHeartbeat(Long.parseLong(nodeId), System.currentTimeMillis(),
                ip, osInfo, javaVersion, cpuCores, totalMemoryMb, totalDiskMb, osArch, agentVersion, agentPid);

        // 保存主机级实时指标到node_info（供Agent状态页面使用，不依赖monitor_snapshot）
        if (metricsBase64 != null && !metricsBase64.isEmpty()) {
            try {
                String metricsJson = new String(java.util.Base64.getDecoder().decode(metricsBase64), "UTF-8");
                @SuppressWarnings("unchecked")
                Map<String, Object> metrics = com.alibaba.fastjson2.JSON.parseObject(metricsJson, Map.class);
                if (metrics != null) {
                    Double cpuPercent = metrics.get("cpuUsagePercent") instanceof Number ? ((Number) metrics.get("cpuUsagePercent")).doubleValue() : null;
                    Integer memPercent = metrics.get("memoryUsagePercent") instanceof Number ? ((Number) metrics.get("memoryUsagePercent")).intValue() : null;
                    Integer diskPercent = metrics.get("diskUsagePercent") instanceof Number ? ((Number) metrics.get("diskUsagePercent")).intValue() : null;
                    nodeMapper.updateHostMetrics(Long.parseLong(nodeId), cpuPercent, memPercent, diskPercent);
                }
            } catch (Exception ignored) {}
        }

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
                    // 保存磁盘信息到节点表
                    String diskInfoJson = (String) metrics.get("diskInfoJson");
                    log.info("[Heartbeat] 节点={} diskInfoJson={}", nodeId, diskInfoJson != null ? diskInfoJson.substring(0, Math.min(50, diskInfoJson.length())) : "null");
                    if (diskInfoJson != null && !diskInfoJson.isEmpty()) {
                        try {
                            nodeMapper.updateDiskInfo(Long.parseLong(nodeId), diskInfoJson);
                            log.info("[Heartbeat] updateDiskInfo成功 节点={}", nodeId);
                        } catch (Exception ex) {
                            log.error("[Heartbeat] updateDiskInfo失败 节点={}", nodeId, ex);
                        }
                    }
                    // 存储到MonitorSnapshot表，同时返回计算结果用于WS推送
                    Map<Long, Map<String, Object>> computed = saveMonitorSnapshot(Long.parseLong(nodeId), metrics);
                    broadcastMonitorUpdate(Long.parseLong(nodeId), metrics, computed);
                }
            } catch (Exception e) {
                log.warn("监控指标解析失败 节点={}", nodeId, e);
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
     * 保存监控快照到数据库（为节点上每个项目各生成一条快照）
     * 返回 Map<projectId, computed>，按项目维度供 WS 广播，
     * 解决「同节点多项目时 WS 只带最后一个项目状态、会串到其他项目」的问题。
     */
    private Map<Long, Map<String, Object>> saveMonitorSnapshot(Long nodeId, Map<String, Object> metrics) {
        Map<Long, Map<String, Object>> computedPerProject = new java.util.HashMap<>();
        try {
            List<Long> projectIds = nodeMapper.getProjectIdsByNodeId(nodeId);
            if (projectIds == null || projectIds.isEmpty()) {
                return computedPerProject;
            }

            for (Long projectId : projectIds) {
                // jarName 校验下沉到 saveOneSnapshot 内部，避免重复查 DB
                Map<String, Object> projectComputed = saveOneSnapshot(nodeId, projectId, metrics);
                if (projectComputed != null && !projectComputed.isEmpty()) {
                    computedPerProject.put(projectId, projectComputed);
                }
            }
        } catch (Exception e) {
            log.warn("监控快照保存失败 节点={}", nodeId, e);
        }
        return computedPerProject;
    }

    /**
     * 为单个项目保存一条监控快照，返回该项目的 computed 供 WS 推送
     */
    private Map<String, Object> saveOneSnapshot(Long nodeId, Long projectId, Map<String, Object> metrics) {
        Map<String, Object> computed = new java.util.HashMap<>();
        try {
            // UPSERT：查找已有快照并更新，避免每次心跳都 INSERT 新行导致表膨胀
            com.ops.common.model.MonitorSnapshotModel snap = snapshotMapper.findLatest(projectId, nodeId);
            if (snap == null) {
                snap = new com.ops.common.model.MonitorSnapshotModel();
                snap.setProjectId(projectId);
                snap.setNodeId(nodeId);
            }
            snap.setCollectTime(System.currentTimeMillis());

            // 解析CPU使用率（现在是真实的系统CPU使用率，不是负载转换值）
            Object cpuUsage = metrics.get("cpuUsagePercent");
            if (cpuUsage instanceof Number) {
                double val = ((Number) cpuUsage).doubleValue();
                snap.setHostCpuPercent(new java.math.BigDecimal(val));
                // 心跳上报时，如果没有应用进程数据，用Agent自身CPU作为进程CPU
                snap.setCpuPercent(new java.math.BigDecimal(val));
            }

            // 解析内存使用率（现在是真实的系统内存使用率，不是JVM内存使用率）
            Object memUsage = metrics.get("memoryUsagePercent");
            if (memUsage instanceof Number) {
                snap.setHostMemoryPercent(((Number) memUsage).intValue());
            }

            // 解析磁盘使用率
            Object diskUsage = metrics.get("diskUsagePercent");
            if (diskUsage instanceof Number) {
                snap.setDiskUsagePercent(((Number) diskUsage).intValue());
            }

            // ======================== 进程状态（先确定） ========================
            com.ops.common.model.ProjectModel snapProject = getProject(snap.getProjectId());
            boolean isFrontend = snapProject != null && "frontend".equalsIgnoreCase(snapProject.getProjectType());
            if (isFrontend) {
                // 前端静态资源没有 Java 进程，直接标记 N/A
                snap.setProcessStatus("N/A");
                snap.setHealthStatus("UP");
                snap.setHealthDetail("静态资源，无需进程存活监控");
                // 前端项目不需要匹配进程，直接跳过进程处理
                // 只更新主机级指标（CPU/内存/磁盘）
                return computed;
            }
            // 默认 STOPPED，由 processes 列表或上次快照覆盖
            snap.setProcessStatus("STOPPED");

            // 解析应用进程指标（Agent 心跳上报）
            // 按 jarName + deployDir 双重匹配，避免同节点多应用时取错 PID/堆内存
            Object processesObj = metrics.get("processes");
            if (processesObj instanceof List) {
                List<?> processes = (List<?>) processesObj;
                if (!processes.isEmpty()) {
                    com.ops.common.model.ProjectModel project = getProject(snap.getProjectId());
                    String expectedJarName = (project != null && project.getJarName() != null) ? project.getJarName().trim() : "";
                    String expectedDeployDir = resolveDeployDir(project);
                    if (expectedJarName.isEmpty()) {
                        log.debug("[Monitor] 跳过未配置jarName的项目 节点={} 项目={}", nodeId, snap.getProjectId());
                    } else {
                        Map<?, ?> matched = findProcessByJarNameAndDeployDir(processes, expectedJarName, expectedDeployDir);
                        if (matched != null) {
                            Object pidObj = matched.get("pid");
                            if (pidObj instanceof Number) {
                                snap.setProcessPid(((Number) pidObj).intValue());
                            }
                            Object procCpu = matched.get("cpuPercent");
                            if (procCpu instanceof Number) {
                                snap.setCpuPercent(new java.math.BigDecimal(((Number) procCpu).doubleValue()));
                            }
                            Object procMem = matched.get("memoryMb");
                            if (procMem instanceof Number) {
                                snap.setMemoryMb(((Number) procMem).intValue());
                            }
                            Object procHeapUsed = matched.get("heapUsedMb");
                            if (procHeapUsed instanceof Number) {
                                snap.setHeapUsedMb(((Number) procHeapUsed).intValue());
                            }
                            // 无 heap 数据时保留已有值（jstat 可能暂时不可用，不覆盖 collectOne 的正确数据）
                            Object procHeapMax = matched.get("heapMaxMb");
                            if (procHeapMax instanceof Number) {
                                snap.setHeapMaxMb(((Number) procHeapMax).intValue());
                            }
                            Object xmxObj = matched.get("xmxMb");
                            if (xmxObj instanceof Number) computed.put("xmxMb", ((Number) xmxObj).intValue());
                            Object gcCount = matched.get("gcCount");
                            if (gcCount instanceof Number) snap.setGcCount(((Number) gcCount).intValue());
                            Object gcTime = matched.get("gcTimeMs");
                            if (gcTime instanceof Number) snap.setGcTimeMs(((Number) gcTime).intValue());
                            Object alive = matched.get("alive");
                            if (Boolean.TRUE.equals(alive)) {
                                snap.setProcessStatus("RUNNING");
                            } else {
                                snap.setProcessStatus("STOPPED");
                            }
                        } else {
                            // 未匹配到进程：清除堆内存，不能保留上一次快照的错误数据
                            snap.setHeapUsedMb(null);
                            snap.setHeapMaxMb(null);
                        }
                    }
                } else {
                    // processes 列表为空，进程未运行
                    markStopped(snap);
                    snap.setHeapUsedMb(null);
                    snap.setHeapMaxMb(null);
                }
            } else {
                // processes 字段不存在，进程未运行
                markStopped(snap);
                snap.setHeapUsedMb(null);
                snap.setHeapMaxMb(null);
            }

            // ======================== 健康状态（进程状态确定后再判断） ========================
            // 三个字段联动：进程状态 ↔ 健康状态 ↔ 应用PID
            // - RUNNING → 看资源：CPU/内存高 → DEGRADED，正常 → UP
            // - STOPPED → DOWN
            if ("STOPPED".equals(snap.getProcessStatus())) {
                snap.setHealthStatus("DOWN");
                snap.setHealthDetail("应用进程已停止");
                snap.setProcessPid(null); // STOPPED 时清空 PID，保证前端三个字段一致
            } else {
                // RUNNING：根据主机资源判断
                double cpuPercent = snap.getHostCpuPercent() != null ? snap.getHostCpuPercent().doubleValue() : 0;
                int memPercent = snap.getHostMemoryPercent() != null ? snap.getHostMemoryPercent() : 0;
                if (cpuPercent > 90 || memPercent > 90) {
                    snap.setHealthStatus("DEGRADED");
                    snap.setHealthDetail("CPU=" + cpuPercent + "%, Memory=" + memPercent + "%");
                } else {
                    snap.setHealthStatus("UP");
                    snap.setHealthDetail("Agent主动上报");
                }
            }

            // 收集计算结果，供 WS 推送（与 DB 同源，不会不一致）
            computed.put("processStatus", snap.getProcessStatus());
            computed.put("processPid", snap.getProcessPid());
            computed.put("healthStatus", snap.getHealthStatus());
            computed.put("healthDetail", snap.getHealthDetail());
            if (snap.getCpuPercent() != null) computed.put("cpuPercent", snap.getCpuPercent());
            if (snap.getMemoryMb() != null) computed.put("memoryMb", snap.getMemoryMb());
            if (snap.getHeapUsedMb() != null) computed.put("heapUsedMb", snap.getHeapUsedMb());
            if (snap.getHeapMaxMb() != null) computed.put("heapMaxMb", snap.getHeapMaxMb());

            // 存储到数据库（有 ID 则更新，无 ID 则插入）
            if (snap.getId() != null) {
                snapshotMapper.update(snap);
            } else {
                snapshotMapper.insert(snap);
            }
            log.debug("监控快照 节点={} 项目={} 进程={} 健康={} PID={} 主机CPU={}% 进程CPU={}% 主机内存={}% 进程内存={}MB 堆={}/{}MB 磁盘={}% 详情={}",
                    nodeId, snap.getProjectId(), snap.getProcessStatus(), snap.getHealthStatus(),
                    snap.getProcessPid(),
                    fmt(snap.getHostCpuPercent()),
                    fmt(snap.getCpuPercent()),
                    snap.getHostMemoryPercent(),
                    snap.getMemoryMb(),
                    snap.getHeapUsedMb(), snap.getHeapMaxMb(),
                    snap.getDiskUsagePercent(),
                    snap.getHealthDetail());
        } catch (Exception e) {
            log.warn("监控快照保存失败 节点={} 项目={}", nodeId, projectId, e);
        }
        return computed;
    }

    /**
     * 标记进程为 STOPPED：进程列表为空或不存在，说明没在运行。
     */
    private void markStopped(com.ops.common.model.MonitorSnapshotModel snap) {
        snap.setProcessStatus("STOPPED");
        snap.setProcessPid(null);
    }

    /**
     * 通过WebSocket广播监控实时指标（CPU/内存/磁盘等高频数据）。
     * 同时按 projectId 维度推送 saveMonitorSnapshot 算好的状态字段（PID/进程状态/健康/堆），
     * 前端可按 projectId 精确 patch 到对应行，保证 WS 与 DB 同源一致。
     */
    private void broadcastMonitorUpdate(Long nodeId, Map<String, Object> metrics, Map<Long, Map<String, Object>> computed) {
        try {
            Map<String, Object> message = new java.util.HashMap<>();
            message.put("type", "monitor_update");
            message.put("nodeId", nodeId);
            message.put("metrics", metrics);
            message.put("timestamp", System.currentTimeMillis());
            // 按 projectId 维度推状态字段（与 DB 同源），前端按 projectId 精确匹配行
            if (computed != null && !computed.isEmpty()) {
                message.put("computed", computed);
            }

            String json = com.alibaba.fastjson2.JSON.toJSONString(message);
            monitorHandler.broadcast("monitor", json);
        } catch (Exception e) {
            log.warn("监控广播失败 节点={}", nodeId, e);
        }
    }

    /** BigDecimal 格式化为 1 位小数，null 返回 "?" */
    private String fmt(java.math.BigDecimal val) {
        if (val == null) return "?";
        return val.setScale(1, java.math.RoundingMode.HALF_UP).toString();
    }

    /** 获取项目配置对象（缓存单次请求内重复查询） */
    private com.ops.common.model.ProjectModel getProject(Long projectId) {
        if (projectId == null || projectId == 0L) return null;
        try {
            return projectMapper.findById(projectId);
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 解析项目的部署目录（与 MonitorCollectorService 保持一致） */
    private String resolveDeployDir(com.ops.common.model.ProjectModel project) {
        if (project == null) return "";
        if (project.getDeployDir() != null && !project.getDeployDir().trim().isEmpty()) {
            return project.getDeployDir().trim();
        }
        return globalPathProperties.resolveDeployDir(project.getName());
    }

    /** 在 processes 列表中按 jarName + deployDir 双重匹配进程 */
    @SuppressWarnings("unchecked")
    private Map<?, ?> findProcessByJarNameAndDeployDir(List<?> processes, String expectedJarName, String expectedDeployDir) {
        if (expectedJarName == null || expectedJarName.isEmpty()) return null;
        boolean hasExpectedDeployDir = expectedDeployDir != null && !expectedDeployDir.isEmpty();
        for (Object obj : processes) {
            if (obj instanceof Map) {
                Map<String, Object> proc = (Map<String, Object>) obj;
                String jarName = (String) proc.get("jarName");
                if (!expectedJarName.equals(jarName)) continue;
                // jarName 匹配后，有 deployDir 就用 deployDir 二次验证
                if (hasExpectedDeployDir) {
                    String procDeployDir = (String) proc.get("deployDir");
                    if (procDeployDir != null && !procDeployDir.isEmpty()) {
                        // 路径比较：支持精确匹配和子路径匹配
                        if (procDeployDir.equals(expectedDeployDir)
                                || procDeployDir.endsWith("/" + expectedDeployDir)
                                || expectedDeployDir.endsWith("/" + procDeployDir)
                                || procDeployDir.contains(expectedDeployDir)
                                || expectedDeployDir.contains(procDeployDir)) {
                            return proc;
                        }
                        continue; // deployDir 不匹配，跳过
                    }
                    // Agent 版本较旧没有 deployDir，回退到只按 jarName 匹配（降级兼容）
                }
                return proc;
            }
        }
        return null;
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
