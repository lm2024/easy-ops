package com.ops.server.controller;

import com.alibaba.fastjson2.JSON;
import com.ops.common.enums.DeployStatus;
import com.ops.common.model.DeployModel;
import com.ops.common.model.NodeModel;
import com.ops.common.model.ProjectModel;
import com.ops.common.model.VersionModel;
import com.ops.common.response.Result;
import com.ops.server.mapper.*;
import com.ops.server.config.GlobalPathProperties;
import com.ops.server.websocket.DeployHandler;
import com.ops.server.service.AuditLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@RestController
@RequestMapping("/deploy")
public class DeployController {

    private static final Logger log = LoggerFactory.getLogger(DeployController.class);

    @Autowired
    private DeployRecordMapper deployRecordMapper;

    @Autowired
    private ProjectMapper projectMapper;

    @Autowired
    private NodeMapper nodeMapper;

    @Autowired
    private VersionPackageMapper versionPackageMapper;

    @Autowired
    private GlobalPathProperties globalPathProperties;

    @Autowired
    private DeployHandler deployHandler;

    @Autowired
    private AuditLogService auditLog;

    @Value("${server.path:./data}")
    private String serverPath;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 部署锁：projectId -> lock
     * 同一项目同一时刻只允许一个部署请求
     */
    private final ConcurrentHashMap<Long, ReentrantLock> deployLocks = new ConcurrentHashMap<>();
    /** 锁获取时间戳，用于超时自动释放 */
    private final ConcurrentHashMap<Long, Long> deployLockTimestamps = new ConcurrentHashMap<>();
    /** 锁最大持有时间 2 分钟，超时自动释放 */
    private static final long LOCK_TIMEOUT_MS = 2 * 60 * 1000;

    // ======================== 步骤定义 ========================
    private static final String[] STEP_NAMES = {"stop", "transfer", "start", "health"};
    private static final String[] STEP_LABELS = {"停止旧进程", "传输文件", "启动应用", "健康检查"};

    // ======================== 发布接口 ========================

    /**
     * POST /api/deploy - 发起部署
     * 立即返回 deployId，后台异步执行部署并通过 WebSocket 推送实时进度
     */
    @PostMapping
    public Result<?> publish(@RequestBody Map<String, Object> request) {
        Long projectId = request.get("projectId") != null ? Long.valueOf(request.get("projectId").toString()) : null;
        Long versionId = request.get("versionId") != null ? Long.valueOf(request.get("versionId").toString()) : null;
        Long nodeId = request.get("nodeId") != null ? Long.valueOf(request.get("nodeId").toString()) : null;
        Long scheduleTime = request.get("scheduleTime") != null ? Long.valueOf(request.get("scheduleTime").toString()) : null;

        if (projectId == null || versionId == null) {
            return Result.paramError("项目和版本ID不能为空");
        }

        // 并发控制
        ReentrantLock lock = deployLocks.computeIfAbsent(projectId, k -> new ReentrantLock());
        boolean acquired = lock.tryLock();

        if (!acquired) {
            // 检查锁是否超时（防止死锁）
            Long lockTime = deployLockTimestamps.get(projectId);
            long heldMs = lockTime != null ? System.currentTimeMillis() - lockTime : -1;
            log.warn("[Deploy] 锁冲突: projectId={}, 已持有 {}ms", projectId, heldMs);

            if (heldMs > LOCK_TIMEOUT_MS) {
                log.warn("[Deploy] 锁超时 {}ms，强制释放 projectId={}", heldMs, projectId);
                lock.unlock();
                deployLockTimestamps.remove(projectId);
                // 重新尝试获取
                acquired = lock.tryLock();
            }

            if (!acquired) {
                log.warn("[Deploy] 拒绝部署: projectId={}, 锁仍被持有 {}ms", projectId, heldMs);
                String hint = heldMs > LOCK_TIMEOUT_MS
                        ? "部署锁已超时但仍无法释放，请重启后端服务"
                        : "请等待当前部署完成后再试（已等待 " + (heldMs / 1000) + " 秒）";
                return Result.error(1009, "该项目正在部署中。" + hint);
            }
        }

        // 记录锁获取时间
        deployLockTimestamps.put(projectId, System.currentTimeMillis());
        log.info("[Deploy] 获取锁: projectId={}, deployId 即将生成", projectId);

        // 获取项目 & 版本
        ProjectModel project = projectMapper.findById(projectId);
        VersionModel version = versionPackageMapper.findById(versionId);
        if (project == null) { lock.unlock(); deployLockTimestamps.remove(projectId); log.warn("[Deploy] 项目不存在: projectId={}", projectId); return Result.error(1005, "项目不存在"); }
        if (version == null) { lock.unlock(); deployLockTimestamps.remove(projectId); log.warn("[Deploy] 版本不存在: versionId={}", versionId); return Result.error(1004, "版本不存在"); }

        // 获取目标节点
        String nodeIdsStr = project.getNodeIds();
        if (nodeIdsStr == null || nodeIdsStr.trim().isEmpty()) {
            lock.unlock(); deployLockTimestamps.remove(projectId);
            log.warn("[Deploy] 项目未绑定节点: projectId={}", projectId);
            return Result.error(1005, "项目未绑定节点");
        }
        List<Long> targetNodeIds = new ArrayList<>();
        if (nodeId != null) {
            targetNodeIds.add(nodeId);
        } else {
            for (String s : nodeIdsStr.split(",")) {
                String trimmed = s.trim();
                if (!trimmed.isEmpty()) targetNodeIds.add(Long.parseLong(trimmed));
            }
        }

        // ====== 定时部署分支（不需要异步） ======
        if (scheduleTime != null && scheduleTime > System.currentTimeMillis()) {
            lock.unlock();
            for (Long nid : targetNodeIds) {
                DeployModel scheduledDeploy = new DeployModel();
                scheduledDeploy.setProjectId(projectId);
                scheduledDeploy.setVersionId(versionId);
                scheduledDeploy.setNodeId(nid);
                scheduledDeploy.setStatus(DeployStatus.SCHEDULED.getCode());
                scheduledDeploy.setJarName(version.getJarName());
                scheduledDeploy.setScheduleTime(scheduleTime);
                scheduledDeploy.setLog("⏰ 定时部署任务已创建\n计划执行时间: " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(scheduleTime)));
                scheduledDeploy.setStartTime(System.currentTimeMillis());
                scheduledDeploy.setCreateTime(System.currentTimeMillis());
                deployRecordMapper.insert(scheduledDeploy);
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("deployId", "");
            data.put("status", DeployStatus.SCHEDULED.getCode());
            data.put("message", "⏰ 定时部署已创建，" + targetNodeIds.size() + " 个节点将在 " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(scheduleTime)) + " 自动执行");
            data.put("scheduleTime", scheduleTime);
            return Result.success(data);
        }

        // ====== 立即部署：生成 deployId，后台异步执行 ======
        String deployId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        log.info("[Deploy] 开始异步部署: deployId={}, projectId={}, nodes={}", deployId, projectId, targetNodeIds.size());
        auditLog.log("DEPLOY", "DEPLOY", "发起部署: 项目=" + project.getName() + ", 版本=" + version.getVersion() + ", 节点数=" + targetNodeIds.size());

        // 异步执行部署
        final Long finalProjectId = projectId;
        final Long finalVersionId = versionId;
        final List<Long> finalNodeIds = targetNodeIds;
        new Thread(() -> {
            try {
                doDeployAsync(deployId, finalProjectId, finalVersionId, finalNodeIds, project, version);
                log.info("[Deploy] 异步部署完成: deployId={}", deployId);
            } catch (Exception e) {
                log.error("[Deploy] 异步部署异常: deployId={}", deployId, e);
                try {
                    pushStep(deployId, -1L, "?", "error", "exception", -1, e.getMessage());
                    pushDone(deployId, DeployStatus.FAILED.getCode(), "❌ 部署异常: " + e.getMessage(), Collections.emptyList());
                } catch (Exception pushEx) {
                    log.error("[Deploy] 推送失败消息异常: deployId={}", deployId, pushEx);
                }
            } finally {
                try {
                    lock.unlock();
                    deployLockTimestamps.remove(finalProjectId);
                    log.info("[Deploy] 释放锁: projectId={}", finalProjectId);
                } catch (Exception unlockEx) {
                    log.error("[Deploy] 释放锁异常: projectId={}", finalProjectId, unlockEx);
                }
                deployHandler.cleanup(deployId);
            }
        }, "deploy-" + deployId).start();

        // 立即返回 deployId
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("deployId", deployId);
        data.put("status", DeployStatus.PROCESSING.getCode());
        data.put("message", "部署已启动，请通过 WebSocket 获取实时进度");
        return Result.success(data);
    }

    // ======================== 异步部署逻辑 ========================

    private void doDeployAsync(String deployId, Long projectId, Long versionId,
                               List<Long> targetNodeIds, ProjectModel project, VersionModel version) {

        String agentFileDir = globalPathProperties.resolveAgentVersionDir(projectId, version.getVersion());
        String deployDir = project.getDeployDir();
        if (deployDir == null || deployDir.isEmpty()) deployDir = agentFileDir;
        String jarName = version.getJarName() != null ? version.getJarName() : "app.jar";
        String agentFilePath = agentFileDir + "/" + jarName;
        boolean isFrontendDeploy = "frontend".equalsIgnoreCase(version.getPackageType())
                || jarName.toLowerCase().endsWith(".zip");
        String frontendDir = globalPathProperties.resolveFrontendDir(deployDir, project.getFrontendDeployDir());

        String startScript = project.getStartScript();
        if (startScript != null && project.getJarName() != null && !project.getJarName().isEmpty()) {
            startScript = startScript.replaceAll("JAR_NAME=\\S+", "JAR_NAME=" + project.getJarName());
        }
        String stopScript = project.getStopScript();

        boolean allSuccess = true;
        List<Map<String, Object>> nodeResults = new ArrayList<>();
        StringBuilder fullLog = new StringBuilder();

        for (int nodeIdx = 0; nodeIdx < targetNodeIds.size(); nodeIdx++) {
            Long nid = targetNodeIds.get(nodeIdx);
            NodeModel node = nodeMapper.findById(nid);
            if (node == null) {
                Map<String, Object> nr = new LinkedHashMap<>();
                nr.put("nodeId", nid); nr.put("nodeName", "?"); nr.put("success", false); nr.put("message", "节点不存在");
                nodeResults.add(nr);
                allSuccess = false;
                continue;
            }

            String agentIp = node.getIp() != null ? node.getIp() : "127.0.0.1";
            int agentPort = node.getPort() != null ? node.getPort() : 2123;
            String agentBase = "http://" + agentIp + ":" + agentPort;

            // 创建部署记录
            DeployModel deploy = new DeployModel();
            deploy.setProjectId(projectId);
            deploy.setVersionId(versionId);
            deploy.setNodeId(nid);
            deploy.setStatus(DeployStatus.PROCESSING.getCode());
            deploy.setJarName(version.getJarName());
            deploy.setStartTime(System.currentTimeMillis());
            deploy.setCreateTime(System.currentTimeMillis());
            deployRecordMapper.insert(deploy);

            Map<String, Object> nodeResult = new LinkedHashMap<>();
            nodeResult.put("nodeId", nid);
            nodeResult.put("nodeName", node.getName());
            nodeResult.put("recordId", deploy.getId());
            StringBuilder nodeLog = new StringBuilder();

            try {
                if (isFrontendDeploy) {
                    // 前端部署：传输 + 解压
                    pushStep(deployId, nid, node.getName(), "running", "transfer", nodeIdx, "正在传输前端包...");
                    String jarPath = findJarPath(projectId, version);
                    java.io.File zipFile = new java.io.File(jarPath);
                    if (!zipFile.exists()) throw new RuntimeException("前端包不存在: " + jarPath);

                    String uploadUrl = agentBase + "/api/file/receive";
                    HttpHeaders fileHeaders = new HttpHeaders();
                    fileHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
                    MultiValueMap<String, Object> fileBody = new LinkedMultiValueMap<>();
                    fileBody.add("file", new FileSystemResource(zipFile));
                    fileBody.add("projectId", String.valueOf(projectId));
                    fileBody.add("versionName", version.getVersion());
                    restTemplate.postForEntity(uploadUrl, new HttpEntity<>(fileBody, fileHeaders), String.class);

                    String unzipUrl = agentBase + "/api/file/unzip";
                    Map<String, String> unzipReq = new HashMap<>();
                    unzipReq.put("zipPath", agentFileDir + "/" + jarName);
                    unzipReq.put("targetDir", frontendDir);
                    restTemplate.postForEntity(unzipUrl, unzipReq, String.class);

                    nodeLog.append("✅ 前端部署成功: ").append(frontendDir).append("\n");
                    deployRecordMapper.updateStatus(deploy.getId(), DeployStatus.SUCCESS.getCode(), nodeLog.toString(), System.currentTimeMillis());
                    pushStep(deployId, nid, node.getName(), "done", "transfer", nodeIdx, "✅ 前端部署成功");
                    nodeResult.put("success", true);
                    nodeResult.put("message", "前端部署成功");
                } else {
                    // 后端部署：4 个步骤

                    // STEP 1: 停旧进程
                    pushStep(deployId, nid, node.getName(), "running", "stop", nodeIdx, "正在停止旧进程...");
                    String stopUrl = agentBase + "/api/process/" + projectId + "/stop";
                    Map<String, String> stopReq = new HashMap<>();
                    stopReq.put("stopScript", stopScript != null ? stopScript : "");
                    stopReq.put("deployDir", deployDir);
                    try { restTemplate.postForEntity(stopUrl, stopReq, String.class); } catch (Exception ignored) {}
                    nodeLog.append("✅ 停止完成\n");
                    pushStep(deployId, nid, node.getName(), "done", "stop", nodeIdx, "✅ 停止完成");

                    // STEP 2: 传输文件
                    pushStep(deployId, nid, node.getName(), "running", "transfer", nodeIdx, "正在传输 Jar 包...");
                    String jarPath = findJarPath(projectId, version);
                    java.io.File jarFile = new java.io.File(jarPath);
                    if (!jarFile.exists()) throw new RuntimeException("Jar包不存在: " + jarPath);

                    String uploadUrl = agentBase + "/api/file/receive";
                    HttpHeaders fileHeaders = new HttpHeaders();
                    fileHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
                    MultiValueMap<String, Object> fileBody = new LinkedMultiValueMap<>();
                    fileBody.add("file", new FileSystemResource(jarFile));
                    fileBody.add("projectId", String.valueOf(projectId));
                    fileBody.add("versionName", version.getVersion());
                    ResponseEntity<String> uploadResp = restTemplate.postForEntity(uploadUrl, new HttpEntity<>(fileBody, fileHeaders), String.class);
                    nodeLog.append("✅ 传输完成: ").append(uploadResp.getStatusCode()).append("\n");
                    pushStep(deployId, nid, node.getName(), "done", "transfer", nodeIdx, "✅ 传输完成 (" + (jarFile.length()/1024) + "KB)");

                    // STEP 3: 启动
                    pushStep(deployId, nid, node.getName(), "running", "start", nodeIdx, "正在启动应用...");
                    String startUrl = agentBase + "/api/process/" + projectId + "/start";
                    Map<String, String> startReq = new HashMap<>();
                    startReq.put("startScript", startScript != null ? startScript : "");
                    startReq.put("deployDir", deployDir);
                    startReq.put("jarPath", agentFilePath);
                    startReq.put("jarName", project.getJarName() != null ? project.getJarName() : jarName);
                    restTemplate.postForEntity(startUrl, startReq, String.class);
                    nodeLog.append("✅ 启动完成\n");
                    pushStep(deployId, nid, node.getName(), "done", "start", nodeIdx, "✅ 启动完成");

                    // STEP 4: 健康检查
                    pushStep(deployId, nid, node.getName(), "running", "health", nodeIdx, "正在健康检查...");
                    boolean healthy = false;
                    boolean hcEnabled = project.getHealthCheckEnabled() == null || project.getHealthCheckEnabled();
                    if (!hcEnabled) {
                        healthy = true;
                        nodeLog.append("⏭️ 健康检查已关闭\n");
                        pushStep(deployId, nid, node.getName(), "done", "health", nodeIdx, "⏭️ 健康检查已关闭");
                    } else {
                        int hcPort = project.getHealthCheckPort() != null ? project.getHealthCheckPort() : 8080;
                        String hcPath = (project.getHealthCheckPath() != null && !project.getHealthCheckPath().isEmpty()) ? project.getHealthCheckPath() : "/hello";
                        String hcKeywordRaw = (project.getHealthCheckKeyword() != null && !project.getHealthCheckKeyword().isEmpty()) ? project.getHealthCheckKeyword() : "Hello,DEPLOYED";
                        String healthCmd = "curl -s --max-time 3 http://127.0.0.1:" + hcPort + hcPath;
                        String[] hcKeywords = hcKeywordRaw.split(",");
                        String shellUrl = agentBase + "/api/shell/exec";

                        for (int i = 1; i <= 5; i++) {
                            pushStep(deployId, nid, node.getName(), "running", "health", nodeIdx, "第 " + i + " 次健康检查...");
                            Thread.sleep(3000);
                            try {
                                Map<String, String> cmdReq = new HashMap<>();
                                cmdReq.put("command", healthCmd);
                                @SuppressWarnings("unchecked")
                                Map<String, Object> shellResp = restTemplate.postForObject(shellUrl, cmdReq, Map.class);
                                String output = "";
                                if (shellResp != null && shellResp.get("data") instanceof Map) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> dm = (Map<String, Object>) shellResp.get("data");
                                    output = dm.get("stdout") != null ? dm.get("stdout").toString() : "";
                                }
                                boolean matched = false;
                                for (String kw : hcKeywords) {
                                    if (output.contains(kw.trim())) { matched = true; break; }
                                }
                                if (matched) {
                                    healthy = true;
                                    nodeLog.append("✅ 第 ").append(i).append(" 次检查通过\n");
                                    pushStep(deployId, nid, node.getName(), "done", "health", nodeIdx, "✅ 第 " + i + " 次检查通过");
                                    break;
                                }
                            } catch (Exception e) {
                                nodeLog.append("⏳ 第 ").append(i).append(" 次检查: ").append(e.getMessage()).append("\n");
                            }
                        }
                    }

                    if (healthy) {
                        nodeResult.put("success", true);
                        nodeResult.put("message", "✅ 部署成功");
                        nodeLog.append("✅ [").append(node.getName()).append("] 部署成功\n");
                        deployRecordMapper.updateStatus(deploy.getId(), DeployStatus.SUCCESS.getCode(), nodeLog.toString(), System.currentTimeMillis());
                    } else {
                        nodeResult.put("success", false);
                        nodeResult.put("message", "❌ 健康检查未通过");
                        nodeLog.append("❌ 健康检查未通过\n");
                        deployRecordMapper.updateStatus(deploy.getId(), DeployStatus.FAILED.getCode(), nodeLog.toString(), System.currentTimeMillis());
                        pushStep(deployId, nid, node.getName(), "failed", "health", nodeIdx, "❌ 健康检查未通过");
                        allSuccess = false;
                    }
                }
            } catch (Exception e) {
                nodeResult.put("success", false);
                nodeResult.put("message", "❌ 部署失败: " + e.getMessage());
                nodeLog.append("❌ 部署失败: ").append(e.getMessage()).append("\n");
                deployRecordMapper.updateStatus(deploy.getId(), DeployStatus.FAILED.getCode(), nodeLog.toString(), System.currentTimeMillis());
                pushStep(deployId, nid, node.getName(), "failed", "error", nodeIdx, "❌ " + e.getMessage());
                allSuccess = false;
            }

            fullLog.append(nodeLog);
            nodeResults.add(nodeResult);
        }

        // 汇总并推送完成消息
        int successCount = 0;
        for (Map<String, Object> nr : nodeResults) {
            if (Boolean.TRUE.equals(nr.get("success"))) successCount++;
        }
        int finalStatus = allSuccess ? DeployStatus.SUCCESS.getCode() : DeployStatus.FAILED.getCode();
        String message = allSuccess
                ? "✅ " + successCount + "/" + targetNodeIds.size() + " 节点部署成功"
                : "⚠️ " + successCount + "/" + targetNodeIds.size() + " 节点成功，其余失败";

        pushDone(deployId, finalStatus, message, nodeResults);
    }

    // ======================== WebSocket 推送工具方法 ========================

    /** 推送单个节点的步骤状态 */
    private void pushStep(String deployId, Long nodeId, String nodeName,
                          String status, String step, int nodeIndex, String detail) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("type", "node-step");
        msg.put("nodeId", nodeId);
        msg.put("nodeName", nodeName);
        msg.put("status", status);     // running / done / failed
        msg.put("step", step);         // stop / transfer / start / health
        msg.put("stepIndex", indexOfStep(step));
        msg.put("nodeIndex", nodeIndex);
        msg.put("detail", detail);
        deployHandler.push(deployId, JSON.toJSONString(msg));
    }

    /** 推送部署完成消息 */
    private void pushDone(String deployId, int status, String message, List<Map<String, Object>> nodeResults) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("type", "deploy-done");
        msg.put("deployId", deployId);
        msg.put("status", status);
        msg.put("message", message);
        msg.put("nodeResults", nodeResults);
        deployHandler.push(deployId, JSON.toJSONString(msg));
    }

    private int indexOfStep(String step) {
        for (int i = 0; i < STEP_NAMES.length; i++) {
            if (STEP_NAMES[i].equals(step)) return i;
        }
        return -1;
    }

    // ======================== 其他接口（不变） ========================

    private String findJarPath(Long projectId, VersionModel version) {
        String jarPath = version.getFilePath();
        if (jarPath != null && !jarPath.isEmpty()) {
            java.io.File f = new java.io.File(jarPath);
            if (f.exists()) {
                if (f.isDirectory()) {
                    String fullPath = jarPath + "/" + version.getJarName();
                    if (new java.io.File(fullPath).exists()) return fullPath;
                } else {
                    return jarPath;
                }
            }
        }
        String jarName = version.getJarName() != null ? version.getJarName() : "app.jar";
        String base = serverPath + "/versions/" + projectId;
        String[] candidates = {
                base + "/" + version.getId() + "/" + jarName,
                base + "/" + version.getVersion() + "/" + jarName,
                base + "/" + jarName
        };
        for (String p : candidates) {
            if (new java.io.File(p).exists()) return p;
        }
        return candidates[1];
    }

    @GetMapping
    public Result<?> listRecords(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        log.info("[Deploy] 查询历史: projectId={}, page={}, pageSize={}", projectId, page, pageSize);
        List<DeployModel> records = deployRecordMapper.findByProjectId(projectId, page, pageSize);
        Long total = deployRecordMapper.countByProjectId(projectId);
        log.info("[Deploy] 查询结果: total={}, records={}", total, records != null ? records.size() : 0);
        Map<String, Object> data = new HashMap<>();
        data.put("list", records);
        data.put("total", total);
        return Result.success(data);
    }

    /**
     * POST /api/deploy/unlock/{projectId} - 强制释放部署锁（卡住时使用）
     */
    @PostMapping("/unlock/{projectId}")
    public Result<?> forceUnlock(@PathVariable Long projectId) {
        ReentrantLock lock = deployLocks.get(projectId);
        if (lock != null && lock.isLocked()) {
            log.warn("[Deploy] 强制释放锁: projectId={}", projectId);
            lock.unlock();
            deployLockTimestamps.remove(projectId);
            return Result.success("锁已释放: projectId=" + projectId);
        }
        return Result.success("该应用没有被锁住: projectId=" + projectId);
    }

    @GetMapping("/{id}")
    public Result<?> getDetail(@PathVariable Long id) {
        DeployModel record = deployRecordMapper.findById(id);
        if (record == null) return Result.error(500, "部署记录不存在");
        return Result.success(record);
    }

    @PostMapping("/{id}/rollback")
    public Result<?> rollback(@PathVariable Long id) {
        DeployModel record = deployRecordMapper.findById(id);
        if (record == null) return Result.error(500, "部署记录不存在");
        ReentrantLock lock = deployLocks.computeIfAbsent(record.getProjectId(), k -> new ReentrantLock());
        boolean acquired = lock.tryLock();
        if (!acquired) {
            return Result.error(1009, "该项目正在部署中，请等待当前部署完成后再回滚");
        }
        try {
            auditLog.log("DEPLOY", "ROLLBACK", "回滚部署: 记录ID=" + id + ", 项目ID=" + record.getProjectId());
            return doRollback(id, record);
        } finally {
            lock.unlock();
        }
    }

    private Result<?> doRollback(Long id, DeployModel record) {
        Long previousVersionId = record.getVersionId() - 1;
        if (previousVersionId < 1) previousVersionId = 1L;
        VersionModel previousVersion = versionPackageMapper.findById(previousVersionId);
        if (previousVersion == null) {
            return Result.error(1004, "找不到上一个版本 (ID: " + previousVersionId + ")，无法回滚");
        }
        ProjectModel project = projectMapper.findById(record.getProjectId());
        NodeModel node = nodeMapper.findById(record.getNodeId());
        if (project == null) return Result.error(1005, "项目不存在");
        if (node == null) return Result.error(1002, "节点不存在");

        DeployModel rollbackRecord = new DeployModel();
        rollbackRecord.setProjectId(record.getProjectId());
        rollbackRecord.setVersionId(previousVersionId);
        rollbackRecord.setNodeId(record.getNodeId());
        rollbackRecord.setStatus(DeployStatus.PROCESSING.getCode());
        rollbackRecord.setJarName(previousVersion.getJarName());
        rollbackRecord.setStartTime(System.currentTimeMillis());
        rollbackRecord.setCreateTime(System.currentTimeMillis());
        deployRecordMapper.insert(rollbackRecord);
        final Long recordId = rollbackRecord.getId();
        final String agentIp = node.getIp() != null ? node.getIp() : "127.0.0.1";
        final int agentPort = node.getPort() != null ? node.getPort() : 2123;
        final String agentBase = "http://" + agentIp + ":" + agentPort;

        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> steps = new ArrayList<>();
        result.put("recordId", recordId);
        result.put("nodeName", node.getName());
        result.put("nodeIp", agentIp);
        result.put("jarName", previousVersion.getJarName());
        result.put("rollback", true);

        try {
            Map<String, Object> step1 = new LinkedHashMap<>();
            step1.put("name", "📦 查找上一个版本 Jar 包");
            String jarPath = findJarPath(record.getProjectId(), previousVersion);
            java.io.File jarFile = new java.io.File(jarPath);
            step1.put("success", jarFile.exists());
            step1.put("detail", "版本: " + previousVersion.getVersion() + "\n路径: " + jarPath + "\n大小: " + (jarFile.exists() ? (jarFile.length() / 1024) + "KB" : "文件不存在"));
            steps.add(step1);
            if (!jarFile.exists()) throw new RuntimeException("Jar包文件不存在: " + jarPath);

            Map<String, Object> step2 = new LinkedHashMap<>();
            step2.put("name", "📤 传输文件到 Agent");
            String uploadUrl = agentBase + "/api/file/receive";
            String deployDir = serverPath + "/versions/" + record.getProjectId() + "/" + previousVersion.getVersion();
            HttpHeaders fileHeaders = new HttpHeaders();
            fileHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
            MultiValueMap<String, Object> fileBody = new LinkedMultiValueMap<>();
            fileBody.add("file", new FileSystemResource(jarFile));
            fileBody.add("projectId", String.valueOf(record.getProjectId()));
            fileBody.add("versionName", previousVersion.getVersion());
            ResponseEntity<String> uploadResp = restTemplate.postForEntity(uploadUrl, new HttpEntity<>(fileBody, fileHeaders), String.class);
            step2.put("success", uploadResp.getStatusCode().is2xxSuccessful());
            step2.put("detail", "传输状态: " + uploadResp.getStatusCode());
            steps.add(step2);

            Map<String, Object> step3 = new LinkedHashMap<>();
            step3.put("name", "🚀 启动进程");
            String agentStartUrl = agentBase + "/api/process/" + record.getProjectId() + "/start";
            String jvmOpts = project.getJvmOpts();
            if (jvmOpts == null || jvmOpts.isEmpty()) jvmOpts = "-Xms256m -Xmx512m -XX:+UseG1GC";
            Map<String, String> startReq = new HashMap<>();
            startReq.put("jarPath", deployDir + "/" + previousVersion.getJarName());
            startReq.put("jvmOpts", jvmOpts);
            if (project.getEnvVars() != null && !project.getEnvVars().isEmpty()) startReq.put("envVars", project.getEnvVars());
            ResponseEntity<String> startResp = restTemplate.postForEntity(agentStartUrl, startReq, String.class);
            step3.put("success", startResp.getStatusCode().is2xxSuccessful());
            step3.put("detail", "回滚版本: " + previousVersion.getVersion() + "\nJVM参数: " + jvmOpts);
            steps.add(step3);

            Map<String, Object> step4 = new LinkedHashMap<>();
            step4.put("name", "🏥 健康检查");
            boolean healthy = false;
            String healthDetail = "";
            boolean hcEnabled = project.getHealthCheckEnabled() == null || project.getHealthCheckEnabled();
            if (!hcEnabled) {
                healthy = true;
                healthDetail = "⏭️ 健康检查已关闭";
            } else {
                int appPort = project.getHealthCheckPort() != null ? project.getHealthCheckPort() : 8080;
                String hcPath = (project.getHealthCheckPath() != null && !project.getHealthCheckPath().isEmpty()) ? project.getHealthCheckPath() : "/hello";
                String hcKeywordRaw = (project.getHealthCheckKeyword() != null && !project.getHealthCheckKeyword().isEmpty()) ? project.getHealthCheckKeyword() : "Hello,DEPLOYED";
                String healthCmd = "curl -s --max-time 3 http://127.0.0.1:" + appPort + hcPath;
                String[] hcKeywords = hcKeywordRaw.split(",");
                String shellUrl = agentBase + "/api/shell/exec";
                for (int i = 1; i <= 5; i++) {
                    Thread.sleep(3000);
                    try {
                        Map<String, String> cmdReq = new HashMap<>();
                        cmdReq.put("command", healthCmd);
                        @SuppressWarnings("unchecked")
                        Map<String, Object> shellResp = restTemplate.postForObject(shellUrl, cmdReq, Map.class);
                        String output = "";
                        if (shellResp != null && shellResp.get("data") instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> dm = (Map<String, Object>) shellResp.get("data");
                            output = dm.get("stdout") != null ? dm.get("stdout").toString() : "";
                        }
                        boolean matched = false;
                        for (String kw : hcKeywords) {
                            if (output.contains(kw.trim())) { matched = true; break; }
                        }
                        if (matched) {
                            healthy = true;
                            healthDetail = "✅ 回滚应用已启动 (第 " + i + " 次检查)";
                            break;
                        }
                    } catch (Exception e) {
                        healthDetail = "⏳ 第 " + i + " 次检查: " + e.getMessage();
                    }
                }
            }
            step4.put("success", healthy);
            step4.put("detail", healthDetail);
            steps.add(step4);

            if (healthy) {
                result.put("status", DeployStatus.ROLLBACK_DONE.getCode());
                result.put("message", "✅ 回滚成功！已切换至版本: " + previousVersion.getVersion());
            } else {
                result.put("status", DeployStatus.FAILED.getCode());
                result.put("message", "⚠️ 回滚完成但健康检查未通过");
            }
        } catch (Exception e) {
            Map<String, Object> errorStep = new LinkedHashMap<>();
            errorStep.put("name", "❌ 异常"); errorStep.put("success", false);
            errorStep.put("detail", e.getMessage() != null ? e.getMessage() : "未知错误");
            steps.add(errorStep);
            result.put("status", DeployStatus.FAILED.getCode());
            result.put("message", "❌ 回滚失败: " + e.getMessage());
        }

        StringBuilder fullLog = new StringBuilder();
        for (Map<String, Object> s : steps) {
            fullLog.append("═══════════════════════════════════════\n");
            fullLog.append(s.get("name")).append("\n");
            fullLog.append("─────────────────────────────────────\n");
            fullLog.append(s.get("detail")).append("\n");
        }
        int finalStatus = (int) result.get("status");
        deployRecordMapper.updateStatus(recordId, finalStatus, fullLog.toString(), System.currentTimeMillis());
        result.put("log", fullLog.toString());
        result.put("steps", steps);
        return Result.success(result);
    }

    @PostMapping("/{id}/cancel")
    public Result<?> cancel(@PathVariable Long id) {
        DeployModel record = deployRecordMapper.findById(id);
        if (record == null) return Result.error(500, "部署记录不存在");
        if (record.getStatus() != 5) return Result.paramError("只有待部署状态的记录才能取消");
        deployRecordMapper.updateStatus(id, 3, "⛔ 已手动取消定时部署", System.currentTimeMillis());
        auditLog.log("DEPLOY", "CANCEL", "取消定时部署: 记录ID=" + id);
        return Result.success();
    }
}
