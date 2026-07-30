package com.ops.agent.process;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * 进程指标采集：ps 获取 CPU/内存，jstat 获取 JVM 指标（可选）。
 */
public class ProcessMetricsHelper {

    private final ProcessStatusChecker statusChecker;

    public ProcessMetricsHelper() {
        this(new ProcessStatusChecker());
    }

    public ProcessMetricsHelper(ProcessStatusChecker statusChecker) {
        this.statusChecker = statusChecker;
    }

    /**
     * 按 PID 直接采集进程 CPU/内存指标（不依赖 deployDir/jarName）。
     */
    public Map<String, Object> getProcessMetricsByPid(long pid) {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("pid", pid);
        result.put("found", true);
        fillPsMetrics(pid, result);
        return result;
    }

    /**
     * 采集进程 CPU/内存指标。
     *
     * @param deployDir 部署目录
     * @param jarName   jar 文件名
     * @return pid、cpuPercent、memPercent、rssKb 等
     */
    public Map<String, Object> getProcessMetrics(String deployDir, String jarName) {
        Map<String, Object> result = new HashMap<String, Object>();
        Long pid = statusChecker.findPid(deployDir, jarName);
        result.put("pid", pid);
        if (pid == null) {
            result.put("found", false);
            return result;
        }
        result.put("found", true);
        fillPsMetrics(pid.longValue(), result);
        return result;
    }

    /**
     * 通过 jstat 采集 JVM 堆与 GC 指标。
     *
     * @param pid 进程 PID
     * @return heapUsedMb、heapMaxMb、gcYoungCount、gcTimeMs；失败时返回空字段
     */
    public Map<String, Object> getJvmMetrics(long pid) {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("pid", pid);
        Process process = null;
        BufferedReader reader = null;
        try {
            process = Runtime.getRuntime().exec(new String[]{"jstat", "-gc", String.valueOf(pid)});
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            // 消费 stderr 防止 buffer 满导致 waitFor 挂起
            final Process p = process;
            Thread drainErr = new Thread(() -> {
                java.io.BufferedReader err = null;
                try {
                    err = new java.io.BufferedReader(new InputStreamReader(p.getErrorStream()));
                    while (err.readLine() != null) {}
                } catch (Exception ignored) {} finally { closeQuietly(err); }
            });
            drainErr.setDaemon(true);
            drainErr.start();
            String header = reader.readLine();
            String values = reader.readLine();
            int exit = process.waitFor();
            drainErr.join(2000); // 等 stderr 消费线程结束
            if (exit != 0 || header == null || values == null) {
                result.put("available", false);
                return result;
            }
            parseJstatGc(header, values, result);
            result.put("available", true);
        } catch (Exception e) {
            result.put("available", false);
            result.put("detail", e.getMessage());
        } finally {
            closeQuietly(reader);
            if (process != null) {
                process.destroy();
            }
        }
        // 从 /proc/{pid}/cmdline 提取 -Xmx 作为 JVM 堆上限参考值
        int xmxMb = readXmxFromCmdline(pid);
        if (xmxMb > 0) {
            result.put("xmxMb", xmxMb);
        }
        return result;
    }

    /**
     * 从 /proc/{pid}/cmdline 读取 -Xmx 参数，返回 MB 值。未找到返回 0。
     */
    private int readXmxFromCmdline(long pid) {
        BufferedReader reader = null;
        try {
            java.io.File cmdlineFile = new java.io.File("/proc/" + pid + "/cmdline");
            if (!cmdlineFile.exists()) return 0;
            reader = new BufferedReader(new java.io.FileReader(cmdlineFile));
            String line = reader.readLine();
            if (line == null) return 0;
            // cmdline 以 \0 分隔参数
            String[] args = line.split("\0");
            for (String arg : args) {
                // 匹配 -Xmx512m、-Xmx1g、-Xmx1024M 等
                if (arg.startsWith("-Xmx")) {
                    return parseXmxToMb(arg.substring(4));
                }
            }
        } catch (Exception ignored) {
            // /proc 不可用或非 Linux 跳过
        } finally {
            closeQuietly(reader);
        }
        return 0;
    }

    /**
     * 解析 Xmx 值为 MB，支持 m/M/g/G 后缀
     */
    private int parseXmxToMb(String val) {
        if (val == null || val.isEmpty()) return 0;
        val = val.trim().toLowerCase();
        try {
            if (val.endsWith("g")) {
                return (int) (Double.parseDouble(val.substring(0, val.length() - 1)) * 1024);
            } else if (val.endsWith("m")) {
                return Integer.parseInt(val.substring(0, val.length() - 1));
            } else if (val.endsWith("k")) {
                return (int) (Long.parseLong(val.substring(0, val.length() - 1)) / 1024);
            } else {
                // 纯数字，JVM 默认单位是 bytes
                return (int) (Long.parseLong(val) / (1024 * 1024));
            }
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void fillPsMetrics(long pid, Map<String, Object> result) {
        Process process = null;
        BufferedReader reader = null;
        try {
            String cmd = "ps -p " + pid + " -o %cpu= -o %mem= -o rss= 2>/dev/null";
            process = Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", cmd});
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            // stderr 已通过 2>/dev/null 丢弃，无需额外消费
            String line = reader.readLine();
            process.waitFor();
            if (line == null || line.trim().isEmpty()) {
                return;
            }
            String[] parts = line.trim().split("\\s+");
            if (parts.length >= 1) {
                result.put("cpuPercent", parseDouble(parts[0]));
            }
            if (parts.length >= 2) {
                result.put("memPercent", parseDouble(parts[1]));
            }
            if (parts.length >= 3) {
                Long rssKb = parseLong(parts[2]);
                result.put("rssKb", rssKb);
                if (rssKb != null) {
                    result.put("memoryMb", Math.round(rssKb / 1024.0));
                }
            }
        } catch (Exception ignored) {
            // ps 不可用时跳过
        } finally {
            closeQuietly(reader);
            if (process != null) {
                process.destroy();
            }
        }
    }

    private void parseJstatGc(String header, String values, Map<String, Object> result) {
        String[] headers = header.trim().split("\\s+");
        String[] cols = values.trim().split("\\s+");
        Map<String, Double> numeric = new HashMap<String, Double>();
        int len = Math.min(headers.length, cols.length);
        for (int i = 0; i < len; i++) {
            numeric.put(headers[i], parseDouble(cols[i]));
        }

        double s0u = getOrZero(numeric, "S0U");
        double s1u = getOrZero(numeric, "S1U");
        double eu = getOrZero(numeric, "EU");
        double ou = getOrZero(numeric, "OU");
        double s0c = getOrZero(numeric, "S0C");
        double s1c = getOrZero(numeric, "S1C");
        double ec = getOrZero(numeric, "EC");
        double oc = getOrZero(numeric, "OC");

        double heapUsedKb = s0u + s1u + eu + ou;
        double heapMaxKb = s0c + s1c + ec + oc;
        result.put("heapUsedMb", roundMb(heapUsedKb));
        result.put("heapMaxMb", roundMb(heapMaxKb));

        double ygc = getOrZero(numeric, "YGC");
        double fgc = getOrZero(numeric, "FGC");
        double gct = getOrZero(numeric, "GCT");
        result.put("gcYoungCount", (int) ygc);
        result.put("gcFullCount", (int) fgc);
        result.put("gcTimeMs", (long) (gct * 1000));
    }

    private double getOrZero(Map<String, Double> map, String key) {
        Double value = map.get(key);
        return value == null ? 0D : value.doubleValue();
    }

    private double roundMb(double kb) {
        return Math.round(kb / 1024.0 * 100.0) / 100.0;
    }

    private Double parseDouble(String text) {
        try {
            return Double.valueOf(text.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private Long parseLong(String text) {
        try {
            return Long.valueOf(text.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private void closeQuietly(BufferedReader reader) {
        if (reader != null) {
            try {
                reader.close();
            } catch (Exception ignored) {
                // ignore
            }
        }
    }
}
