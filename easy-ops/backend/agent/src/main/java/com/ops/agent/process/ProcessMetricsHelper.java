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
            String header = reader.readLine();
            String values = reader.readLine();
            int exit = process.waitFor();
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
        return result;
    }

    private void fillPsMetrics(long pid, Map<String, Object> result) {
        Process process = null;
        BufferedReader reader = null;
        try {
            String cmd = "ps -p " + pid + " -o %cpu=,%mem=,rss= 2>/dev/null";
            process = Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", cmd});
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            process.waitFor();
            if (line == null || line.trim().isEmpty()) {
                return;
            }
            String[] parts = line.trim().split("\\s*,\\s*");
            if (parts.length >= 1) {
                result.put("cpuPercent", parseDouble(parts[0]));
            }
            if (parts.length >= 2) {
                result.put("memPercent", parseDouble(parts[1]));
            }
            if (parts.length >= 3) {
                result.put("rssKb", parseLong(parts[2]));
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
