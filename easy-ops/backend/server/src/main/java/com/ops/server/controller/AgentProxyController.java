package com.ops.server.controller;

import com.ops.common.model.NodeModel;
import com.ops.common.response.Result;
import com.ops.server.mapper.NodeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
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

    /**
     * 使用全局 RestTemplate Bean（连接池 + 10s/30s 超时），
     * 替代无超时的裸 RestTemplate，防止 Tomcat 线程被 Agent 拖垮。
     */
    @Autowired
    private RestTemplate restTemplate;

    /**
     * GET /api/agent/{nodeId}/sys-info - 获取节点系统硬件信息
     */
    @GetMapping("/{nodeId}/sys-info")
    public Result<?> getSysInfo(@PathVariable String nodeId) {
        return proxyGet(nodeId, "/sys/info", null);
    }

    /**
     * GET /api/agent/{nodeId}/process/thread-top — 线程 CPU 排名
     */
    @GetMapping("/{nodeId}/process/thread-top")
    public Result<?> threadTop(@PathVariable String nodeId,
                               @RequestParam long pid,
                               @RequestParam(defaultValue = "20") int top) {
        Map<String, String> params = new java.util.HashMap<>();
        params.put("pid", String.valueOf(pid));
        params.put("top", String.valueOf(top));
        return proxyGet(nodeId, "/process/thread-top", params);
    }

    /**
     * GET /api/agent/{nodeId}/process/thread-info — 线程详情（状态/死锁/栈）
     */
    @GetMapping("/{nodeId}/process/thread-info")
    public Result<?> threadInfo(@PathVariable String nodeId,
                                @RequestParam long pid,
                                @RequestParam(defaultValue = "5") int maxStack) {
        Map<String, String> params = new java.util.HashMap<>();
        params.put("pid", String.valueOf(pid));
        params.put("maxStack", String.valueOf(maxStack));
        return proxyGet(nodeId, "/process/thread-info", params);
    }

    /**
     * GET /api/agent/{nodeId}/process/jvm-detail — JVM 详情（堆分区/非堆/GC/fd）
     */
    @GetMapping("/{nodeId}/process/jvm-detail")
    public Result<?> jvmDetail(@PathVariable String nodeId,
                               @RequestParam long pid) {
        Map<String, String> params = new java.util.HashMap<>();
        params.put("pid", String.valueOf(pid));
        return proxyGet(nodeId, "/process/jvm-detail", params);
    }

    // ==================== 通用代理方法 ====================

    @SuppressWarnings("unchecked")
    private Result<?> proxyGet(String nodeId, String path, Map<String, String> params) {
        NodeModel node = nodeMapper.findById(Long.parseLong(nodeId));
        if (node == null) {
            return Result.error(1002, "节点不存在");
        }

        String agentIp = node.getIp() != null ? node.getIp() : "127.0.0.1";
        int agentPort = node.getPort() != null ? node.getPort() : 2123;
        StringBuilder url = new StringBuilder("http://" + agentIp + ":" + agentPort + "/api" + path);
        if (params != null && !params.isEmpty()) {
            url.append("?");
            boolean first = true;
            for (Map.Entry<String, String> e : params.entrySet()) {
                if (!first) url.append("&");
                url.append(e.getKey()).append("=").append(e.getValue());
                first = false;
            }
        }

        try {
            Map<String, Object> response = restTemplate.getForObject(url.toString(), Map.class);
            if (response != null) {
                // 直接返回 Agent 的完整响应（含 code/message/data），前端兼容处理
                Object code = response.get("code");
                if (code instanceof Number && ((Number) code).intValue() == 200) {
                    Object data = response.get("data");
                    return Result.success(data != null ? data : response);
                }
                return Result.success(response);
            }
            return Result.error(500, "Agent 无响应");
        } catch (Exception e) {
            return Result.error(500, "请求 Agent 失败: " + e.getMessage());
        }
    }
}
