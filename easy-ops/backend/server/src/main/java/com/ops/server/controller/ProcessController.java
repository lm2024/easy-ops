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

    /**
     * POST /api/process/{projectId}/{nodeId}/start - 启动项目
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

        try {
            Map<String, String> body = new HashMap<>();
            body.put("startScript", project.getStartScript() != null ? project.getStartScript() : "");
            body.put("deployDir", project.getDeployDir() != null ? project.getDeployDir() : "");
            body.put("jarName", project.getJarName() != null ? project.getJarName() : "");

            Map<String, Object> resp = agentClient.post(node, "/process/" + projectId + "/start", body);
            agentClient.ensureAgentSuccess(resp);
            log.info("Start command sent to node {} for project {}", node.getName(), project.getName());
            return Result.success(resp);
        } catch (Exception e) {
            log.error("Failed to start project {} on node {}: {}", project.getName(), node.getName(), e.getMessage());
            return Result.error(500, "启动失败: " + e.getMessage());
        }
    }

    /**
     * POST /api/process/{projectId}/{nodeId}/stop - 停止项目
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

        try {
            Map<String, String> body = new HashMap<>();
            body.put("stopScript", project.getStopScript() != null ? project.getStopScript() : "");
            body.put("deployDir", project.getDeployDir() != null ? project.getDeployDir() : "");

            Map<String, Object> resp = agentClient.post(node, "/process/" + projectId + "/stop", body);
            agentClient.ensureAgentSuccess(resp);
            log.info("Stop command sent to node {} for project {}", node.getName(), project.getName());
            return Result.success(resp);
        } catch (Exception e) {
            log.error("Failed to stop project {} on node {}: {}", project.getName(), node.getName(), e.getMessage());
            return Result.error(500, "停止失败: " + e.getMessage());
        }
    }

    /**
     * POST /api/process/{projectId}/{nodeId}/restart - 重启项目
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

        try {
            Map<String, String> body = new HashMap<>();
            body.put("startScript", project.getStartScript() != null ? project.getStartScript() : "");
            body.put("stopScript", project.getStopScript() != null ? project.getStopScript() : "");
            body.put("deployDir", project.getDeployDir() != null ? project.getDeployDir() : "");

            Map<String, Object> resp = agentClient.post(node, "/process/" + projectId + "/restart", body);
            agentClient.ensureAgentSuccess(resp);
            log.info("Restart command sent to node {} for project {}", node.getName(), project.getName());
            return Result.success(resp);
        } catch (Exception e) {
            log.error("Failed to restart project {} on node {}: {}", project.getName(), node.getName(), e.getMessage());
            return Result.error(500, "重启失败: " + e.getMessage());
        }
    }
}
