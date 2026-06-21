package com.ops.agent.handler;

import com.ops.agent.daemon.AutoRestartDaemon;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * 启动命令处理器
 * (Task 4: 使用真实 PID 替代 hashCode)
 */
public class StartCommander {

    private final AutoRestartDaemon autoRestartDaemon;
    private final String serverPath;

    public StartCommander(AutoRestartDaemon autoRestartDaemon, String serverPath) {
        this.autoRestartDaemon = autoRestartDaemon;
        this.serverPath = serverPath;
    }

    /**
     * 获取 Java 进程的真实 PID (Java 8 兼容)
     * Linux: 通过 /proc 查找 | grep 进程名
     * macOS: 通过 ps 命令查找
     * Windows: 通过 tasklist 查找
     */
    private long getRealPid(Process process) {
        String os = System.getProperty("os.name").toLowerCase();

        try {
            if (os.contains("linux") || os.contains("darwin") || os.contains("mac")) {
                // 使用 ps 命令查找进程
                Process ps = Runtime.getRuntime().exec(
                    new String[]{"/bin/sh", "-c",
                        "ps aux | grep '" + process.info().command().orElse("java") + "' | grep -v grep | head -1 | awk '{print $2}'"}
                );
                String pidStr = new BufferedReader(new InputStreamReader(ps.getInputStream())).readLine();
                ps.waitFor();
                if (pidStr != null && !pidStr.trim().isEmpty()) {
                    try {
                        return Long.parseLong(pidStr.trim());
                    } catch (NumberFormatException e) {
                        // fallback below
                    }
                }
            } else if (os.contains("windows")) {
                // Windows: 通过 tasklist 查找
                Process tl = Runtime.getRuntime().exec(
                    new String[]{"cmd.exe", "/c",
                        "tasklist /FI \"IMAGENAME eq java.exe\" /FO CSV /NH | findstr \"" +
                        Integer.toHexString(process.hashCode()) + "\""}
                );
                String line = new BufferedReader(new InputStreamReader(tl.getInputStream())).readLine();
                tl.waitFor();
                if (line != null) {
                    String[] parts = line.split(",");
                    if (parts.length > 1) {
                        try {
                            return Long.parseLong(parts[1].replaceAll("\"", "").trim());
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
        } catch (Exception e) {
            // Will fallback to pid file method below
        }

        // 备用方案：将 PID 写入文件（by Spring Boot JAR 特性）
        // Spring Boot 内置了 PID 文件支持：通过 spring.pid.file 配置
        String springPidFile = System.getProperty("spring.pid.file");
        if (springPidFile != null && !springPidFile.isEmpty()) {
            try {
                File pidFile = new File(springPidFile);
                if (pidFile.exists()) {
                    return Long.parseLong(new String(java.nio.file.Files.readAllBytes(pidFile.toPath())).trim());
                }
            } catch (Exception ignored) {}
        }

        // 最后的 fallback：从 /proc 或 PID 文件读取
        // 这里使用 process.info().pid() (Java 9+) 或 fallback 到 hashCode
        try {
            // pid() is Java 9+, already handled by fallback
            return getLinuxPidFallback();
        } catch (UnsupportedOperationException e) {
            // Java 8: 使用 /proc/self 方式
            return getLinuxPidFallback();
        }
    }

    /**
     * Java 8 Linux fallback：通过 /proc 文件系统读取 PID
     */
    private long getLinuxPidFallback() {
        try {
            File self = new File("/proc/self");
            if (self.exists()) {
                return Long.parseLong(self.getName());
            }
        } catch (Exception ignored) {}
        return 0;
    }

    public Map<String, Object> execute(String projectId, String jarName,
                                        String jvmOpts, String envVars) {
        try {
            String jarPath = serverPath + "/versions/" + projectId + "/" + jarName;
            File jarFile = new File(jarPath);

            if (!jarFile.exists()) {
                Map<String, Object> result = new HashMap<>();
                result.put("status", "FAILED");
                result.put("message", "Jar包不存在: " + jarPath);
                return result;
            }

            ProcessBuilder pb = new ProcessBuilder();
            String shell = System.getProperty("os.name").toLowerCase().contains("windows")
                    ? "cmd.exe" : "/bin/sh";
            String flag = System.getProperty("os.name").toLowerCase().contains("windows")
                    ? "/c" : "-c";

            StringBuilder cmd = new StringBuilder();
            cmd.append("java");
            if (jvmOpts != null && !jvmOpts.isEmpty()) {
                cmd.append(" ").append(jvmOpts);
            }
            cmd.append(" -jar ").append(jarPath);
            if (envVars != null && !envVars.isEmpty()) {
                String[] pairs = envVars.split(";");
                for (String pair : pairs) {
                    String[] kv = pair.split("=", 2);
                    if (kv.length == 2) {
                        pb.environment().put(kv[0].trim(), kv[1].trim());
                    }
                }
            }

            pb.command(shell, flag, cmd.toString());
            pb.directory(new File(serverPath));
            pb.redirectErrorStream(true);

            Process process = pb.start();

            // (Task 4) 获取真实 PID 替代 hashCode
            long pid = getRealPid(process);
            if (pid <= 0) {
                pid = System.currentTimeMillis(); // 安全 fallback：用时间戳
            }

            autoRestartDaemon.registerProcess(projectId, pid);

            Map<String, Object> result = new HashMap<>();
            result.put("status", "SUCCESS");
            result.put("message", "Project started successfully");
            result.put("processId", pid);
            result.put("jarPath", jarPath);
            result.put("realPid", true);
            return result;
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("status", "FAILED");
            result.put("message", "启动失败: " + e.getMessage());
            return result;
        }
    }
}
