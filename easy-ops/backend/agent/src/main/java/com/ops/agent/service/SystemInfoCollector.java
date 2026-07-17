package com.ops.agent.service;

import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 采集 Agent 宿主机系统信息（CPU / 内存 / 磁盘 / 系统环境）。
 * 优先使用 Linux 标准工具（lscpu、top、/proc），兼容 Docker 容器环境。
 */
@Service
public class SystemInfoCollector {

    /**
     * 采集完整系统信息。
     *
     * @return 系统信息键值对
     */
    public Map<String, Object> collect() {
        Map<String, Object> info = new HashMap<>();
        String os = System.getProperty("os.name").toLowerCase();

        int logicalCores = Runtime.getRuntime().availableProcessors();
        int physicalCores = resolvePhysicalCores(os, logicalCores);
        String cpuModel = resolveCpuModel(os);

        info.put("cpuModel", cpuModel);
        info.put("cpuCores", logicalCores);
        info.put("cpuPhysicalCores", physicalCores);
        info.put("cpuLogicalCores", logicalCores);
        info.put("cpuUsagePercent", sampleCpuUsagePercent(os));
        info.put("cpuUsageDescription", "当前 CPU 忙碌程度（用户态+内核态），采样间隔约 0.5 秒");

        Map<String, String> load = resolveLoadAverage(os);
        info.put("loadAverage1m", load.get("load1"));
        info.put("loadAverage5m", load.get("load5"));
        info.put("loadAverage15m", load.get("load15"));
        info.put("loadDescription", buildLoadDescription(logicalCores, load.get("load1")));

        info.putAll(collectMemory(os));
        info.put("disks", collectDiskInfo(os));

        info.put("osName", System.getProperty("os.name"));
        info.put("osVersion", System.getProperty("os.version"));
        info.put("osArch", System.getProperty("os.arch"));
        info.put("hostname", readLine("hostname", true));
        info.put("uptime", resolveUptime(os));
        info.put("javaVersion", System.getProperty("java.version"));
        info.put("jvmMaxHeapMB", Runtime.getRuntime().maxMemory() / (1024 * 1024));
        info.put("jvmTotalMemoryMB", Runtime.getRuntime().totalMemory() / (1024 * 1024));
        info.put("jvmFreeMemoryMB", Runtime.getRuntime().freeMemory() / (1024 * 1024));

        return info;
    }

    // ==================== CPU ====================

    private String resolveCpuModel(String os) {
        if (os.contains("linux")) {
            String model = readLine("grep -m1 'model name' /proc/cpuinfo | cut -d: -f2", true);
            if (isPresent(model)) {
                return model;
            }
            String vendor = lscpuField("Vendor ID");
            String arch = lscpuField("Architecture");
            String modelName = lscpuField("Model name");
            if (isPresent(modelName)) {
                return modelName;
            }
            if (isPresent(vendor) && isPresent(arch)) {
                return vendor + " " + arch + "（容器可见信息，完整型号可能被虚拟化层隐藏）";
            }
            if (isPresent(arch)) {
                return arch + " 处理器（容器未暴露完整型号）";
            }
        } else if (os.contains("mac") || os.contains("darwin")) {
            String brand = readLine("sysctl -n machdep.cpu.brand_string", true);
            if (isPresent(brand)) {
                return brand;
            }
        }
        return "未能识别（虚拟机/Docker 可能隐藏 CPU 型号）";
    }

    private int resolvePhysicalCores(String os, int logicalCores) {
        if (os.contains("linux")) {
            int threadsPerCore = parseIntSafe(lscpuField("Thread(s) per core"));
            int cpuCount = parseIntSafe(lscpuField("CPU(s)"));
            if (threadsPerCore > 0 && cpuCount > 0) {
                return Math.max(1, cpuCount / threadsPerCore);
            }
            int coresPerCluster = parseIntSafe(lscpuField("Core(s) per cluster"));
            int clusters = parseIntSafe(lscpuField("Cluster(s)"));
            if (coresPerCluster > 0 && clusters > 0) {
                return coresPerCluster * clusters;
            }
            int fromCpuInfo = countPhysicalCoresFromCpuInfo();
            if (fromCpuInfo > 0) {
                return fromCpuInfo;
            }
        } else if (os.contains("mac") || os.contains("darwin")) {
            String physical = readLine("sysctl -n hw.physicalcpu", true);
            int count = parseIntSafe(physical);
            if (count > 0) {
                return count;
            }
        }
        return logicalCores;
    }

    private int countPhysicalCoresFromCpuInfo() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", "grep -E '^(physical id|core id)' /proc/cpuinfo"});
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            Set<String> unique = new HashSet<>();
            String phys = null;
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("physical id")) {
                    phys = line.split(":")[1].trim();
                } else if (line.startsWith("core id") && phys != null) {
                    unique.add(phys + ":" + line.split(":")[1].trim());
                }
            }
            reader.close();
            return unique.isEmpty() ? 0 : unique.size();
        } catch (Exception ignored) {
            return 0;
        }
    }

    private int sampleCpuUsagePercent(String os) {
        try {
            // 优先读 cgroup（容器环境获取容器自身 CPU，非容器环境读到宿主机 cgroup 也准确）
            int cgroupCpu = sampleCpuFromCgroup();
            if (cgroupCpu > 0) return cgroupCpu;
            // cgroup 不可用或返回 0，降级到 /proc
            if (os.contains("linux")) {
                String topLine = readLine("top -bn1 | grep '%Cpu(s)'", false);
                if (topLine != null) {
                    int idle = extractIdlePercent(topLine);
                    if (idle >= 0) {
                        return Math.min(100, Math.max(0, 100 - idle));
                    }
                }
                return sampleCpuUsageFromProcStat();
            }
            if (os.contains("mac") || os.contains("darwin")) {
                String usage = readLine("top -l 1 | grep 'CPU usage' | awk '{print $3}' | sed 's/%//'", false);
                double user = parseDoubleSafe(usage);
                String sys = readLine("top -l 1 | grep 'CPU usage' | awk '{print $5}' | sed 's/%//'", false);
                return (int) Math.min(100, Math.round(user + parseDoubleSafe(sys)));
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private int extractIdlePercent(String topLine) {
        for (String part : topLine.split(",")) {
            part = part.trim();
            if (part.endsWith(" id") || part.contains(" id")) {
                String num = part.replace("id", "").replace("%Cpu(s):", "").replace("%", "").trim();
                return (int) Math.round(parseDoubleSafe(num));
            }
        }
        return -1;
    }

    private int sampleCpuUsageFromProcStat() throws Exception {
        String line1 = readProcStatCpuLine();
        if (line1 == null) {
            return 0;
        }
        long idle1 = getIdleTime(line1);
        long total1 = getTotalCpuTime(line1);
        Thread.sleep(500);
        String line2 = readProcStatCpuLine();
        if (line2 == null) {
            return 0;
        }
        long idle2 = getIdleTime(line2);
        long total2 = getTotalCpuTime(line2);
        long diffIdle = idle2 - idle1;
        long diffTotal = total2 - total1;
        if (diffTotal > 0) {
            return (int) ((diffTotal - diffIdle) * 100 / diffTotal);
        }
        return 0;
    }

    private String readProcStatCpuLine() {
        return readLine("grep '^cpu ' /proc/stat | head -1", false);
    }

    // ==================== 内存 ====================

    private Map<String, Object> collectMemory(String os) {
        Map<String, Object> mem = new HashMap<>();
        long totalMB = 0;
        long availableMB = 0;
        long freeMB = 0;
        long usedMB = 0;
        long buffersCachedMB = 0;
        int percent = 0;

        // 优先读 cgroup（容器环境获取容器自身内存，非容器环境有 cgroup 限制时也准确）
        long[] cgroupMem = readCgroupMemory();
        if (cgroupMem != null) {
            totalMB = cgroupMem[0];
            usedMB = cgroupMem[1];
            if (totalMB > 0) {
                percent = (int) (usedMB * 100 / totalMB);
            }
            mem.put("totalMemoryMB", totalMB);
            mem.put("usedMemoryMB", usedMB);
            mem.put("freeMemoryMB", Math.max(0, totalMB - usedMB));
            mem.put("availableMemoryMB", Math.max(0, totalMB - usedMB));
            mem.put("buffersCachedMB", 0);
            mem.put("memoryUsagePercent", percent);
            mem.put("memoryUsedDescription", "容器内存使用量（cgroup 限制）");
            mem.put("memorySummary", String.format("容器内存: %dMB / %dMB (%d%%)", usedMB, totalMB, percent));
            return mem;
        }

        if (os.contains("linux")) {
            try {
                Process p = Runtime.getRuntime().exec("cat /proc/meminfo");
                BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line;
                long totalKB = 0;
                long availableKB = 0;
                long freeKB = 0;
                long buffersKB = 0;
                long cachedKB = 0;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("MemTotal:")) {
                        totalKB = parseKb(line);
                    } else if (line.startsWith("MemAvailable:")) {
                        availableKB = parseKb(line);
                    } else if (line.startsWith("MemFree:")) {
                        freeKB = parseKb(line);
                    } else if (line.startsWith("Buffers:")) {
                        buffersKB = parseKb(line);
                    } else if (line.startsWith("Cached:")) {
                        cachedKB = parseKb(line);
                    }
                }
                reader.close();
                totalMB = totalKB / 1024;
                availableMB = availableKB / 1024;
                freeMB = freeKB / 1024;
                buffersCachedMB = (buffersKB + cachedKB) / 1024;
                usedMB = Math.max(0, totalMB - availableMB);
                if (totalMB > 0) {
                    percent = (int) (usedMB * 100 / totalMB);
                }
            } catch (Exception ignored) {
            }
        } else if (os.contains("mac") || os.contains("darwin")) {
            String totalStr = readLine("sysctl hw.memsize | awk '{print $2}'", false);
            if (totalStr != null) {
                totalMB = Long.parseLong(totalStr) / (1024 * 1024);
            }
            String pageSize = readLine("vm_stat | grep 'page size' | awk '{print $8}'", false);
            String freePages = readLine("vm_stat | grep 'Pages free' | awk '{print $3}' | sed 's/\\.//'", false);
            String inactivePages = readLine("vm_stat | grep 'Pages inactive' | awk '{print $3}' | sed 's/\\.//'", false);
            String speculativePages = readLine("vm_stat | grep 'Pages speculative' | awk '{print $3}' | sed 's/\\.//'", false);
            String wiredPages = readLine("vm_stat | grep 'Pages wired' | awk '{print $4}' | sed 's/\\.//'", false);
            String activePages = readLine("vm_stat | grep 'Pages active' | awk '{print $3}' | sed 's/\\.//'", false);
            long ps = pageSize != null ? Long.parseLong(pageSize) : 16384;
            long fp = freePages != null ? Long.parseLong(freePages) : 0;
            long ip = inactivePages != null ? Long.parseLong(inactivePages) : 0;
            long sp = speculativePages != null ? Long.parseLong(speculativePages) : 0;
            long wp = wiredPages != null ? Long.parseLong(wiredPages) : 0;
            long ap = activePages != null ? Long.parseLong(activePages) : 0;
            availableMB = (fp + ip + sp) * ps / (1024 * 1024);
            freeMB = fp * ps / (1024 * 1024);
            usedMB = (ap + wp) * ps / (1024 * 1024);
            buffersCachedMB = Math.max(0, totalMB - usedMB - freeMB);
            if (totalMB > 0) {
                percent = (int) (usedMB * 100 / totalMB);
            }
        }

        if (totalMB <= 0) {
            totalMB = 4096;
        }

        mem.put("totalMemoryMB", totalMB);
        mem.put("usedMemoryMB", usedMB);
        mem.put("freeMemoryMB", freeMB);
        mem.put("availableMemoryMB", availableMB);
        mem.put("buffersCachedMB", buffersCachedMB);
        mem.put("memoryUsagePercent", percent);
        mem.put("memoryUsedDescription", "应用程序与内核实际占用的内存，不含可回收的文件缓存");
        mem.put("memoryBuffersCachedDescription", "磁盘读写产生的缓存和缓冲区，内存紧张时系统会自动回收");
        mem.put("memoryAvailableDescription", "估算仍可分配给新程序的内存（含大部分可回收缓存）");
        mem.put("memoryFreeDescription", "完全未被使用的物理页；通常小于「仍可分配」，因为 Linux 会尽量用空闲内存做缓存");
        mem.put("memorySummary", buildMemorySummary(totalMB, usedMB, buffersCachedMB, availableMB));
        return mem;
    }

    private String buildMemorySummary(long totalMB, long usedMB, long buffersCachedMB, long availableMB) {
        return String.format(
                "总内存 %s：应用占用 %s，文件缓存 %s，仍可分配 %s（缓存会在需要时自动释放）",
                formatMemory(totalMB), formatMemory(usedMB), formatMemory(buffersCachedMB), formatMemory(availableMB)
        );
    }

    private String formatMemory(long mb) {
        if (mb >= 1024) {
            return String.format("%.1f GB", mb / 1024.0);
        }
        return mb + " MB";
    }

    // ==================== 磁盘 / 负载 / 运行时间 ====================

    private Map<String, String> resolveLoadAverage(String os) {
        Map<String, String> load = new HashMap<>();
        load.put("load1", "?");
        load.put("load5", "?");
        load.put("load15", "?");
        if (os.contains("linux")) {
            String line = readLine("cat /proc/loadavg", false);
            if (line != null) {
                String[] parts = line.split("\\s+");
                if (parts.length >= 3) {
                    load.put("load1", parts[0]);
                    load.put("load5", parts[1]);
                    load.put("load15", parts[2]);
                }
            }
        }
        return load;
    }

    private String buildLoadDescription(int cpuCores, String load1Str) {
        try {
            double load1 = parseDoubleSafe(load1Str);
            if (load1 <= 0) {
                return "系统负载：等待 CPU 的任务数，通常应低于逻辑核数（" + cpuCores + " 核）";
            }
            String level = load1 < cpuCores * 0.7 ? "正常" : (load1 < cpuCores ? "偏高" : "过载");
            return String.format("1分钟负载 %.2f / %d核 = %s（负载>核数表示 CPU 繁忙）", load1, cpuCores, level);
        } catch (Exception e) {
            return "系统负载：1/5/15分钟平均等待任务数，超过 CPU 核数表示过载";
        }
    }

    private List<Map<String, Object>> collectDiskInfo(String os) {
        List<Map<String, Object>> disks = new ArrayList<>();
        if (os.contains("linux")) {
            try {
                Process p = Runtime.getRuntime().exec("df -P");
                BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line;
                boolean header = true;
                while ((line = reader.readLine()) != null) {
                    if (header) {
                        header = false;
                        continue;
                    }
                    if (line.trim().isEmpty() || line.trim().startsWith("Filesystem")) {
                        continue;
                    }
                    String[] parts = line.replaceAll("\\s+", " ").split(" ");
                    if (parts.length < 6) {
                        continue;
                    }
                    String mountPoint = parts[5];
                    if (mountPoint.startsWith("/proc") || mountPoint.startsWith("/sys")
                            || mountPoint.startsWith("/dev") || "/dev/shm".equals(mountPoint)
                            || "/etc/hosts".equals(mountPoint) || "/etc/hostname".equals(mountPoint)
                            || "/etc/resolv.conf".equals(mountPoint)) {
                        continue;
                    }
                    Map<String, Object> disk = new HashMap<>();
                    long totalBlocks = parseLongSafe(parts[1]);
                    long usedBlocks = parseLongSafe(parts[2]);
                    long freeBlocks = parseLongSafe(parts[3]);
                    long factor = 1024;
                    disk.put("mountPoint", mountPoint);
                    disk.put("totalBytes", totalBlocks * factor);
                    disk.put("usedBytes", usedBlocks * factor);
                    disk.put("freeBytes", freeBlocks * factor);
                    disk.put("usagePercent", parts[4].replace("%", ""));
                    disk.put("totalGB", roundGb(totalBlocks * factor));
                    disk.put("usedGB", roundGb(usedBlocks * factor));
                    disk.put("freeGB", roundGb(freeBlocks * factor));
                    disks.add(disk);
                }
                reader.close();
            } catch (Exception ignored) {
            }
        }
        return disks;
    }

    private String resolveUptime(String os) {
        try {
            if (os.contains("linux")) {
                String line = readLine("cat /proc/uptime", false);
                if (line != null) {
                    return formatDuration(Double.parseDouble(line.split(" ")[0]));
                }
            } else if (os.contains("mac") || os.contains("darwin")) {
                String boot = readLine("sysctl kern.boottime | awk -F'= ' '{print $2}' | cut -d',' -f1", false);
                if (boot != null) {
                    long seconds = System.currentTimeMillis() / 1000 - Long.parseLong(boot.trim());
                    return formatDuration(seconds);
                }
            }
        } catch (Exception ignored) {
        }
        return "未知";
    }

    private String formatDuration(double seconds) {
        int days = (int) (seconds / 86400);
        int hours = (int) ((seconds % 86400) / 3600);
        int mins = (int) ((seconds % 3600) / 60);
        if (days > 0) {
            return days + "天" + hours + "小时" + mins + "分钟";
        }
        return hours + "小时" + mins + "分钟";
    }

    // ==================== 工具 ====================

    private String lscpuField(String field) {
        return readLine("lscpu | grep '" + field + "' | awk -F: '{print $2}'", true);
    }

    private boolean isPresent(String value) {
        return value != null && !value.trim().isEmpty() && !"-".equals(value.trim());
    }

    private long getIdleTime(String line) {
        String[] parts = line.replaceAll("\\s+", " ").split(" ");
        if (parts.length >= 5) {
            return parseLongSafe(parts[4]);
        }
        return 0;
    }

    private long getTotalCpuTime(String line) {
        String[] parts = line.replaceAll("\\s+", " ").split(" ");
        long total = 0;
        for (int i = 1; i < parts.length; i++) {
            total += parseLongSafe(parts[i]);
        }
        return total;
    }

    private long parseKb(String line) {
        try {
            String[] parts = line.replaceAll("\\s+", " ").split(" ");
            if (parts.length >= 2) {
                return Long.parseLong(parts[1]);
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private String readLine(String command, boolean trim) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            String[] cmd = os.contains("windows")
                    ? new String[]{"cmd.exe", "/c", command}
                    : new String[]{"/bin/sh", "-c", command};
            Process p = Runtime.getRuntime().exec(cmd);
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = reader.readLine();
            reader.close();
            return line != null ? (trim ? line.trim() : line) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private int parseIntSafe(String s) {
        if (s == null || s.trim().isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private double parseDoubleSafe(String s) {
        if (s == null || s.trim().isEmpty()) {
            return 0;
        }
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private long parseLongSafe(String s) {
        if (s == null || s.isEmpty()) {
            return 0;
        }
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private double roundGb(long bytes) {
        return Math.round(bytes * 10.0 / (1024 * 1024 * 1024)) / 10.0;
    }

    // ==================== Docker / cgroup ====================

    /** 判断是否运行在 Docker 容器中 */
    private boolean isInDocker() {
        // 方法1: 检查 .dockerenv 文件
        if (new java.io.File("/.dockerenv").exists()) {
            return true;
        }
        // 方法2: 检查 cgroup 信息
        try {
            String cgroup = readLine("cat /proc/1/cgroup 2>/dev/null", false);
            if (cgroup != null && (cgroup.contains("docker") || cgroup.contains("kubepods") || cgroup.contains("containerd"))) {
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /**
     * 从 cgroup 读取容器 CPU 使用率。
     * cgroup v1: /sys/fs/cgroup/cpuacct/cpuacct.usage (纳秒)
     * 两次采样间隔计算百分比。
     */
    private int sampleCpuFromCgroup() {
        try {
            long usage1 = readCgroupCpuUsage();
            if (usage1 <= 0) return 0;
            long wall1 = System.nanoTime();
            Thread.sleep(2000); // 2秒采样，空闲时也能测到差异
            long usage2 = readCgroupCpuUsage();
            if (usage2 <= usage1) return 0;
            long wall2 = System.nanoTime();

            long cpuDelta = usage2 - usage1; // 纳秒
            long wallDelta = wall2 - wall1;  // 纳秒

            if (wallDelta > 0) {
                // cgroup usage_usec 是总 CPU 时间，除以核心数得到等效单核百分比
                int cores = Runtime.getRuntime().availableProcessors();
                return (int) Math.min(100, Math.round(cpuDelta * 100.0 / wallDelta / cores));
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    /** 读取 cgroup CPU 使用时间（纳秒） */
    private long readCgroupCpuUsage() {
        // cgroup v1: 单行纯数字（纳秒）
        try {
            java.io.File v1 = new java.io.File("/sys/fs/cgroup/cpuacct/cpuacct.usage");
            if (v1.exists()) {
                java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(v1));
                String line = r.readLine();
                r.close();
                if (line != null && !line.trim().isEmpty()) {
                    return Long.parseLong(line.trim());
                }
            }
        } catch (Exception ignored) {}

        // cgroup v2: 多行 key-value，取 usage_usec 行（微秒 → 纳秒）
        try {
            java.io.File v2 = new java.io.File("/sys/fs/cgroup/cpu.stat");
            if (v2.exists()) {
                java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(v2));
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.startsWith("usage_usec ")) {
                        r.close();
                        return Long.parseLong(line.split("\\s+")[1]) * 1000;
                    }
                }
                r.close();
            }
        } catch (Exception ignored) {}
        return 0;
    }

    /**
     * 从 cgroup 读取容器内存限制和使用量。
     * @return [totalMB, usedMB]，失败返回 null
     */
    private long[] readCgroupMemory() {
        try {
            // cgroup v2: memory.max + memory.current（优先，因为更新）
            String maxStr = readCgroupFile("/sys/fs/cgroup/memory.max");
            String curStr = readCgroupFile("/sys/fs/cgroup/memory.current");
            if (maxStr != null && curStr != null && !"max".equals(maxStr.trim())) {
                long maxBytes = Long.parseLong(maxStr.trim());
                long curBytes = Long.parseLong(curStr.trim());
                if (maxBytes > 100L * 1024 * 1024 * 1024) return null;
                return new long[]{maxBytes / (1024 * 1024), curBytes / (1024 * 1024)};
            }

            // cgroup v1: memory.limit_in_bytes + memory.usage_in_bytes
            String limitStr = readCgroupFile("/sys/fs/cgroup/memory/memory.limit_in_bytes");
            String usageStr = readCgroupFile("/sys/fs/cgroup/memory/memory.usage_in_bytes");
            if (limitStr != null && usageStr != null) {
                long limitBytes = Long.parseLong(limitStr.trim());
                long usageBytes = Long.parseLong(usageStr.trim());
                if (limitBytes > 100L * 1024 * 1024 * 1024) return null;
                return new long[]{limitBytes / (1024 * 1024), usageBytes / (1024 * 1024)};
            }
        } catch (Exception ignored) {}
        return null;
    }

    /** 直接用 Java I/O 读取 cgroup 文件第一行（不用 shell） */
    private String readCgroupFile(String path) {
        try {
            java.io.File f = new java.io.File(path);
            if (!f.exists()) return null;
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(f));
            String line = r.readLine();
            r.close();
            return line;
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 读取文件第一行 */
    private String readFileLine(String path) {
        try {
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(path));
            String line = reader.readLine();
            reader.close();
            return line;
        } catch (Exception ignored) {
            return null;
        }
    }
}
