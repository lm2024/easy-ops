package com.ops.server.scheduler;

import com.ops.common.enums.DeployStatus;
import com.ops.common.model.*;
import com.ops.server.mapper.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.util.*;

/**
 * 定时部署调度器
 * 每 10 秒检查一次，找到到期的待部署记录并执行
 */
@Component
public class DeployScheduler {

    private static final Logger log = LoggerFactory.getLogger(DeployScheduler.class);

    @Autowired
    private DeployRecordMapper deployRecordMapper;

    @Autowired
    private ProjectMapper projectMapper;

    @Autowired
    private VersionPackageMapper versionPackageMapper;

    @Autowired
    private NodeMapper nodeMapper;

    @Value("${server.path:./data}")
    private String serverPath;

    @Autowired
    private DistributedLock distributedLock;

    private static final String LOCK_NAME_DEPLOY = "deploy_scheduler";

    private final RestTemplate restTemplate = new RestTemplate();

    @Scheduled(fixedRate = 10000)
    public void executeScheduledDeploys() {
        // SEC-001: 分布式锁 - 仅单实例执行
        if (!distributedLock.tryLock(LOCK_NAME_DEPLOY)) {
            log.debug("DeployScheduler: lock not acquired by this instance, skipping");
            return;
        }

        try {
            doExecuteScheduledDeploys();
        } finally {
            distributedLock.releaseLock(LOCK_NAME_DEPLOY);
        }
    }

    private void doExecuteScheduledDeploys() {
        long now = System.currentTimeMillis();
        List<DeployModel> readyList = deployRecordMapper.findScheduledReady(now);
        if (readyList == null || readyList.isEmpty()) return;

        for (DeployModel deploy : readyList) {
            try {
                log.info("Executing scheduled deploy ID={} projectId={} versionId={}", deploy.getId(), deploy.getProjectId(), deploy.getVersionId());
                executeDeploy(deploy);
            } catch (Exception e) {
                log.error("Scheduled deploy ID={} failed: {}", deploy.getId(), e.getMessage());
                deployRecordMapper.updateStatus(deploy.getId(), DeployStatus.FAILED.getCode(),
                        "定时部署执行失败: " + e.getMessage(), System.currentTimeMillis());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void executeDeploy(DeployModel deploy) {
        Long recordId = deploy.getId();
        Long projectId = deploy.getProjectId();
        Long versionId = deploy.getVersionId();
        Long nodeId = deploy.getNodeId();

        // 获取项目、版本、节点
        ProjectModel project = projectMapper.findById(projectId);
        VersionModel version = versionPackageMapper.findById(versionId);
        NodeModel node = nodeMapper.findById(nodeId);
        if (project == null || version == null || node == null) {
            deployRecordMapper.updateStatus(recordId, DeployStatus.FAILED.getCode(),
                    "定时部署失败: 项目/版本/节点不存在", System.currentTimeMillis());
            return;
        }

        String agentIp = node.getIp() != null ? node.getIp() : "127.0.0.1";
        int agentPort = node.getPort() != null ? node.getPort() : 2123;
        String agentBase = "http://" + agentIp + ":" + agentPort;
        StringBuilder logBuf = new StringBuilder();

        try {
            // Step 1: 查找 Jar
            logBuf.append("═══════════════════════════════════════\n");
            logBuf.append("⏰ 定时部署 - 自动执行\n");
            logBuf.append("─────────────────────────────────────\n");

            String jarPath = findJarPath(projectId, version);
            File jarFile = new File(jarPath);
            if (!jarFile.exists()) throw new RuntimeException("Jar包不存在: " + jarPath);
            logBuf.append("✅ Jar包已就绪: ").append(jarFile.getName()).append(" (").append(jarFile.length() / 1024).append("KB)\n");

            // Step 2: 停止旧进程
            logBuf.append("停止旧进程...\n");
            String stopUrl = agentBase + "/api/process/" + projectId + "/stop";
            Map<String, String> stopReq = new HashMap<>();
            stopReq.put("stopScript", project.getStopScript() != null ? project.getStopScript() : "");
            String agentFileDir = serverPath + "/versions/" + projectId + "/" + version.getVersion();
            String deployDir = project.getDeployDir();
            if (deployDir == null || deployDir.isEmpty()) {
                deployDir = agentFileDir;
            }
            String jarName = version.getJarName() != null ? version.getJarName() : "app.jar";
            String agentFilePath = agentFileDir + "/" + jarName;
            stopReq.put("deployDir", deployDir);
            try { restTemplate.postForEntity(stopUrl, stopReq, String.class); } catch (Exception ignored) {}
            logBuf.append("✅ 停止完成\n");

            // Step 3: 传输到 Agent
            logBuf.append("传输文件...\n");
            String uploadUrl = agentBase + "/api/file/receive";
            HttpHeaders fileHeaders = new HttpHeaders();
            fileHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
            MultiValueMap<String, Object> fileBody = new LinkedMultiValueMap<>();
            fileBody.add("file", new FileSystemResource(jarFile));
            fileBody.add("projectId", String.valueOf(projectId));
            fileBody.add("versionName", version.getVersion());
            HttpEntity<MultiValueMap<String, Object>> fileEntity = new HttpEntity<>(fileBody, fileHeaders);
            restTemplate.postForEntity(uploadUrl, fileEntity, String.class);
            logBuf.append("✅ 文件传输完成\n");

            // Step 4: 启动（使用项目的 startScript）
            logBuf.append("启动应用...\n");
            String agentStartUrl = agentBase + "/api/process/" + projectId + "/start";
            Map<String, String> startReq = new HashMap<>();
            startReq.put("startScript", project.getStartScript() != null ? project.getStartScript() : "");
            startReq.put("deployDir", deployDir);
            restTemplate.postForEntity(agentStartUrl, startReq, String.class);
            logBuf.append("✅ 启动完成\n");

            // Step 5: 健康检查 (异步等待，不阻塞调度线程)
            logBuf.append("健康检查 (异步模式)...\n");
            final int[] healthAttempt = {0};
            final java.util.concurrent.atomic.AtomicBoolean healthy = new java.util.concurrent.atomic.AtomicBoolean(false);
            String shellUrl = agentBase + "/api/shell/exec";

            // 使用独立线程进行健康检查轮询（避免阻塞调度器线程）
            Thread healthChecker = new Thread(() -> {
                for (int i = 1; i <= 5; i++) {
                    healthAttempt[0] = i;
                    try {
                        Thread.sleep(3000);
                        Map<String, String> cmdReq = new HashMap<>();
                        int hcPort = project.getHealthCheckPort() != null ? project.getHealthCheckPort() : 8080;
                        String hcPath = (project.getHealthCheckPath() != null && !project.getHealthCheckPath().isEmpty()) ? project.getHealthCheckPath() : "/hello";
                        String hcKeywordRaw = (project.getHealthCheckKeyword() != null && !project.getHealthCheckKeyword().isEmpty()) ? project.getHealthCheckKeyword() : "200";
                        String healthCmd = "curl -s -w \"\\n%{http_code}\" --max-time 3 http://127.0.0.1:" + hcPort + hcPath;
                        String[] hcKeywords = hcKeywordRaw.split(",");
                        cmdReq.put("command", healthCmd);
                        Map<String, Object> shellResp = restTemplate.postForObject(shellUrl, cmdReq, Map.class);
                        String cmdOutput = "";
                        if (shellResp != null && shellResp.get("data") instanceof Map) {
                            cmdOutput = ((Map<String, Object>) shellResp.get("data")).get("stdout") != null
                                    ? ((Map<String, Object>) shellResp.get("data")).get("stdout").toString() : "";
                        }
                        String[] lines = cmdOutput.split("\\n");
                        String statusCode = "";
                        StringBuilder body = new StringBuilder();
                        for (int li = 0; li < lines.length; li++) {
                            if (li == lines.length - 1) {
                                statusCode = lines[li].trim();
                            } else {
                                if (body.length() > 0) body.append("\n");
                                body.append(lines[li]);
                            }
                        }
                        String output = body.toString();
                        boolean matched = false;
                        if (statusCode.startsWith("2")) { matched = true; }
                        if (!matched) {
                            for (String kw : hcKeywords) {
                                String trimmed = kw.trim();
                                if (trimmed.matches("\\d{3}") && statusCode.equals(trimmed)) { matched = true; break; }
                            }
                        }
                        if (!matched) {
                            for (String kw : hcKeywords) {
                                String trimmed = kw.trim();
                                if (!trimmed.matches("\\d{3}") && body.toString().contains(trimmed)) { matched = true; break; }
                            }
                        }
                        if (matched) {
                            healthy.set(true);
                            synchronized (logBuf) {
                                logBuf.append("✅ 健康检查通过 (HTTP ").append(statusCode).append(") (第").append(i).append("次)\n");
                            }
                            break;
                        } else {
                            synchronized (logBuf) {
                                logBuf.append("⏳ 第").append(i).append("次检查: HTTP ").append(statusCode).append(" 等待就绪...\n");
                            }
                        }
                    } catch (Exception e) {
                        synchronized (logBuf) {
                            logBuf.append("⏳ 第").append(i).append("次检查: ").append(e.getMessage()).append("\n");
                        }
                    }
                }
            }, "deploy-health-check-" + recordId);
            healthChecker.start();
            // 等待健康检查结果 (最多 15 秒)
            healthChecker.join(15000);

            int finalStatus = healthy.get() ? DeployStatus.SUCCESS.getCode() : DeployStatus.FAILED.getCode();
            logBuf.append(healthy.get() ? "\n✅ 定时部署执行成功！" : "\n❌ 健康检查未通过");
            deployRecordMapper.updateStatus(recordId, finalStatus, logBuf.toString(), System.currentTimeMillis());
            log.info("Scheduled deploy ID={} completed status={}", recordId, finalStatus);

        } catch (Exception e) {
            logBuf.append("\n❌ 执行异常: ").append(e.getMessage());
            deployRecordMapper.updateStatus(recordId, DeployStatus.FAILED.getCode(), logBuf.toString(), System.currentTimeMillis());
        }
    }

    private String findJarPath(Long projectId, VersionModel version) {
        String jarPath = version.getFilePath();
        if (jarPath != null && !jarPath.isEmpty()) {
            File f = new File(jarPath);
            if (f.exists()) {
                if (f.isDirectory()) {
                    String fullPath = jarPath + "/" + version.getJarName();
                    if (new File(fullPath).exists()) return fullPath;
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
            if (new File(p).exists()) return p;
        }
        return candidates[1];
    }
}
