package com.ops.agent.controller;

import com.ops.common.model.NodeModel;
import com.ops.common.response.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

/**
 * Agent系统信息接口
 * 上报节点信息、心跳
 */
@RestController
@RequestMapping("/sys")
public class SystemController {

    @Value("${agent.token}")
    private String agentToken;

    @Value("${agent.node-name}")
    private String nodeName;

    /**
     * 节点注册/心跳接口 (Agent主动调用Server)
     * Server侧有对应的 /api/nodes/heartbeat 接收
     */
    @PostMapping("/heartbeat")
    public Result<Map<String, Object>> heartbeat(@RequestParam(defaultValue = "") String nodeIp) {
        Map<String, Object> data = new HashMap<>();

        try {
            String ip = nodeIp.isEmpty() ? getLocalIp() : nodeIp;
            String osInfo = System.getProperty("os.name") + " " + System.getProperty("os.version");
            String javaVersion = System.getProperty("java.version");

            data.put("nodeId", hashNodeId(ip));
            data.put("nodeName", nodeName);
            data.put("ip", ip);
            data.put("port", 2123);
            data.put("osInfo", osInfo);
            data.put("javaVersion", javaVersion);
            data.put("status", 1); // ONLINE
        } catch (Exception e) {
            return Result.error(500, "获取系统信息失败: " + e.getMessage());
        }

        return Result.success(data);
    }

    /**
     * 获取本地IP地址
     */
    private String getLocalIp() throws Exception {
        return InetAddress.getLocalHost().getHostAddress();
    }

    /**
     * 根据IP生成节点ID
     */
    private String hashNodeId(String ip) {
        return Integer.toHexString(ip.hashCode());
    }
}
