package com.ops.agent.daemon;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

/**
 * 心跳保活 Daemon (Agent侧)
 * 定时向Server发送心跳，上报节点状态
 */
@Component
public class HeartbeatDaemon {

    @Value("${agent.server-url}")
    private String serverUrl;

    @Value("${agent.token}")
    private String agentToken;

    @Value("${agent.node-name}")
    private String nodeName;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 每30秒发送一次心跳
     */
    @Scheduled(fixedRate = 30000)
    public void sendHeartbeat() {
        try {
            String ip = InetAddress.getLocalHost().getHostAddress();
            String osInfo = System.getProperty("os.name") + " " + System.getProperty("os.version");
            String javaVersion = System.getProperty("java.version");

            Map<String, Object> heartbeat = new HashMap<>();
            heartbeat.put("nodeId", hashNodeId(ip));
            heartbeat.put("nodeName", nodeName);
            heartbeat.put("ip", ip);
            heartbeat.put("port", 2123);
            heartbeat.put("osInfo", osInfo);
            heartbeat.put("javaVersion", javaVersion);
            heartbeat.put("status", 1); // ONLINE

            String url = serverUrl + "/nodes/heartbeat?nodeIp=" + ip;
            restTemplate.postForObject(url, heartbeat, String.class);
        } catch (Exception e) {
            System.err.println("[Agent Heartbeat] Failed: " + e.getMessage());
        }
    }

    private String hashNodeId(String ip) {
        return Integer.toHexString(ip.hashCode());
    }
}
