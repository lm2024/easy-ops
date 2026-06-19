package com.ops.agent.controller;

import com.ops.common.response.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 系统信息接口
 * 返回详细的 CPU、内存、磁盘、系统等硬件信息
 */
@RestController
@RequestMapping("/sys")
public class SystemInfoController {

    /**
     * GET /api/sys/info - 获取系统详细信息（CPU/内存/磁盘/系统）
     */
    @GetMapping("/info")
    public Result<Map<String, Object>> getSystemInfo() {
        Map<String, Object> info = new HashMap<>();
        String os = System.getProperty("os.name").toLowerCase();

        // ========== CPU 信息 ==========
        int cpuCores = Runtime.getRuntime().availableProcessors();
        info.put("cpuCores", cpuCores);
        info.put("cpuModel", readLine("cat /proc/cpuinfo | grep 'model name' | head -1 | cut -d: -f2", true));
        info.put("cpuUsagePercent", getCpuUsagePercent());
        info.put("loadAverage1m", readLine("cat /proc/loadavg | awk '{print $1}'", true));
        info.put("loadAverage5m", readLine("cat /proc/loadavg | awk '{print $2}'", true));
        info.put("loadAverage15m", readLine("cat /proc/loadavg | awk '{print $3}'", true));

        // ========== 内存信息 ==========
        Map<String, Object> memInfo = getDetailedMemory(os);
        info.putAll(memInfo);

        // ========== 磁盘信息 ==========
        info.put("disks", getDiskInfo(os));

        // ========== 系统信息 ==========
        info.put("osName", System.getProperty("os.name"));
        info.put("osVersion", System.getProperty("os.version"));
        info.put("osArch", System.getProperty("os.arch"));
        info.put("hostname", readLine("hostname", true));
        info.put("uptime", getUptime());
        info.put("javaVersion", System.getProperty("java.version"));
        info.put("jvmMaxHeapMB", Runtime.getRuntime().maxMemory() / (1024 * 1024));
        info.put("jvmTotalMemoryMB", Runtime.getRuntime().totalMemory() / (1024 * 1024));
        info.put("jvmFreeMemoryMB", Runtime.getRuntime().freeMemory() / (1024 * 1024));

        return Result.success(info);
    }

    // ==================== 内存 ====================

    private Map<String, Object> getDetailedMemory(String os) {
        Map<String, Object> mem = new HashMap<>();
        long totalMB = 0, availableMB = 0, freeMB = 0, usedMB = 0;
        int percent = 0;

        if (os.contains("linux")) {
            try {
                Process p = Runtime.getRuntime().exec("cat /proc/meminfo");
                BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line;
                long totalKB = 0, availableKB = 0, freeKB = 0, buffersKB = 0, cachedKB = 0;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("MemTotal:")) totalKB = parseKb(line);
                    else if (line.startsWith("MemAvailable:")) availableKB = parseKb(line);
                    else if (line.startsWith("MemFree:")) freeKB = parseKb(line);
                    else if (line.startsWith("Buffers:")) buffersKB = parseKb(line);
                    else if (line.startsWith("Cached:")) cachedKB = parseKb(line);
                }
                reader.close();
                totalMB = totalKB / 1024;
                availableMB = availableKB / 1024;
                freeMB = freeKB / 1024;
                usedMB = totalMB - availableMB;
                if (totalMB > 0) percent = (int) (usedMB * 100 / totalMB);
            } catch (Exception ignored) {}
        } else if (os.contains("mac") || os.contains("darwin")) {
            String totalStr = readLine("sysctl hw.memsize | awk '{print $2}'", false);
            if (totalStr != null) totalMB = Long.parseLong(totalStr) / (1024 * 1024);
            String pageSize = readLine("vm_stat | grep 'page size' | awk '{print $8}'", false);
            String freePages = readLine("vm_stat | grep 'Pages free' | awk '{print $3}' | sed 's/\\.//'", false);
            String activePages = readLine("vm_stat | grep 'Pages active' | awk '{print $3}' | sed 's/\\.//'", false);
            String wiredPages = readLine("vm_stat | grep 'Pages wired' | awk '{print $4}' | sed 's/\\.//'", false);
            long ps = pageSize != null ? Long.parseLong(pageSize) : 16384;
            long fp = freePages != null ? Long.parseLong(freePages) : 0;
            long ap = activePages != null ? Long.parseLong(activePages) : 0;
            long wp = wiredPages != null ? Long.parseLong(wiredPages) : 0;
            freeMB = fp * ps / (1024 * 1024);
            usedMB = (ap + wp) * ps / (1024 * 1024);
            availableMB = freeMB;
            if (totalMB > 0) percent = (int) (usedMB * 100 / totalMB);
        }

        mem.put("totalMemoryMB", totalMB > 0 ? totalMB : mem.getOrDefault("totalMemoryMB", 4096));
        mem.put("usedMemoryMB", usedMB);
        mem.put("freeMemoryMB", freeMB);
        mem.put("availableMemoryMB", availableMB);
        mem.put("memoryUsagePercent", percent);
        return mem;
    }

    private long parseKb(String line) {
        try {
            String[] parts = line.replaceAll("\\s+", " ").split(" ");
            if (parts.length >= 2) return Long.parseLong(parts[1]);
        } catch (Exception ignored) {}
        return 0;
    }

    // ==================== 磁盘 ====================

    private List<Map<String, Object>> getDiskInfo(String os) {
        List<Map<String, Object>> disks = new ArrayList<>();

        if (os.contains("linux")) {
            try {
                // df -P 输出: Filesystem     1024-blocks    Used Available Capacity Mounted on
                Process p = Runtime.getRuntime().exec("df -P");
                BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line;
                boolean header = true;
                while ((line = reader.readLine()) != null) {
                    if (header) { header = false; continue; }
                    if (line.trim().isEmpty() || line.trim().startsWith("Filesystem")) continue;
                    String[] parts = line.replaceAll("\\s+", " ").split(" ");
                    if (parts.length < 6) continue;
                    String mountPoint = parts[5];
                    // 跳过虚拟文件系统
                    if (mountPoint.startsWith("/proc") || mountPoint.startsWith("/sys") ||
                        mountPoint.startsWith("/dev") || mountPoint.equals("/dev/shm") ||
                        mountPoint.equals("/etc/hosts") || mountPoint.equals("/etc/hostname") ||
                        mountPoint.equals("/etc/resolv.conf")) continue;

                    Map<String, Object> disk = new HashMap<>();
                    // df -P 输出块大小是 1024 bytes
                    long totalBlocks = parseLongSafe(parts[1]);
                    long usedBlocks = parseLongSafe(parts[2]);
                    long freeBlocks = parseLongSafe(parts[3]);
                    long factor = 1024; // 1 block = 1024 bytes
                    disk.put("mountPoint", mountPoint);
                    disk.put("totalBytes", totalBlocks * factor);
                    disk.put("usedBytes", usedBlocks * factor);
                    disk.put("freeBytes", freeBlocks * factor);
                    disk.put("usagePercent", parts[4].replace("%", ""));
                    disk.put("totalGB", Math.round(totalBlocks * factor / 1.0 / (1024*1024*1024) * 10) / 10.0);
                    disk.put("usedGB", Math.round(usedBlocks * factor / 1.0 / (1024*1024*1024) * 10) / 10.0);
                    disk.put("freeGB", Math.round(freeBlocks * factor / 1.0 / (1024*1024*1024) * 10) / 10.0);
                    disks.add(disk);
                }
                reader.close();
            } catch (Exception ignored) {}
        } else if (os.contains("mac") || os.contains("darwin")) {
            try {
                Process p = Runtime.getRuntime().exec("df -k");
                BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line;
                boolean header = true;
                while ((line = reader.readLine()) != null) {
                    if (header) { header = false; continue; }
                    String[] parts = line.replaceAll("\\s+", " ").split(" ");
                    if (parts.length >= 9) {
                        Map<String, Object> disk = new HashMap<>();
                        disk.put("mountPoint", parts[8]);
                        disk.put("totalBytes", parseLongSafe(parts[1]) * 1024);
                        disk.put("usedBytes", parseLongSafe(parts[2]) * 1024);
                        disk.put("freeBytes", parseLongSafe(parts[3]) * 1024);
                        disk.put("usagePercent", parts[4].replace("%", ""));
                        disk.put("totalGB", toGB(disk.get("totalBytes")));
                        disk.put("usedGB", toGB(disk.get("usedBytes")));
                        disk.put("freeGB", toGB(disk.get("freeBytes")));
                        String mp = (String) disk.get("mountPoint");
                        if (mp != null && mp.startsWith("/") && !mp.startsWith("/System")) {
                            disks.add(disk);
                        }
                    }
                }
                reader.close();
            } catch (Exception ignored) {}
        }

        return disks;
    }

    // ==================== CPU 使用率 ====================

    private int getCpuUsagePercent() {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("linux")) {
                Process p1 = Runtime.getRuntime().exec("cat /proc/stat | grep '^cpu ' | head -1");
                BufferedReader r1 = new BufferedReader(new InputStreamReader(p1.getInputStream()));
                String line1 = r1.readLine();
                r1.close();
                if (line1 == null) return 0;
                long idle1 = getIdleTime(line1);
                long total1 = getTotalCpuTime(line1);
                Thread.sleep(500);
                Process p2 = Runtime.getRuntime().exec("cat /proc/stat | grep '^cpu ' | head -1");
                BufferedReader r2 = new BufferedReader(new InputStreamReader(p2.getInputStream()));
                String line2 = r2.readLine();
                r2.close();
                if (line2 == null) return 0;
                long idle2 = getIdleTime(line2);
                long total2 = getTotalCpuTime(line2);
                long diffIdle = idle2 - idle1;
                long diffTotal = total2 - total1;
                if (diffTotal > 0) return (int) ((diffTotal - diffIdle) * 100 / diffTotal);
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private long getIdleTime(String line) {
        String[] parts = line.replaceAll("\\s+", " ").split(" ");
        if (parts.length >= 5) return parseLongSafe(parts[4]);
        return 0;
    }

    private long getTotalCpuTime(String line) {
        String[] parts = line.replaceAll("\\s+", " ").split(" ");
        long total = 0;
        for (int i = 1; i < parts.length; i++) total += parseLongSafe(parts[i]);
        return total;
    }

    // ==================== 系统运行时间 ====================

    private String getUptime() {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("linux")) {
                String line = readLine("cat /proc/uptime", false);
                if (line != null) {
                    double seconds = Double.parseDouble(line.split(" ")[0]);
                    int days = (int) (seconds / 86400);
                    int hours = (int) ((seconds % 86400) / 3600);
                    int mins = (int) ((seconds % 3600) / 60);
                    if (days > 0) return days + "天" + hours + "小时" + mins + "分钟";
                    return hours + "小时" + mins + "分钟";
                }
            } else if (os.contains("mac") || os.contains("darwin")) {
                String boot = readLine("sysctl kern.boottime | awk -F'= ' '{print $2}' | cut -d',' -f1", false);
                if (boot != null) {
                    long bootSec = Long.parseLong(boot.trim());
                    long now = System.currentTimeMillis() / 1000;
                    long seconds = now - bootSec;
                    int days = (int) (seconds / 86400);
                    int hours = (int) ((seconds % 86400) / 3600);
                    int mins = (int) ((seconds % 3600) / 60);
                    if (days > 0) return days + "天" + hours + "小时" + mins + "分钟";
                    return hours + "小时" + mins + "分钟";
                }
            }
        } catch (Exception ignored) {}
        return "未知";
    }

    // ==================== 工具方法 ====================

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

    private long parseLongSafe(String s) {
        if (s == null || s.isEmpty()) return 0;
        try { return Long.parseLong(s.trim()); } catch (NumberFormatException e) { return 0; }
    }

    private long parseBytes(String s) {
        if (s == null || s.isEmpty()) return 0;
        try {
            s = s.trim().toUpperCase();
            if (s.endsWith("G")) return (long) (Double.parseDouble(s.replace("G", "")) * 1024 * 1024 * 1024);
            if (s.endsWith("M")) return (long) (Double.parseDouble(s.replace("M", "")) * 1024 * 1024);
            if (s.endsWith("K")) return (long) (Double.parseDouble(s.replace("K", "")) * 1024);
            if (s.endsWith("T")) return (long) (Double.parseDouble(s.replace("T", "")) * 1024 * 1024 * 1024 * 1024);
            if (s.endsWith("B")) s = s.substring(0, s.length() - 1);
            return Long.parseLong(s);
        } catch (NumberFormatException e) { return 0; }
    }

    private double toGB(Object bytes) {
        if (bytes == null) return 0;
        long b = ((Number) bytes).longValue();
        return Math.round(b * 10.0 / (1024 * 1024 * 1024)) / 10.0;
    }
}
