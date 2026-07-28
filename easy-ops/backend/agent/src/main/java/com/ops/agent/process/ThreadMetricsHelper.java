package com.ops.agent.process;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 线程级指标采集：top -H 获取线程 CPU，jcmd 获取 Java 线程名/状态/死锁。
 * JVM 详情：jstat + /proc 补充非堆/fd/线程数等。
 *
 * 仅在用户主动请求时调用，不影响日常监控性能。
 */
public class ThreadMetricsHelper {

    private static final Pattern TOP_TID_PATTERN = Pattern.compile("^\\s*(\\d+)\\s+\\S+\\s+\\S+\\s+");

    // ==================== 线程 CPU Top ====================

    /**
     * 获取进程内各线程的 CPU 使用率排名。
     *
     * @param pid  目标进程 PID
     * @param topN 返回前 N 个线程（默认 20）
     * @return threadList（按 CPU% 降序）、stateDistribution、totalCpuPercent 等
     */
    public Map<String, Object> getThreadTop(long pid, int topN) {
        Map<String, Object> result = new HashMap<>();
        if (topN <= 0) topN = 20;

        List<Map<String, Object>> threads = new ArrayList<>();
        Map<String, Integer> stateDist = new HashMap<>();

        // 1. top -H 获取实时 CPU 百分比
        parseTopThreads(pid, threads);

        // 2. jcmd 补充 Java 线程名
        Map<Long, String> javaNames = parseJavaThreadNames(pid);
        for (Map<String, Object> t : threads) {
            Long tid = ((Number) t.get("tid")).longValue();
            String javaName = javaNames.get(tid);
            if (javaName != null) {
                t.put("javaName", javaName);
            }
        }

        // 3. /proc/<pid>/task/<tid>/status 补充线程状态
        for (Map<String, Object> t : threads) {
            Long tid = ((Number) t.get("tid")).longValue();
            String state = readThreadState(pid, tid);
            if (state != null) {
                t.put("state", state);
                stateDist.merge(state, 1, Integer::sum);
            }
        }

        // 4. 计算总 CPU
        double totalCpu = 0;
        for (Map<String, Object> t : threads) {
            Object cpuObj = t.get("cpuPercent");
            if (cpuObj instanceof Number) {
                totalCpu += ((Number) cpuObj).doubleValue();
            }
        }

        // 5. 排序 + 截断
        threads.sort((a, b) -> {
            double ca = a.get("cpuPercent") instanceof Number ? ((Number) a.get("cpuPercent")).doubleValue() : 0;
            double cb = b.get("cpuPercent") instanceof Number ? ((Number) b.get("cpuPercent")).doubleValue() : 0;
            return Double.compare(cb, ca);
        });

        List<Map<String, Object>> topThreads = threads.size() > topN
                ? threads.subList(0, topN) : threads;

        result.put("pid", pid);
        result.put("totalThreads", threads.size());
        result.put("totalCpuPercent", Math.round(totalCpu * 10.0) / 10.0);
        result.put("topThreads", topThreads);
        result.put("stateDistribution", stateDist);
        return result;
    }

    // ==================== 线程详情（含死锁 + 栈） ====================

    /**
     * 获取线程详情：状态分布、死锁检测、线程列表（含栈摘要）。
     *
     * @param pid       目标进程 PID
     * @param maxStack  每线程最大栈帧数（0=不返回栈）
     * @return stateDistribution、deadlock（含 detected/threads/detail）、threads 等
     */
    public Map<String, Object> getThreadInfo(long pid, int maxStack) {
        Map<String, Object> result = new HashMap<>();
        if (maxStack <= 0) maxStack = 5;

        List<Map<String, Object>> threads = new ArrayList<>();
        Map<String, Integer> stateDist = new HashMap<>();
        List<String> deadlockThreads = new ArrayList<>();
        String deadlockDetail = null;
        boolean inDeadlockSection = false;
        StringBuilder deadlockBuilder = new StringBuilder();

        // 执行 jcmd Thread.print
        List<String> lines = execJcmd(pid, "Thread.print");

        Map<String, Object> current = null;
        for (String line : lines) {
            // 线程头：以双引号开头
            if (line.length() > 0 && line.charAt(0) == '"') {
                current = parseThreadHeader(line, threads, stateDist);
                continue;
            }
            if (current == null) {
                // 检查死锁区域
                if (line.contains("Found one Java-level deadlock")) {
                    inDeadlockSection = true;
                    deadlockBuilder.append(line).append("\n");
                } else if (inDeadlockSection) {
                    deadlockBuilder.append(line).append("\n");
                    // 提取死锁线程名
                    String trimmed = line.trim();
                    if (trimmed.startsWith("\"") && trimmed.contains("\"")) {
                        String tName = trimmed.substring(1, trimmed.indexOf('"', 1));
                        deadlockThreads.add(tName);
                    }
                    if (trimmed.isEmpty() && deadlockBuilder.length() > 20) {
                        inDeadlockSection = false;
                        deadlockDetail = deadlockBuilder.toString().trim();
                        deadlockBuilder.setLength(0);
                    }
                }
                continue;
            }
            // 栈帧
            String trimmed = line.trim();
            if (trimmed.startsWith("at ") || trimmed.startsWith("- ")) {
                @SuppressWarnings("unchecked")
                List<String> stack = (List<String>) current.get("stack");
                if (stack != null && stack.size() < maxStack) {
                    stack.add(trimmed);
                }
            }
        }

        // 处理末尾死锁段
        if (inDeadlockSection && deadlockBuilder.length() > 0) {
            deadlockDetail = deadlockBuilder.toString().trim();
        }

        Map<String, Object> deadlock = new HashMap<>();
        deadlock.put("detected", !deadlockThreads.isEmpty());
        deadlock.put("threads", deadlockThreads);
        deadlock.put("detail", deadlockDetail);

        result.put("pid", pid);
        result.put("totalThreads", threads.size());
        result.put("stateDistribution", stateDist);
        result.put("deadlock", deadlock);
        result.put("threads", threads);
        return result;
    }

    // ==================== JVM 详情 ====================

    /**
     * 获取 JVM 详情：堆/非堆/GC/类加载/编译/线程数/fd 数。
     *
     * @param pid 目标进程 PID
     * @return heapUsedMb/heapMaxMb/nonHeapUsedMb/gcData/classLoading/threadCount/fdCount 等
     */
    public Map<String, Object> getJvmDetail(long pid) {
        Map<String, Object> result = new HashMap<>();
        result.put("pid", pid);

        // 1. jstat -gc 获取堆与 GC 详情
        fillJstatDetail(pid, result);

        // 2. /proc 补充线程数与 fd 数
        result.put("threadCount", countProcThreads(pid));
        result.put("fdCount", countProcFd(pid));

        // 3. /proc/<pid>/status 补充进程级内存信息
        fillProcStatus(pid, result);

        // 4. jcmd VM.info 补充类加载与编译时间
        fillJcmdVmInfo(pid, result);

        return result;
    }

    // ==================== top 解析 ====================

    private void parseTopThreads(long pid, List<Map<String, Object>> threads) {
        BufferedReader reader = null;
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(
                    new String[]{"/bin/sh", "-c", "top -bn1 -H -p " + pid + " 2>/dev/null"});
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            boolean inProcessSection = false;
            String line;
            while ((line = reader.readLine()) != null) {
                // 跳过头部，从 PID 行开始
                if (!inProcessSection) {
                    Matcher m = TOP_TID_PATTERN.matcher(line);
                    if (m.find()) {
                        inProcessSection = true;
                    } else {
                        continue;
                    }
                }
                String trimmed = line.trim();
                if (trimmed.isEmpty()) break;

                String[] parts = trimmed.split("\\s+");
                if (parts.length < 12) continue;

                try {
                    long tid = Long.parseLong(parts[0]);
                    String cpuStr = parts[8];
                    // 处理 top 的小数点替代字符（, 或 .）
                    cpuStr = cpuStr.replace(',', '.');
                    double cpuPercent = Double.parseDouble(cpuStr);

                    Map<String, Object> thread = new HashMap<>();
                    thread.put("tid", tid);
                    thread.put("name", parts[11]); // top 显示的线程名
                    thread.put("cpuPercent", Math.round(cpuPercent * 10.0) / 10.0);
                    if (parts.length > 9) {
                        try {
                            thread.put("memPercent", Double.parseDouble(parts[9].replace(',', '.')));
                        } catch (NumberFormatException ignored) {}
                    }
                    threads.add(thread);
                } catch (NumberFormatException ignored) {
                    // 非进程行，跳过
                }
            }
            process.waitFor();
        } catch (Exception ignored) {
            // top 不可用时返回空列表
        } finally {
            closeQuietly(reader);
            if (process != null) process.destroy();
        }
    }

    // ==================== jcmd 线程名解析 ====================

    /**
     * 通过 jcmd 获取 Java 线程名映射（OS tid → Java 线程名）。
     */
    private Map<Long, String> parseJavaThreadNames(long pid) {
        Map<Long, String> names = new HashMap<>();
        List<String> lines = execJcmd(pid, "Thread.print");
        for (String line : lines) {
            if (line.length() > 0 && line.charAt(0) == '"') {
                int endQuote = line.indexOf('"', 1);
                if (endQuote > 1) {
                    String threadName = line.substring(1, endQuote);
                    // 提取 os tid（十六进制）
                    int nidIdx = line.indexOf("nid=");
                    if (nidIdx > 0) {
                        int nidEnd = line.indexOf(' ', nidIdx);
                        String nidHex = nidEnd > nidIdx
                                ? line.substring(nidIdx + 4, nidEnd)
                                : line.substring(nidIdx + 4);
                        try {
                            names.put(Long.parseLong(nidHex.trim(), 16), threadName);
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
        }
        return names;
    }

    // ==================== /proc 线程状态 ====================

    private String readThreadState(long pid, long tid) {
        BufferedReader reader = null;
        try {
            File statusFile = new File("/proc/" + pid + "/task/" + tid + "/status");
            if (!statusFile.exists()) return null;
            reader = new BufferedReader(new FileReader(statusFile));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("State:")) {
                    // 格式：State:\tS (sleeping)
                    String raw = line.substring(6).trim();
                    if (raw.length() > 0) {
                        return mapThreadState(String.valueOf(raw.charAt(0)));
                    }
                }
            }
        } catch (Exception ignored) {
            // 非 Linux 或权限不足
        } finally {
            closeQuietly(reader);
        }
        return null;
    }

    private String mapThreadState(String code) {
        switch (code) {
            case "R": return "RUNNABLE";
            case "S": return "WAITING";      // interruptible sleep
            case "D": return "BLOCKED";      // uninterruptible (IO)
            case "T": return "TERMINATED";   // stopped/traced
            case "Z": return "TERMINATED";   // zombie
            case "t": return "TERMINATED";   // tracing stop
            default:  return "UNKNOWN(" + code + ")";
        }
    }

    // ==================== jcmd 线程头解析 ====================

    private Map<String, Object> parseThreadHeader(String line,
                                                   List<Map<String, Object>> threads,
                                                   Map<String, Integer> stateDist) {
        // 格式："<name>" ... prio=N tid=0xNNNN nid=0xNNNN <state> [0x...]
        int endQuote = line.indexOf('"', 1);
        String name = endQuote > 1 ? line.substring(1, endQuote) : "unknown";

        String state = "UNKNOWN";
        String lower = line.toLowerCase();
        for (String[] pair : new String[][]{
                {"runnable", "RUNNABLE"},
                {"waiting on condition", "WAITING"},
                {"waiting for monitor entry", "BLOCKED"},
                {"timed_waiting", "TIMED_WAITING"},
                {"sleeping", "TIMED_WAITING"},
                {"in Object.wait()", "WAITING"},
        }) {
            if (lower.contains(pair[0])) {
                state = pair[1];
                break;
            }
        }
        // 回退：检查原始行中是否有大写状态关键字
        if ("UNKNOWN".equals(state)) {
            for (String kw : new String[]{"RUNNABLE", "BLOCKED", "WAITING", "TIMED_WAITING", "TERMINATED"}) {
                if (line.contains(kw)) {
                    state = kw;
                    break;
                }
            }
        }

        Map<String, Object> thread = new HashMap<>();
        thread.put("name", name);
        thread.put("state", state);
        thread.put("stack", new ArrayList<String>());
        threads.add(thread);
        stateDist.merge(state, 1, Integer::sum);
        return thread;
    }

    // ==================== jstat 扩展 ====================

    private void fillJstatDetail(long pid, Map<String, Object> result) {
        BufferedReader reader = null;
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(new String[]{"jstat", "-gc", String.valueOf(pid)});
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String header = reader.readLine();
            String values = reader.readLine();
            int exit = process.waitFor();
            if (exit != 0 || header == null || values == null) return;

            String[] headers = header.trim().split("\\s+");
            String[] cols = values.trim().split("\\s+");
            Map<String, Double> num = new HashMap<>();
            int len = Math.min(headers.length, cols.length);
            for (int i = 0; i < len; i++) {
                try { num.put(headers[i], Double.parseDouble(cols[i])); }
                catch (NumberFormatException ignored) {}
            }

            // 堆
            double s0u = getOrZero(num, "S0U"); double s1u = getOrZero(num, "S1U");
            double eu = getOrZero(num, "EU");   double ou = getOrZero(num, "OU");
            double s0c = getOrZero(num, "S0C"); double s1c = getOrZero(num, "S1C");
            double ec = getOrZero(num, "EC");   double oc = getOrZero(num, "OC");
            double mu = getOrZero(num, "MU");   double mc = getOrZero(num, "MC");
            double ccu = getOrZero(num, "CCU"); double ccsc = getOrZero(num, "CCSC");

            double heapUsedKb = s0u + s1u + eu + ou;
            double heapMaxKb = s0c + s1c + ec + oc;
            result.put("heapUsedMb", Math.round(heapUsedKb / 1024.0 * 100.0) / 100.0);
            result.put("heapMaxMb", Math.round(heapMaxKb / 1024.0 * 100.0) / 100.0);
            result.put("edenUsedMb", Math.round(eu / 1024.0 * 100.0) / 100.0);
            result.put("edenCapacityMb", Math.round(ec / 1024.0 * 100.0) / 100.0);
            result.put("survivorUsedMb", Math.round((s0u + s1u) / 1024.0 * 100.0) / 100.0);
            result.put("oldUsedMb", Math.round(ou / 1024.0 * 100.0) / 100.0);
            result.put("oldCapacityMb", Math.round(oc / 1024.0 * 100.0) / 100.0);
            result.put("metaspaceUsedMb", Math.round(mu / 1024.0 * 100.0) / 100.0);
            result.put("metaspaceCapacityMb", Math.round(mc / 1024.0 * 100.0) / 100.0);
            result.put("compressedClassUsedMb", Math.round(ccu / 1024.0 * 100.0) / 100.0);
            result.put("compressedClassCapacityMb", Math.round(ccsc / 1024.0 * 100.0) / 100.0);

            // GC
            result.put("gcYoungCount", (int) getOrZero(num, "YGC"));
            result.put("gcFullCount", (int) getOrZero(num, "FGC"));
            result.put("gcYoungTimeMs", (long) (getOrZero(num, "YGCT") * 1000));
            result.put("gcFullTimeMs", (long) (getOrZero(num, "FGCT") * 1000));
            result.put("gcTotalTimeMs", (long) (getOrZero(num, "GCT") * 1000));
        } catch (Exception ignored) {
        } finally {
            closeQuietly(reader);
            if (process != null) process.destroy();
        }
    }

    // ==================== /proc 补充 ====================

    private int countProcThreads(long pid) {
        File taskDir = new File("/proc/" + pid + "/task");
        if (taskDir.isDirectory()) {
            String[] tids = taskDir.list();
            return tids != null ? tids.length : 0;
        }
        return 0;
    }

    private int countProcFd(long pid) {
        File fdDir = new File("/proc/" + pid + "/fd");
        if (fdDir.isDirectory()) {
            String[] fds = fdDir.list();
            return fds != null ? fds.length : 0;
        }
        return 0;
    }

    private void fillProcStatus(long pid, Map<String, Object> result) {
        BufferedReader reader = null;
        try {
            File statusFile = new File("/proc/" + pid + "/status");
            if (!statusFile.exists()) return;
            reader = new BufferedReader(new FileReader(statusFile));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("VmRSS:")) {
                    long kb = parseKbValue(line);
                    if (kb > 0) result.put("rssKb", kb);
                } else if (line.startsWith("VmSize:")) {
                    long kb = parseKbValue(line);
                    if (kb > 0) result.put("vmSizeKb", kb);
                } else if (line.startsWith("VmPeak:")) {
                    long kb = parseKbValue(line);
                    if (kb > 0) result.put("vmPeakKb", kb);
                } else if (line.startsWith("FDSize:")) {
                    // fd 目录大小（上限）
                    try {
                        result.put("fdLimit", Integer.parseInt(line.substring(7).trim()));
                    } catch (NumberFormatException ignored) {}
                } else if (line.startsWith("Threads:")) {
                    try {
                        result.put("procThreadCount", Integer.parseInt(line.substring(8).trim()));
                    } catch (NumberFormatException ignored) {}
                }
            }
        } catch (Exception ignored) {
        } finally {
            closeQuietly(reader);
        }
    }

    // ==================== jcmd VM.info 补充 ====================

    private void fillJcmdVmInfo(long pid, Map<String, Object> result) {
        List<String> lines = execJcmd(pid, "VM.info");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.contains("Loaded") && trimmed.contains("Unloaded")) {
                // 格式：Classes: loaded 12345 ... unloaded 23 ...
                Pattern p = Pattern.compile("loaded\\s+(\\d+).*unloaded\\s+(\\d+)");
                Matcher m = p.matcher(trimmed);
                if (m.find()) {
                    try {
                        result.put("classLoaded", Integer.parseInt(m.group(1)));
                        result.put("classUnloaded", Integer.parseInt(m.group(2)));
                    } catch (NumberFormatException ignored) {}
                }
            } else if (trimmed.startsWith("Compilation:")) {
                // 格式：Compilation: enabled, 45232ms
                Pattern p = Pattern.compile("(\\d+)\\s*ms");
                Matcher m = p.matcher(trimmed);
                if (m.find()) {
                    try {
                        result.put("jitCompileTimeMs", Long.parseLong(m.group(1)));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
    }

    // ==================== jcmd 执行 ====================

    private List<String> execJcmd(long pid, String cmd) {
        List<String> lines = new ArrayList<>();
        Process process = null;
        BufferedReader reader = null;
        try {
            process = Runtime.getRuntime().exec(
                    new String[]{"jcmd", String.valueOf(pid), cmd});
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            process.waitFor();
        } catch (Exception ignored) {
            // jcmd 不可用时返回空列表
        } finally {
            closeQuietly(reader);
            if (process != null) process.destroy();
        }
        return lines;
    }

    // ==================== 工具方法 ====================

    private long parseKbValue(String line) {
        try {
            String val = line.substring(line.indexOf(':') + 1).trim();
            if (val.endsWith("kB")) val = val.substring(0, val.length() - 2).trim();
            return Long.parseLong(val);
        } catch (Exception e) {
            return 0;
        }
    }

    private double getOrZero(Map<String, Double> map, String key) {
        Double v = map.get(key);
        return v != null ? v : 0D;
    }

    private void closeQuietly(Closeable c) {
        if (c != null) {
            try { c.close(); } catch (Exception ignored) {}
        }
    }
}
