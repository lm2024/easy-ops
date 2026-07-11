package com.ops.server.controller;

import com.ops.common.enums.DeployStatus;
import com.ops.common.model.DeployModel;
import com.ops.common.model.NodeModel;
import com.ops.common.model.ProjectModel;
import com.ops.common.model.VersionModel;
import com.ops.common.response.Result;
import com.ops.server.mapper.*;
import com.ops.server.config.GlobalPathProperties;
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

@RestController
@RequestMapping("/deploy")
public class DeployController {

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

    @Value("${server.path:./data}")
    private String serverPath;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * POST /api/deploy - 发布版本
     * 完整流程：创建记录 → 传输Jar到Agent → 启动进程 → 健康检查 → 返回完整结果
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

        // 获取项目 & 版本 & 节点信息
        ProjectModel project = projectMapper.findById(projectId);
        VersionModel version = versionPackageMapper.findById(versionId);
        if (project == null) return Result.error(1005, "项目不存在");
        if (version == null) return Result.error(1004, "版本不存在");

        // 获取项目配置的所有节点
        String nodeIdsStr = project.getNodeIds();
        if (nodeIdsStr == null || nodeIdsStr.trim().isEmpty()) {
            return Result.error(1005, "项目未绑定节点");
        }
        String[] nodeIdArr = nodeIdsStr.split(",");
        // 如果指定了 nodeId 就用它，否则部署到所有节点
        List<Long> targetNodeIds = new ArrayList<>();
        if (nodeId != null) {
            targetNodeIds.add(nodeId);
        } else {
            for (String s : nodeIdArr) {
                String trimmed = s.trim();
                if (!trimmed.isEmpty()) targetNodeIds.add(Long.parseLong(trimmed));
            }
        }

        // ====== 定时部署分支 ======
        if (scheduleTime != null && scheduleTime > System.currentTimeMillis()) {
            // 为每个节点创建定时部署记录
            for (Long nid : targetNodeIds) {
                DeployModel scheduledDeploy = new DeployModel();
                scheduledDeploy.setProjectId(projectId);
                scheduledDeploy.setVersionId(versionId);
                scheduledDeploy.setNodeId(nid);
                scheduledDeploy.setStatus(DeployStatus.SCHEDULED.getCode());
                scheduledDeploy.setJarName(version.getJarName());
                scheduledDeploy.setScheduleTime(scheduleTime);
                scheduledDeploy.setLog("⏰ 定时部署任务已创建，等待执行...\n计划执行时间: " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(scheduleTime)));
                scheduledDeploy.setStartTime(System.currentTimeMillis());
                scheduledDeploy.setCreateTime(System.currentTimeMillis());
                deployRecordMapper.insert(scheduledDeploy);
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("recordId", 0);
            data.put("status", DeployStatus.SCHEDULED.getCode());
            data.put("message", "⏰ 定时部署已创建，" + targetNodeIds.size() + " 个节点将在 " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(scheduleTime)) + " 自动执行");
            data.put("scheduleTime", scheduleTime);
            return Result.success(data);
        }

        // Agent 上 jar 文件的实际存储路径（由 file/receive 决定）
        String agentFileDir = globalPathProperties.resolveAgentVersionDir(projectId, version.getVersion());
        // 项目的 deployDir（用户可自定义），默认与 jar 存放目录一致
        String deployDir = project.getDeployDir();
        if (deployDir == null || deployDir.isEmpty()) {
            deployDir = agentFileDir;
        }
        String jarName = version.getJarName() != null ? version.getJarName() : "app.jar";
        String agentFilePath = agentFileDir + "/" + jarName;
        boolean isFrontendDeploy = "frontend".equalsIgnoreCase(version.getPackageType())
                || jarName.toLowerCase().endsWith(".zip");
        String frontendDir = globalPathProperties.resolveFrontendDir(deployDir, project.getFrontendDeployDir());

        // 获取项目的 startScript 和 stopScript
        // 自动修正 startScript 中 JAR_NAME=xxx 使其与项目的 jarName 一致（最后一道防线）
        String startScript = project.getStartScript();
        if (startScript != null && project.getJarName() != null && !project.getJarName().isEmpty()) {
            startScript = startScript.replaceAll("JAR_NAME=\\S+", "JAR_NAME=" + project.getJarName());
        }
        String stopScript = project.getStopScript();

        // 为每个节点执行部署
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> nodeResults = new ArrayList<>();
        result.put("recordId", 0);
        result.put("jarName", version.getJarName());
        result.put("status", DeployStatus.SUCCESS.getCode());
        result.put("message", "");
        result.put("deployDir", deployDir);

        boolean allSuccess = true;
        StringBuilder fullLog = new StringBuilder();
        Long firstRecordId = null;

        for (Long nid : targetNodeIds) {
            NodeModel node = nodeMapper.findById(nid);
            if (node == null) {
                Map<String, Object> nr = new LinkedHashMap<>();
                nr.put("nodeId", nid); nr.put("nodeName", "?");
                nr.put("success", false); nr.put("message", "节点不存在");
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
            if (firstRecordId == null) firstRecordId = deploy.getId();

            Map<String, Object> nodeResult = new LinkedHashMap<>();
            nodeResult.put("nodeId", nid);
            nodeResult.put("nodeName", node.getName());
            nodeResult.put("recordId", deploy.getId());
            StringBuilder nodeLog = new StringBuilder();

            try {
                if (isFrontendDeploy) {
                    nodeLog.append("[").append(node.getName()).append("] 前端静态资源部署...\n");
                    String jarPath = findJarPath(projectId, version);
                    java.io.File zipFile = new java.io.File(jarPath);
                    if (!zipFile.exists()) throw new RuntimeException("前端包不存在: " + jarPath);
                    nodeLog.append("  Zip: ").append(zipFile.getName()).append("\n");

                    String uploadUrl = agentBase + "/api/file/receive";
                    HttpHeaders fileHeaders = new HttpHeaders();
                    fileHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
                    MultiValueMap<String, Object> fileBody = new LinkedMultiValueMap<>();
                    fileBody.add("file", new FileSystemResource(zipFile));
                    fileBody.add("projectId", String.valueOf(projectId));
                    fileBody.add("versionName", version.getVersion());
                    HttpEntity<MultiValueMap<String, Object>> fileEntity = new HttpEntity<>(fileBody, fileHeaders);
                    restTemplate.postForEntity(uploadUrl, fileEntity, String.class);
                    nodeLog.append("  已传输到 Agent\n");

                    String agentZipPath = agentFileDir + "/" + jarName;
                    String unzipUrl = agentBase + "/api/file/unzip";
                    Map<String, String> unzipReq = new HashMap<>();
                    unzipReq.put("zipPath", agentZipPath);
                    unzipReq.put("targetDir", frontendDir);
                    restTemplate.postForEntity(unzipUrl, unzipReq, String.class);
                    nodeLog.append("  ✅ 已解压到 ").append(frontendDir).append("\n");

                    deployRecordMapper.updateStatus(deploy.getId(), DeployStatus.SUCCESS.getCode(),
                            nodeLog.toString(), System.currentTimeMillis());
                    nodeResult.put("success", true);
                    nodeResult.put("message", "前端部署成功");
                    nodeResults.add(nodeResult);
                    fullLog.append(nodeLog);
                    continue;
                }

                // STEP 1: 停旧进程
                nodeLog.append("[").append(node.getName()).append("] 停止旧进程...\n");
                String stopUrl = agentBase + "/api/process/" + projectId + "/stop";
                Map<String, String> stopReq = new HashMap<>();
                stopReq.put("stopScript", stopScript != null ? stopScript : "");
                stopReq.put("deployDir", deployDir);
                try { restTemplate.postForEntity(stopUrl, stopReq, String.class); } catch (Exception ignored) {}
                nodeLog.append("✅ 停止完成\n");

                // STEP 2: 找 Jar 包
                nodeLog.append("查找 Jar 包...\n");
                String jarPath = findJarPath(projectId, version);
                java.io.File jarFile = new java.io.File(jarPath);
                if (!jarFile.exists()) throw new RuntimeException("Jar包不存在: " + jarPath);
                nodeLog.append("  Jar: ").append(jarFile.getName()).append(" (").append(jarFile.length() / 1024).append("KB)\n");

                // STEP 3: 传输 Jar 到 Agent
                nodeLog.append("传输文件...\n");
                String uploadUrl = agentBase + "/api/file/receive";
                HttpHeaders fileHeaders = new HttpHeaders();
                fileHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
                MultiValueMap<String, Object> fileBody = new LinkedMultiValueMap<>();
                fileBody.add("file", new FileSystemResource(jarFile));
                fileBody.add("projectId", String.valueOf(projectId));
                fileBody.add("versionName", version.getVersion());
                HttpEntity<MultiValueMap<String, Object>> fileEntity = new HttpEntity<>(fileBody, fileHeaders);
                ResponseEntity<String> uploadResp = restTemplate.postForEntity(uploadUrl, fileEntity, String.class);
                nodeLog.append("  ").append(uploadResp.getStatusCode()).append("\n");

                // STEP 4: 启动
                nodeLog.append("启动应用...\n");
                String startUrl = agentBase + "/api/process/" + projectId + "/start";
                Map<String, String> startReq = new HashMap<>();
                startReq.put("startScript", startScript != null ? startScript : "");
                startReq.put("deployDir", deployDir);
                startReq.put("jarPath", agentFilePath);
                startReq.put("jarName", project.getJarName() != null ? project.getJarName() : jarName);
                ResponseEntity<String> startResp = restTemplate.postForEntity(startUrl, startReq, String.class);
                nodeLog.append("  ").append(startResp.getStatusCode()).append("\n");

                // STEP 5: 健康检查（通过 Agent Shell API 从容器内检查）
                nodeLog.append("健康检查...\n");
                boolean healthy = false;
                String shellUrl = agentBase + "/api/shell/exec";
                // 健康检查可配置、可关闭（项目级开关）
                boolean hcEnabled = project.getHealthCheckEnabled() == null || project.getHealthCheckEnabled();
                if (!hcEnabled) {
                    healthy = true;
                    nodeLog.append("  ⏭️ 健康检查已关闭，跳过检查，直接判定成功\n");
                } else {
                    int hcPort = project.getHealthCheckPort() != null ? project.getHealthCheckPort() : 8080;
                    String hcPath = (project.getHealthCheckPath() != null && !project.getHealthCheckPath().isEmpty()) ? project.getHealthCheckPath() : "/hello";
                    String hcKeywordRaw = (project.getHealthCheckKeyword() != null && !project.getHealthCheckKeyword().isEmpty()) ? project.getHealthCheckKeyword() : "Hello,DEPLOYED";
                    String healthCmd = "curl -s --max-time 3 http://127.0.0.1:" + hcPort + hcPath;
                    String[] hcKeywords = hcKeywordRaw.split(",");
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
                                nodeLog.append("  ✅ 第 ").append(i).append(" 次检查通过 (").append(hcPort).append(hcPath).append(")\n");
                                break;
                            } else {
                                nodeLog.append("  ⏳ 第 ").append(i).append(" 次检查 (").append(hcPort).append(hcPath).append(")\n");
                            }
                        } catch (Exception e) {
                            nodeLog.append("  ⏳ 第 ").append(i).append(" 次检查: ").append(e.getMessage()).append("\n");
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
                    nodeLog.append("❌ [").append(node.getName()).append("] 健康检查未通过\n");
                    deployRecordMapper.updateStatus(deploy.getId(), DeployStatus.FAILED.getCode(), nodeLog.toString(), System.currentTimeMillis());
                    allSuccess = false;
                }
            } catch (Exception e) {
                nodeResult.put("success", false);
                nodeResult.put("message", "❌ 部署失败: " + e.getMessage());
                nodeLog.append("❌ [").append(node.getName()).append("] 部署失败: ").append(e.getMessage()).append("\n");
                deployRecordMapper.updateStatus(deploy.getId(), DeployStatus.FAILED.getCode(), nodeLog.toString(), System.currentTimeMillis());
                allSuccess = false;
            }

            fullLog.append(nodeLog);
            nodeResults.add(nodeResult);
        }

        // 汇总结果
        int successCount = 0;
        for (Map<String, Object> nr : nodeResults) {
            if (Boolean.TRUE.equals(nr.get("success"))) successCount++;
        }
        result.put("nodeResults", nodeResults);
        result.put("successCount", successCount);
        result.put("totalCount", targetNodeIds.size());
        result.put("log", fullLog.toString());
        result.put("recordId", firstRecordId != null ? firstRecordId : 0);
        if (allSuccess) {
            result.put("status", DeployStatus.SUCCESS.getCode());
            result.put("message", "✅ " + successCount + "/" + targetNodeIds.size() + " 节点部署成功");
        } else {
            result.put("status", DeployStatus.FAILED.getCode());
            result.put("message", "⚠️ " + successCount + "/" + targetNodeIds.size() + " 节点成功，其余失败");
        }
        return Result.success(result);
    }

    private String findJarPath(Long projectId, VersionModel version) {
        String jarPath = version.getFilePath();
        if (jarPath != null && !jarPath.isEmpty()) {
            java.io.File f = new java.io.File(jarPath);
            if (f.exists()) {
                if (f.isDirectory()) {
                    // filePath 存的是目录，加上 jarName
                    String fullPath = jarPath + "/" + version.getJarName();
                    if (new java.io.File(fullPath).exists()) return fullPath;
                } else {
                    return jarPath; // 直接是文件路径
                }
            }
        }
        // 尝试多个可能的路径
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
        // 返回最可能的路径用于错误提示
        return candidates[1];
    }

    @GetMapping
    public Result<?> listRecords(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        List<DeployModel> records = deployRecordMapper.findByProjectId(projectId, page, pageSize);
        Long total = deployRecordMapper.countByProjectId(projectId);
        Map<String, Object> data = new HashMap<>();
        data.put("list", records);
        data.put("total", total);
        return Result.success(data);
    }

    @GetMapping("/{id}")
    public Result<?> getDetail(@PathVariable Long id) {
        DeployModel record = deployRecordMapper.findById(id);
        if (record == null) return Result.error(500, "部署记录不存在");
        return Result.success(record);
    }

    @PostMapping("/{id}/rollback")
    public Result<?> rollback(@PathVariable Long id) {
        // 获取当前部署记录
        DeployModel record = deployRecordMapper.findById(id);
        if (record == null) return Result.error(500, "部署记录不存在");

        // 查找上一个版本
        Long previousVersionId = record.getVersionId() - 1;
        if (previousVersionId < 1) previousVersionId = 1L;
        VersionModel previousVersion = versionPackageMapper.findById(previousVersionId);
        if (previousVersion == null) {
            return Result.error(1004, "找不到上一个版本 (ID: " + previousVersionId + ")，无法回滚");
        }

        // 获取项目和节点信息
        ProjectModel project = projectMapper.findById(record.getProjectId());
        NodeModel node = nodeMapper.findById(record.getNodeId());
        if (project == null) return Result.error(1005, "项目不存在");
        if (node == null) return Result.error(1002, "节点不存在");

        // 创建回滚记录（状态: PROCESSING）
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

        // ====== 执行回滚部署 ======
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> steps = new ArrayList<>();
        result.put("recordId", recordId);
        result.put("nodeName", node.getName());
        result.put("nodeIp", agentIp);
        result.put("jarName", previousVersion.getJarName());
        result.put("rollback", true); // 标记为回滚

        try {
            // STEP 1: 查找 Jar
            Map<String, Object> step1 = new LinkedHashMap<>();
            step1.put("name", "📦 查找上一个版本 Jar 包");
            String jarPath = findJarPath(record.getProjectId(), previousVersion);
            java.io.File jarFile = new java.io.File(jarPath);
            step1.put("success", jarFile.exists());
            step1.put("detail", "版本: " + previousVersion.getVersion() + " (ID: " + previousVersionId + ")\n路径: " + jarPath + "\n大小: " + (jarFile.exists() ? (jarFile.length() / 1024) + "KB" : "文件不存在"));
            steps.add(step1);
            if (!jarFile.exists()) throw new RuntimeException("Jar包文件不存在: " + jarPath);

            // STEP 2: 传输文件
            Map<String, Object> step2 = new LinkedHashMap<>();
            step2.put("name", "📤 传输文件到 Agent");
            String uploadUrl = agentBase + "/api/file/receive";
            String deployDir = serverPath + "/versions/" + record.getProjectId() + "/" + previousVersion.getVersion();
            String agentFilePath = deployDir + "/" + previousVersion.getJarName();

            HttpHeaders fileHeaders = new HttpHeaders();
            fileHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
            MultiValueMap<String, Object> fileBody = new LinkedMultiValueMap<>();
            fileBody.add("file", new FileSystemResource(jarFile));
            fileBody.add("projectId", String.valueOf(record.getProjectId()));
            fileBody.add("versionName", previousVersion.getVersion());
            HttpEntity<MultiValueMap<String, Object>> fileEntity = new HttpEntity<>(fileBody, fileHeaders);
            ResponseEntity<String> uploadResp = restTemplate.postForEntity(uploadUrl, fileEntity, String.class);
            step2.put("success", uploadResp.getStatusCode().is2xxSuccessful());
            step2.put("detail", "目标路径: " + agentFilePath + "\n传输状态: " + uploadResp.getStatusCode());
            steps.add(step2);

            // STEP 3: 启动进程
            Map<String, Object> step3 = new LinkedHashMap<>();
            step3.put("name", "🚀 启动进程");
            String agentStartUrl = agentBase + "/api/process/" + record.getProjectId() + "/start";
            String jvmOpts = project.getJvmOpts();
            if (jvmOpts == null || jvmOpts.isEmpty()) jvmOpts = "-Xms256m -Xmx512m -XX:+UseG1GC";

            Map<String, String> startReq = new HashMap<>();
            startReq.put("jarPath", agentFilePath);
            startReq.put("jvmOpts", jvmOpts);
            if (project.getEnvVars() != null && !project.getEnvVars().isEmpty()) startReq.put("envVars", project.getEnvVars());
            ResponseEntity<String> startResp = restTemplate.postForEntity(agentStartUrl, startReq, String.class);
            step3.put("success", startResp.getStatusCode().is2xxSuccessful());
            step3.put("detail", "回滚版本: " + previousVersion.getVersion() + " (Jar: " + previousVersion.getJarName() + ")\nJVM参数: " + jvmOpts + "\n部署目录: " + deployDir);
            steps.add(step3);

            // STEP 4: 健康检查
            Map<String, Object> step4 = new LinkedHashMap<>();
            step4.put("name", "🏥 健康检查");
            boolean healthy = false;
            String healthDetail = "";
            String shellUrl = agentBase + "/api/shell/exec";
            boolean hcEnabled = project.getHealthCheckEnabled() == null || project.getHealthCheckEnabled();
            if (!hcEnabled) {
                healthy = true;
                healthDetail = "⏭️ 健康检查已关闭，跳过检查，直接判定成功";
            } else {
                int appPort = project.getHealthCheckPort() != null ? project.getHealthCheckPort() : 8080;
                String hcPath = (project.getHealthCheckPath() != null && !project.getHealthCheckPath().isEmpty()) ? project.getHealthCheckPath() : "/hello";
                String hcKeywordRaw = (project.getHealthCheckKeyword() != null && !project.getHealthCheckKeyword().isEmpty()) ? project.getHealthCheckKeyword() : "Hello,DEPLOYED";
                String healthCmd = "curl -s --max-time 3 http://127.0.0.1:" + appPort + hcPath;
                String[] hcKeywords = hcKeywordRaw.split(",");
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
                            healthDetail = "✅ 回滚应用已启动 (第 " + i + " 次检查)\n响应: " + output.substring(0, Math.min(200, output.length()));
                            break;
                        } else {
                            healthDetail = "⏳ 第 " + i + " 次检查: " + (output.isEmpty() ? "(等待就绪)" : output.substring(0, Math.min(80, output.length())));
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
                result.put("message", "⚠️ 回滚部署完成但健康检查未通过");
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
        fullLog.append("═══════════════════════════════════════\n");
        int finalStatus = (int) result.get("status");
        deployRecordMapper.updateStatus(recordId, finalStatus, fullLog.toString(), System.currentTimeMillis());
        result.put("log", fullLog.toString());
        result.put("steps", steps);
        return Result.success(result);
    }

    /**
     * POST /api/deploy/{id}/cancel - 取消定时部署
     */
    @PostMapping("/{id}/cancel")
    public Result<?> cancel(@PathVariable Long id) {
        DeployModel record = deployRecordMapper.findById(id);
        if (record == null) return Result.error(500, "部署记录不存在");
        if (record.getStatus() != 5) return Result.paramError("只有待部署状态的记录才能取消");

        deployRecordMapper.updateStatus(id, 3, "⛔ 已手动取消定时部署\n取消时间: " +
                new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()), System.currentTimeMillis());
        return Result.success();
    }
}
