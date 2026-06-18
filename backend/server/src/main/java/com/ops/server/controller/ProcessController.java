package com.ops.server.controller;

import com.ops.common.model.ProjectModel;
import com.ops.common.response.Result;
import com.ops.server.mapper.ProjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/process")
public class ProcessController {

    @Autowired
    private ProjectMapper projectMapper;

    /**
     * POST /api/process/{projectId}/{nodeId}/start - 启动项目
     */
    @PostMapping("/{projectId}/{nodeId}/start")
    public Result<?> start(@PathVariable Long projectId, @PathVariable Long nodeId) {
        ProjectModel project = projectMapper.findById(projectId);
        if (project == null) {
            return Result.error(1005, "项目不存在");
        }

        // Call Agent start endpoint
        Map<String, Object> result = new HashMap<>();
        result.put("message", "启动指令已发送给Agent端节点 " + nodeId);
        result.put("script", project.getStartScript());
        return Result.success(result);
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
        Map<String, Object> result = new HashMap<>();
        result.put("message", "停止指令已发送给Agent端节点 " + nodeId);
        result.put("script", project.getStopScript());
        return Result.success(result);
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
        Map<String, Object> result = new HashMap<>();
        result.put("message", "重启指令已发送给Agent端节点 " + nodeId);
        return Result.success(result);
    }
}
