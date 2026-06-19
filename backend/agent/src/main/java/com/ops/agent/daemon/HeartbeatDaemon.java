package com.ops.agent.daemon;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
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

    /**
     * 每30秒发送一次心跳
     */
    @Scheduled(fixedRate = 30000)
    public void sendHeartbeat() {
        try {
            String ip = InetAddress.getLocalHost().getHostAddress();
            String osInfo = System.getProperty("os.name") + " " + System.getProperty("os.version");
            String javaVersion = System.getProperty("java.version");

            String url = serverUrl + "/nodes/heartbeat?nodeIp=" + ip;

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Token", agentToken);

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                System.out.println("[Agent Heartbeat] Sent OK for " + nodeName + " (" + ip + ")");
            }
        } catch (Exception e) {
            System.err.println("[Agent Heartbeat] Failed: " + e.getMessage());
        }
    }

    private String hashNodeId(String ip) {
        return Integer.toHexString(ip.hashCode());
    }
}
