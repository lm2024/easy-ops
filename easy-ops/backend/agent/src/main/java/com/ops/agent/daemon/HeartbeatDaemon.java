package com.ops.agent.daemon;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 心跳保活 Daemon (Agent侧)
 * 定时向Server发送心跳，上报节点状态
 */
@Component
public class HeartbeatDaemon implements CommandLineRunner {

    @Value("${agent.server-url:http://localhost:8081/api}")
    private String serverUrl;

    @Value("${agent.token:}")
    private String agentToken;

    @Value("${agent.node-name:default-node}")
    private String nodeName;

    @Value("${agent.check-interval:30}")
    private int checkInterval;

    @Value("${agent.host-ip:}")
    private String hostIp;

    @Value("${agent.host-port:0}")
    private int hostPort;

    @Value("${agent.version:1.0.0-SNAPSHOT}")
    private String agentVersion;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 启动时：未配置 Token 则自动生成（内网简化部署），并打印到控制台供 Server 注册节点使用
     */
    @Override
    public void run(String... args) {
        if (agentToken == null || agentToken.trim().isEmpty()) {
            agentToken = "easyops-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            System.out.println("============================================================");
            System.out.println("[HeartbeatDaemon] 未配置 AGENT_TOKEN，已自动生成（内网模式）");
            System.out.println("[HeartbeatDaemon] 请在 Server 节点管理中注册此 Token:");
            System.out.println("[HeartbeatDaemon]   " + agentToken);
            System.out.println("============================================================");
        } else {
            System.out.println("[HeartbeatDaemon] Agent token loaded. Node: " + nodeName);
        }
    }

    /**
     * 每 N 秒发送一次心跳（从配置读取）
     * 心跳中包含监控数据：CPU、内存、磁盘使用率
     */
    @Scheduled(fixedRateString = "${agent.check-interval:30}000")
    public void sendHeartbeat() {
        try {
            String ip;
            if (hostIp != null && !hostIp.isEmpty()) {
                ip = hostIp;
            } else {
                ip = InetAddress.getLocalHost().getHostAddress();
            }
            String osInfo = System.getProperty("os.name") + " " + System.getProperty("os.version");
            String osArch = System.getProperty("os.arch");
            String javaVersion = System.getProperty("java.version");

            // 系统硬件信息
            int cpuCores = Runtime.getRuntime().availableProcessors();
            long maxMem = Runtime.getRuntime().maxMemory();
            int jvmMaxMb = maxMem > 0 && maxMem < Long.MAX_VALUE ? (int)(maxMem / (1024 * 1024)) : 0;
            long totalMemMb = getTotalMemoryMB();

            // 收集监控数据
            Map<String, Object> metrics = collectMetrics();

            // 上报外部可访问的端口（Docker 映射端口）
            String url = serverUrl + "/nodes/heartbeat?nodeIp=" + ip;
            if (hostPort > 0) {
                url += "&nodePort=" + hostPort;
            }

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Token", agentToken);
            headers.set("X-Node-Name", nodeName);
            headers.set("X-OS-Info", osInfo);
            headers.set("X-Java-Version", javaVersion);
            headers.set("X-CPU-Info", String.valueOf(cpuCores));
            headers.set("X-Mem-Info", String.valueOf(totalMemMb));
            headers.set("X-OS-Arch", osArch);
            headers.set("X-Agent-Version", agentVersion);

            // 添加监控数据到Header（Base64编码避免特殊字符问题）
            String metricsJson = new ObjectMapper().writeValueAsString(metrics);
            String metricsBase64 = java.util.Base64.getEncoder().encodeToString(metricsJson.getBytes("UTF-8"));
            headers.set("X-Metrics", metricsBase64);

            System.out.println("[Agent Heartbeat] Sending headers: X-Node-Name=" + nodeName + ", X-Token=" + agentToken);

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                System.out.println("[Agent Heartbeat] Sent OK for " + nodeName + " (" + ip + "), CPU=" + metrics.get("cpuUsagePercent") + "%, Memory=" + metrics.get("memoryUsagePercent") + "%");
            }
        } catch (Exception e) {
            System.err.println("[Agent Heartbeat] Failed: " + e.getMessage());
        }
    }

    /**
     * 收集系统监控数据
     */
    private Map<String, Object> collectMetrics() {
        Map<String, Object> metrics = new HashMap<>();

        try {
            // CPU使用率
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            double cpuUsage = osBean.getSystemLoadAverage();
            // 转换为百分比（0-100）
            int cpuCores = Runtime.getRuntime().availableProcessors();
            double cpuUsagePercent = cpuUsage >= 0 ? (cpuUsage / cpuCores) * 100 : 0;
            metrics.put("cpuUsagePercent", Math.round(cpuUsagePercent * 10.0) / 10.0);

            // 内存使用率
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
            MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
            long totalMemory = heapUsage.getUsed() + nonHeapUsage.getUsed();
            long maxMemory = Runtime.getRuntime().maxMemory();
            double memoryUsagePercent = (totalMemory * 100.0) / maxMemory;
            metrics.put("memoryUsagePercent", Math.round(memoryUsagePercent * 10.0) / 10.0);
            metrics.put("heapUsedMB", heapUsage.getUsed() / (1024 * 1024));
            metrics.put("heapMaxMB", heapUsage.getMax() / (1024 * 1024));

            // 线程数
            ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
            metrics.put("threadCount", threadBean.getThreadCount());

            // 磁盘使用率（根分区）
            metrics.put("diskUsagePercent", getRootDiskUsagePercent());

            // 系统负载
            metrics.put("systemLoadAverage", cpuUsage);

            // 进程运行时间（毫秒）
            long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
            metrics.put("processUptimeMs", uptime);

        } catch (Exception e) {
            System.err.println("[Agent Metrics] Failed to collect metrics: " + e.getMessage());
        }

        return metrics;
    }

    /**
     * 获取根分区磁盘使用率
     */
    private double getRootDiskUsagePercent() {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            Process p;
            if (os.contains("linux")) {
                p = Runtime.getRuntime().exec("df -h /");
            } else if (os.contains("mac") || os.contains("darwin")) {
                p = Runtime.getRuntime().exec("df -h /");
            } else {
                return 0;
            }

            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue;
                }
                // 格式：Filesystem Size Used Avail Use% Mounted
                String[] parts = line.split("\\s+");
                if (parts.length >= 5) {
                    String usePercent = parts[4].replace("%", "");
                    reader.close();
                    return Double.parseDouble(usePercent);
                }
            }
            reader.close();
        } catch (Exception e) {
            // 忽略错误，返回0
        }
        return 0;
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

    String hashNodeId(String ip) {
        return Integer.toHexString(ip.hashCode());
    }
}
