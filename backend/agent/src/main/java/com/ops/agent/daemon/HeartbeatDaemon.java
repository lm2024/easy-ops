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
    @Scheduled(fixedRate = 5000)
    public void sendHeartbeat() {
        try {
            String ip = InetAddress.getLocalHost().getHostAddress();
            String osInfo = System.getProperty("os.name") + " " + System.getProperty("os.version");
            String osArch = System.getProperty("os.arch");
            String javaVersion = System.getProperty("java.version");

            // 系统硬件信息
            int cpuCores = Runtime.getRuntime().availableProcessors();
            long maxMem = Runtime.getRuntime().maxMemory();
            int jvmMaxMb = maxMem > 0 && maxMem < Long.MAX_VALUE ? (int)(maxMem / (1024 * 1024)) : 0;
            long totalMemMb = getTotalMemoryMB();

            String url = serverUrl + "/nodes/heartbeat?nodeIp=" + ip;

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Token", agentToken);
            headers.set("X-OS-Info", osInfo);
            headers.set("X-Java-Version", javaVersion);
            headers.set("X-CPU-Info", String.valueOf(cpuCores));
            headers.set("X-Mem-Info", String.valueOf(totalMemMb));
            headers.set("X-OS-Arch", osArch);

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                System.out.println("[Agent Heartbeat] Sent OK for " + nodeName + " (" + ip + ")");
            }
        } catch (Exception e) {
            System.err.println("[Agent Heartbeat] Failed: " + e.getMessage());
        }
    }

    private long getTotalMemoryMB() {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            java.io.BufferedReader reader;
            if (os.contains("linux")) {
                Process p = Runtime.getRuntime().exec("cat /proc/meminfo | grep MemTotal");
                reader = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
                String line = reader.readLine();
                reader.close();
                if (line != null) {
                    String[] parts = line.replaceAll("\\s+", " ").split(" ");
                    if (parts.length >= 2) return Long.parseLong(parts[1]) / 1024;
                }
            } else if (os.contains("mac") || os.contains("darwin")) {
                Process p = Runtime.getRuntime().exec("sysctl hw.memsize");
                reader = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
                String line = reader.readLine();
                reader.close();
                if (line != null) {
                    String[] parts = line.split(":");
                    if (parts.length >= 2) return Long.parseLong(parts[1].trim()) / (1024 * 1024);
                }
            }
        } catch (Exception ignored) {}
        long maxMem = Runtime.getRuntime().maxMemory();
        return maxMem > 0 && maxMem < Long.MAX_VALUE ? maxMem / (1024 * 1024) : 4096;
    }

    private String hashNodeId(String ip) {
        return Integer.toHexString(ip.hashCode());
    }
}
