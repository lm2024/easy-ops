package com.ops.agent.daemon;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ops.agent.process.ProcessMetricsHelper;
import com.ops.agent.process.ProcessStatusChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 心跳保活 Daemon (Agent侧)
 * 定时向Server发送心跳，上报节点状态
 */
@Component
public class HeartbeatDaemon implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatDaemon.class);

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

    @Value("${agent.data-path:/app/data}")
    private String agentDataPath;

    private final ProcessStatusChecker processStatusChecker = new ProcessStatusChecker();
    private final ProcessMetricsHelper processMetricsHelper = new ProcessMetricsHelper(processStatusChecker);

    /**
     * 从版本文件读取升级后的版本号，文件不存在则返回编译时版本
     */
    private String getEffectiveVersion() {
        try {
            java.io.File versionFile = new java.io.File(agentDataPath, "agent-version.txt");
            if (versionFile.exists()) {
                String v = new String(java.nio.file.Files.readAllBytes(versionFile.toPath()), "UTF-8").trim();
                if (!v.isEmpty()) return v;
            }
        } catch (Exception ignored) {
        }
        return agentVersion;
    }

    /**
     * 获取 Agent 自身的 PID
     */
    private long getAgentPid() {
        try {
            String name = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
            int at = name.indexOf('@');
            if (at > 0) {
                return Long.parseLong(name.substring(0, at));
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    /**
     * 带超时的 RestTemplate，防止 Server 不可达时心跳阻塞
     * Spring @Scheduled 默认单线程调度器，心堵会导致所有定时任务停止
     */
    private final RestTemplate restTemplate;
    {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);   // 连接超时 3 秒
        factory.setReadTimeout(5000);      // 读取超时 5 秒
        this.restTemplate = new RestTemplate(factory);
    }

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
            headers.set("X-Agent-Version", getEffectiveVersion());
            headers.set("X-Agent-PID", String.valueOf(getAgentPid()));

            // 添加监控数据到Header（Base64编码避免特殊字符问题）
            String metricsJson = new ObjectMapper().writeValueAsString(metrics);
            String metricsBase64 = java.util.Base64.getEncoder().encodeToString(metricsJson.getBytes("UTF-8"));
            headers.set("X-Metrics", metricsBase64);

            System.out.println("[Agent Heartbeat] Sending headers: X-Node-Name=" + nodeName + ", X-Token=" + agentToken);

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.debug("心跳成功 节点={} IP={} CPU={}% 内存={}%",
                        nodeName, ip, metrics.get("cpuUsagePercent"), metrics.get("memoryUsagePercent"));
            }
        } catch (Exception e) {
            log.warn("心跳失败 节点={}", nodeName, e);
        }
    }

    /**
     * 收集系统监控数据
     */
    private Map<String, Object> collectMetrics() {
        Map<String, Object> metrics = new HashMap<>();

        try {
            // CPU使用率 - 使用 com.sun.management.OperatingSystemMXBean 获取真实CPU使用率
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            int cpuCores = Runtime.getRuntime().availableProcessors();

            // 优先使用 getSystemCpuLoad()（Java 8 com.sun.management API）
            double cpuUsagePercent = -1;
            if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
                com.sun.management.OperatingSystemMXBean sunOsBean =
                        (com.sun.management.OperatingSystemMXBean) osBean;
                cpuUsagePercent = sunOsBean.getSystemCpuLoad() * 100; // 返回 0.0~1.0
            }
            // fallback: 系统负载转换（不太准，但总比没有好）
            if (cpuUsagePercent < 0) {
                double cpuUsage = osBean.getSystemLoadAverage();
                cpuUsagePercent = cpuUsage >= 0 ? (cpuUsage / cpuCores) * 100 : 0;
            }
            metrics.put("cpuUsagePercent", Math.round(cpuUsagePercent * 10.0) / 10.0);

            // 内存使用率 - 采集真实的系统内存，而不是 JVM 内存
            double memoryUsagePercent = 0;
            long totalSystemMemoryMb = 0;
            long usedSystemMemoryMb = 0;

            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("linux")) {
                // Linux: 从 /proc/meminfo 读取真实系统内存
                try {
                    java.io.BufferedReader memReader = new java.io.BufferedReader(
                            new java.io.FileReader("/proc/meminfo"));
                    long memTotalKb = 0, memAvailableKb = 0;
                    String memLine;
                    while ((memLine = memReader.readLine()) != null) {
                        if (memLine.startsWith("MemTotal:")) {
                            memTotalKb = parseMemInfoValue(memLine);
                        } else if (memLine.startsWith("MemAvailable:")) {
                            memAvailableKb = parseMemInfoValue(memLine);
                        }
                        if (memTotalKb > 0 && memAvailableKb > 0) break;
                    }
                    memReader.close();
                    if (memTotalKb > 0) {
                        totalSystemMemoryMb = memTotalKb / 1024;
                        usedSystemMemoryMb = (memTotalKb - memAvailableKb) / 1024;
                        memoryUsagePercent = ((memTotalKb - memAvailableKb) * 100.0) / memTotalKb;
                    }
                } catch (Exception e) {
                    log.warn("读取内存信息失败 /proc/meminfo", e);
                }
            } else if (os.contains("mac") || os.contains("darwin")) {
                // macOS: 通过 sysctl 获取总内存，vm_stat 获取使用量
                try {
                    totalSystemMemoryMb = getTotalMemoryMB();
                    Process p = Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", "vm_stat"});
                    java.io.BufferedReader vmReader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(p.getInputStream()));
                    // 第一行是 header: "Virtual Memory Statistics..."
                    vmReader.readLine();
                    long pageSize = 4096; // 默认页大小
                    long freePages = 0, activePages = 0, inactivePages = 0, wiredPages = 0;
                    String vmLine;
                    while ((vmLine = vmReader.readLine()) != null) {
                        if (vmLine.contains("page size of")) {
                            // 提取页大小
                            String[] parts = vmLine.split("\\s+");
                            for (int i = 0; i < parts.length - 1; i++) {
                                if (parts[i].matches("\\d+")) {
                                    pageSize = Long.parseLong(parts[i]);
                                    break;
                                }
                            }
                        } else if (vmLine.startsWith("Pages free:")) {
                            freePages = parseVmStatValue(vmLine);
                        } else if (vmLine.startsWith("Pages active:")) {
                            activePages = parseVmStatValue(vmLine);
                        } else if (vmLine.startsWith("Pages inactive:")) {
                            inactivePages = parseVmStatValue(vmLine);
                        } else if (vmLine.startsWith("Pages wired down:")) {
                            wiredPages = parseVmStatValue(vmLine);
                        }
                    }
                    vmReader.close();
                    long usedPages = activePages + inactivePages + wiredPages;
                    long totalPages = usedPages + freePages;
                    if (totalPages > 0) {
                        usedSystemMemoryMb = (usedPages * pageSize) / (1024 * 1024);
                        memoryUsagePercent = (usedPages * 100.0) / totalPages;
                    }
                } catch (Exception e) {
                    log.warn("读取内存信息失败 macOS vm_stat", e);
                }
            }

            // 使用系统级内存数据
            metrics.put("memoryUsagePercent", Math.round(memoryUsagePercent * 10.0) / 10.0);
            metrics.put("totalSystemMemoryMb", totalSystemMemoryMb);
            metrics.put("usedSystemMemoryMb", usedSystemMemoryMb);

            // JVM 内存（Agent 自身）- 独立字段，不与系统内存混淆
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
            MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
            metrics.put("heapUsedMB", heapUsage.getUsed() / (1024 * 1024));
            metrics.put("heapMaxMB", heapUsage.getMax() / (1024 * 1024));

            // 线程数
            ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
            metrics.put("threadCount", threadBean.getThreadCount());

            // 磁盘使用率（根分区）
            metrics.put("diskUsagePercent", getRootDiskUsagePercent());

            // 系统负载
            double cpuUsage = osBean.getSystemLoadAverage();
            metrics.put("systemLoadAverage", cpuUsage);

            // 进程运行时间（毫秒）
            long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
            metrics.put("processUptimeMs", uptime);

            // 扫描本地部署的应用进程并采集指标
            metrics.put("processes", collectProcessMetrics());

        } catch (Exception e) {
            log.warn("指标采集异常", e);
        }

        return metrics;
    }

    /**
     * 解析 /proc/meminfo 中的值（单位 kB）
     * 格式：MemTotal:       16384000 kB
     */
    private long parseMemInfoValue(String line) {
        try {
            String[] parts = line.split("\\s+");
            if (parts.length >= 2) {
                return Long.parseLong(parts[1]);
            }
        } catch (NumberFormatException ignored) {}
        return 0;
    }

    /**
     * 解析 vm_stat 中的值（页数）
     * 格式：Pages free:       123456.
     */
    private long parseVmStatValue(String line) {
        try {
            String[] parts = line.split("\\s+");
            for (String part : parts) {
                if (part.matches("\\d+\\.?")) {
                    return Long.parseLong(part.replace(".", ""));
                }
            }
        } catch (NumberFormatException ignored) {}
        return 0;
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

    /**
     * 通过 jps/ps 直接发现本机所有 Java 进程（排除 Agent 自身）。
     * 不依赖目录扫描 —— 只要进程在跑就能发现。
     */
    private List<Map<String, Object>> collectProcessMetrics() {
        List<Map<String, Object>> processes = new ArrayList<>();
        long agentPid = getAgentPid();

        // ps 列出所有 Java 进程
        List<Long> javaPids = listJavaProcesses();
        if (javaPids.isEmpty()) {
            log.warn("进程发现失败: jps/ps 未找到任何Java进程");
            return processes;
        }

        int found = 0;
        for (Long pid : javaPids) {
            if (pid == agentPid) continue; // 排除 Agent 自身

            try {
                Map<String, Object> proc = new HashMap<>();
                proc.put("pid", pid.intValue());
                proc.put("alive", true);

                // 从 /proc/<pid>/cmdline 读取命令行，提取 jar 名作为标识
                String cmdline = readCmdline(pid);
                proc.put("command", cmdline);
                proc.put("jarName", extractJarName(cmdline));
                // 读取进程工作目录，用于 Server 端按 deployDir 精确定位（同一节点多应用 jar 同名时区分）
                proc.put("deployDir", readCwd(pid));

                // 采集 CPU/内存
                Map<String, Object> metricsResult = processMetricsHelper.getProcessMetricsByPid(pid);
                if (Boolean.TRUE.equals(metricsResult.get("found"))) {
                    Object cpu = metricsResult.get("cpuPercent");
                    if (cpu instanceof Number) proc.put("cpuPercent", ((Number) cpu).doubleValue());
                    Object mem = metricsResult.get("memoryMb");
                    if (mem instanceof Number) proc.put("memoryMb", ((Number) mem).intValue());
                }

                // 采集 JVM 指标
                Map<String, Object> jvmResult = processMetricsHelper.getJvmMetrics(pid);
                if (Boolean.TRUE.equals(jvmResult.get("available"))) {
                    Object heapUsed = jvmResult.get("heapUsedMb");
                    if (heapUsed instanceof Number) proc.put("heapUsedMb", ((Number) heapUsed).intValue());
                    Object heapMax = jvmResult.get("heapMaxMb");
                    if (heapMax instanceof Number) proc.put("heapMaxMb", ((Number) heapMax).intValue());
                    Object gcCount = jvmResult.get("gcYoungCount");
                    if (gcCount instanceof Number) proc.put("gcCount", ((Number) gcCount).intValue());
                    Object gcTime = jvmResult.get("gcTimeMs");
                    if (gcTime instanceof Number) proc.put("gcTimeMs", ((Number) gcTime).intValue());
                }

                // 从 cmdline 提取 -Xmx 作为 JVM 上限参考值
                int xmxMb = parseXmx(cmdline);
                if (xmxMb > 0) proc.put("xmxMb", xmxMb);

                processes.add(proc);
                found++;
            } catch (Exception e) {
                log.warn("进程指标采集失败 PID={}", pid, e);
            }
        }
        log.debug("进程发现完成: Java进程总数={} 上报数={}(已排除Agent自身)", javaPids.size(), found);
        return processes;
    }

    /** 用 ps 列出本机所有 Java 进程 PID（不依赖 jps，更可靠） */
    private List<Long> listJavaProcesses() {
        List<Long> pids = new ArrayList<>();
        Process p = null;
        java.io.BufferedReader r = null;
        try {
            p = Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c",
                    "ps -eo pid:10000,args:10000 2>/dev/null | grep '[j]ava' | awk '{print $1}'"});
            r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
            String line;
            while ((line = r.readLine()) != null) {
                try { pids.add(Long.parseLong(line.trim())); } catch (NumberFormatException ignored) {}
            }
            p.waitFor();
        } catch (Exception ignored) {
        } finally {
            try { if (r != null) r.close(); } catch (Exception ignored) {}
            if (p != null) p.destroy();
        }
        return pids;
    }

    /** 读取 /proc/<pid>/cmdline */
    private String readCmdline(Long pid) {
        try {
            byte[] bytes = java.nio.file.Files.readAllBytes(new File("/proc/" + pid + "/cmdline").toPath());
            return new String(bytes, "UTF-8").replace('\0', ' ');
        } catch (Exception e) {
            return "pid=" + pid;
        }
    }

    /** 读取 /proc/<pid>/cwd 获取进程工作目录（用于按 deployDir 精确匹配应用进程） */
    private String readCwd(Long pid) {
        try {
            return new File("/proc/" + pid + "/cwd").getCanonicalPath();
        } catch (Exception e) {
            return "";
        }
    }

    /** 从命令行中提取 jar 文件名，非 -jar 方式启动时返回空字符串 */
    private String extractJarName(String cmdline) {
        if (cmdline == null) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("-jar\\s+(\\S+\\.jar)").matcher(cmdline);
        if (m.find()) {
            String jar = m.group(1);
            int slash = jar.lastIndexOf('/');
            return slash >= 0 ? jar.substring(slash + 1) : jar;
        }
        // 非 -jar 启动（如 java -cp MainClass），返回空避免错误匹配
        return "";
    }

    /** 从命令行中提取 -Xmx 值（单位 MB），如 -Xmx3g → 3072 */
    private int parseXmx(String cmdline) {
        if (cmdline == null) return -1;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("-Xmx(\\d+)([gGmMkK]?)").matcher(cmdline);
        if (m.find()) {
            long val = Long.parseLong(m.group(1));
            String unit = m.group(2).toLowerCase();
            if ("g".equals(unit)) return (int)(val * 1024);
            if ("m".equals(unit)) return (int) val;
            if ("k".equals(unit)) return (int)(val / 1024);
            return (int)(val / (1024 * 1024));
        }
        return -1;
    }
}
