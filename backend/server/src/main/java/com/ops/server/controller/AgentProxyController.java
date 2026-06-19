package com.ops.server.controller;

import com.ops.common.model.NodeModel;
import com.ops.common.response.Result;
import com.ops.server.mapper.NodeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Agent 代理接口
 * 前端通过 Server 中转调用 Agent 端接口
 */
@RestController
@RequestMapping("/agent")
public class AgentProxyController {

    @Autowired
    private NodeMapper nodeMapper;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * GET /api/agent/{nodeId}/sys-info - 获取节点系统硬件信息
     */
    @GetMapping("/{nodeId}/sys-info")
    public Result<?> getSysInfo(@PathVariable String nodeId) {
        NodeModel node = nodeMapper.findById(Long.parseLong(nodeId));
        if (node == null) {
            return Result.error(1002, "节点不存在");
        }

        String agentIp = node.getIp() != null ? node.getIp() : "127.0.0.1";
        int agentPort = node.getPort() != null ? node.getPort() : 2123;
        String url = "http://" + agentIp + ":" + agentPort + "/api/sys/info";

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response != null) {
                Object data = response.get("data");
                if (data instanceof Map) {
                    return Result.success(data);
                }
                return Result.success(response);
            }
            return Result.error(500, "Agent 无响应");
        } catch (Exception e) {
            return Result.error(500, "获取系统信息失败: " + e.getMessage());
        }
    }
}
