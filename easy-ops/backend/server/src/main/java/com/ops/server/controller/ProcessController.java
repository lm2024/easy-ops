package com.ops.server.controller;

import com.ops.common.model.NodeModel;
import com.ops.common.model.ProjectModel;
import com.ops.common.response.Result;
import com.ops.server.mapper.NodeMapper;
import com.ops.server.mapper.ProjectMapper;
import com.ops.server.client.AgentClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 进程管理接口 — 通过 AgentClient 调用 Agent 执行实际操作
 */
@RestController
@RequestMapping("/process")
public class ProcessController {

    private static final Logger log = LoggerFactory.getLogger(ProcessController.class);

    @Autowired
    private ProjectMapper projectMapper;

    @Autowired
    private NodeMapper nodeMapper;

    @Autowired
    private AgentClient agentClient;

    /** 异步执行线程池 */
    private final ExecutorService execPool = Executors.newFixedThreadPool(4);

    /** 任务状态跟踪：taskId → {status, result, error, startTime} */
    private final ConcurrentHashMap<String, Map<String, Object>> taskMap = new ConcurrentHashMap<>();

    /**
     * POST /api/process/{projectId}/{nodeId}/start - 启动项目（异步）
     * 立即返回 taskId，后台执行启动命令
     */
    @PostMapping("/{projectId}/{nodeId}/start")
    public Result<?> start(@PathVariable Long projectId, @PathVariable Long nodeId) {
        ProjectModel project = projectMapper.findById(projectId);
        if (project == null) {
            return Result.error(1005, "项目不存在");
        }
        NodeModel node = nodeMapper.findById(nodeId);
        if (node == null) {
            return Result.error(1002, "节点不存在");
        }

        String taskId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Map<String, Object> taskStatus = new ConcurrentHashMap<>();
        taskStatus.put("status", "RUNNING");
        taskStatus.put("startTime", System.currentTimeMillis());
        taskStatus.put("action", "start");
        taskStatus.put("projectId", projectId);
        taskStatus.put("nodeId", nodeId);
        taskStatus.put("nodeName", node.getName());
        taskStatus.put("projectName", project.getName());
        taskMap.put(taskId, taskStatus);

        CompletableFuture.runAsync(() -> {
            try {
                String jarName = project.getJarName() != null ? project.getJarName() : "";
                String startScript = ensureStartScript(project.getStartScript(), jarName);

                Map<String, String> body = new HashMap<>();
                body.put("startScript", startScript);
                body.put("deployDir", project.getDeployDir() != null ? project.getDeployDir() : "");
                body.put("jarName", jarName);

                Map<String, Object> resp = agentClient.post(node, "/process/" + projectId + "/start", body);
                agentClient.ensureAgentSuccess(resp);
                taskStatus.put("status", "DONE");
                taskStatus.put("result", "启动指令已发送");
                log.info("Start command sent to node {} for project {}", node.getName(), project.getName());
            } catch (Exception e) {
                taskStatus.put("status", "ERROR");
                taskStatus.put("error", e.getMessage());
                log.error("Failed to start project {} on node {}: {}", project.getName(), node.getName(), e.getMessage());
            }
        }, execPool);

        Map<String, Object> data = new HashMap<>();
        data.put("taskId", taskId);
        data.put("action", "start");
        data.put("nodeName", node.getName());
        return Result.success(data);
    }

    /**
     * POST /api/process/{projectId}/{nodeId}/stop - 停止项目（异步）
     */
    @PostMapping("/{projectId}/{nodeId}/stop")
    public Result<?> stop(@PathVariable Long projectId, @PathVariable Long nodeId) {
        ProjectModel project = projectMapper.findById(projectId);
        if (project == null) {
            return Result.error(1005, "项目不存在");
        }
        NodeModel node = nodeMapper.findById(nodeId);
        if (node == null) {
            return Result.error(1002, "节点不存在");
        }

        String taskId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Map<String, Object> taskStatus = new ConcurrentHashMap<>();
        taskStatus.put("status", "RUNNING");
        taskStatus.put("startTime", System.currentTimeMillis());
        taskStatus.put("action", "stop");
        taskStatus.put("projectId", projectId);
        taskStatus.put("nodeId", nodeId);
        taskStatus.put("nodeName", node.getName());
        taskStatus.put("projectName", project.getName());
        taskMap.put(taskId, taskStatus);

        CompletableFuture.runAsync(() -> {
            try {
                String jarName = project.getJarName() != null ? project.getJarName() : "";
                String stopScript = ensureStopScript(project.getStopScript(), jarName);

                Map<String, String> body = new HashMap<>();
                body.put("stopScript", stopScript);
                body.put("deployDir", project.getDeployDir() != null ? project.getDeployDir() : "");

                Map<String, Object> resp = agentClient.post(node, "/process/" + projectId + "/stop", body);
                agentClient.ensureAgentSuccess(resp);
                taskStatus.put("status", "DONE");
                taskStatus.put("result", "停止指令已发送");
                log.info("Stop command sent to node {} for project {}", node.getName(), project.getName());
            } catch (Exception e) {
                taskStatus.put("status", "ERROR");
                taskStatus.put("error", e.getMessage());
                log.error("Failed to stop project {} on node {}: {}", project.getName(), node.getName(), e.getMessage());
            }
        }, execPool);

        Map<String, Object> data = new HashMap<>();
        data.put("taskId", taskId);
        data.put("action", "stop");
        data.put("nodeName", node.getName());
        return Result.success(data);
    }

    /**
     * POST /api/process/{projectId}/{nodeId}/restart - 重启项目（异步）
     */
    @PostMapping("/{projectId}/{nodeId}/restart")
    public Result<?> restart(@PathVariable Long projectId, @PathVariable Long nodeId) {
        ProjectModel project = projectMapper.findById(projectId);
        if (project == null) {
            return Result.error(1005, "项目不存在");
        }
        NodeModel node = nodeMapper.findById(nodeId);
        if (node == null) {
            return Result.error(1002, "节点不存在");
        }

        String taskId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Map<String, Object> taskStatus = new ConcurrentHashMap<>();
        taskStatus.put("status", "RUNNING");
        taskStatus.put("startTime", System.currentTimeMillis());
        taskStatus.put("action", "restart");
        taskStatus.put("projectId", projectId);
        taskStatus.put("nodeId", nodeId);
        taskStatus.put("nodeName", node.getName());
        taskStatus.put("projectName", project.getName());
        taskMap.put(taskId, taskStatus);

        CompletableFuture.runAsync(() -> {
            try {
                String jarName = project.getJarName() != null ? project.getJarName() : "";
                String startScript = ensureStartScript(project.getStartScript(), jarName);
                String stopScript = ensureStopScript(project.getStopScript(), jarName);

                Map<String, String> body = new HashMap<>();
                body.put("startScript", startScript);
                body.put("stopScript", stopScript);
                body.put("deployDir", project.getDeployDir() != null ? project.getDeployDir() : "");

                Map<String, Object> resp = agentClient.post(node, "/process/" + projectId + "/restart", body);
                agentClient.ensureAgentSuccess(resp);
                taskStatus.put("status", "DONE");
                taskStatus.put("result", "重启指令已发送");
                log.info("Restart command sent to node {} for project {}", node.getName(), project.getName());
            } catch (Exception e) {
                taskStatus.put("status", "ERROR");
                taskStatus.put("error", e.getMessage());
                log.error("Failed to restart project {} on node {}: {}", project.getName(), node.getName(), e.getMessage());
            }
        }, execPool);

        Map<String, Object> data = new HashMap<>();
        data.put("taskId", taskId);
        data.put("action", "restart");
        data.put("nodeName", node.getName());
        return Result.success(data);
    }

    /**
     * GET /api/process/task/{taskId} - 查询异步任务状态
     */
    @GetMapping("/task/{taskId}")
    public Result<?> getTaskStatus(@PathVariable String taskId) {
        Map<String, Object> status = taskMap.get(taskId);
        if (status == null) {
            return Result.error(404, "任务不存在");
        }
        return Result.success(status);
    }

    // ======================== 默认脚本生成 ========================

    /**
     * 确保有启动脚本：如果项目未配置，则根据 jarName 自动生成默认脚本
     */
    private String ensureStartScript(String startScript, String jarName) {
        if (startScript != null && !startScript.trim().isEmpty()) {
            return startScript;
        }
        String jar = (jarName != null && !jarName.trim().isEmpty()) ? jarName : "app.jar";
        return "#!/bin/bash\n"
                + "cd \"$(dirname \"$0\")\"\n"
                + "JAR_NAME=" + jar + "\n"
                + "mkdir -p logs\n"
                + "PIDS=$(ps -ef | grep \"[j]ava.*-jar.*$JAR_NAME\" | awk '{print $2}')\n"
                + "if [ -n \"$PIDS\" ]; then\n"
                + "  for p in $PIDS; do kill \"$p\" 2>/dev/null; done\n"
                + "  sleep 2\n"
                + "  for p in $PIDS; do kill -9 \"$p\" 2>/dev/null; done\n"
                + "fi\n"
                + "nohup java -jar \"$JAR_NAME\" >> logs/startup.log 2>&1 &\n"
                + "echo \"Started PID=$! jar=$JAR_NAME\"\n";
    }

    /**
     * 确保有停止脚本：如果项目未配置，则根据 jarName 自动生成默认脚本
     */
    private String ensureStopScript(String stopScript, String jarName) {
        if (stopScript != null && !stopScript.trim().isEmpty()) {
            return stopScript;
        }
        String jar = (jarName != null && !jarName.trim().isEmpty()) ? jarName : "app.jar";
        return "#!/bin/bash\n"
                + "JAR_NAME=" + jar + "\n"
                + "PIDS=$(ps -ef | grep \"[j]ava.*-jar.*$JAR_NAME\" | awk '{print $2}')\n"
                + "if [ -z \"$PIDS\" ]; then\n"
                + "  echo \"未找到 $JAR_NAME 进程\"; exit 0\n"
                + "fi\n"
                + "echo \"停止 $JAR_NAME: $PIDS\"\n"
                + "for p in $PIDS; do kill \"$p\" 2>/dev/null; done\n"
                + "sleep 3\n"
                + "for p in $PIDS; do kill -9 \"$p\" 2>/dev/null; done\n"
                + "echo \"已停止 $JAR_NAME\"\n";
    }
}
