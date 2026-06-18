package com.ops.agent.handler;

import com.ops.agent.daemon.AutoRestartDaemon;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * 启动命令处理器
 */
public class StartCommander {

    private final AutoRestartDaemon autoRestartDaemon;
    private final String serverPath;

    public StartCommander(AutoRestartDaemon autoRestartDaemon, String serverPath) {
        this.autoRestartDaemon = autoRestartDaemon;
        this.serverPath = serverPath;
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

            // Java 8 compatible - use hashCode as pseudo-PID
            long pid = process.hashCode();

            autoRestartDaemon.registerProcess(projectId, pid);

            Map<String, Object> result = new HashMap<>();
            result.put("status", "SUCCESS");
            result.put("message", "Project started successfully");
            result.put("processId", pid);
            result.put("jarPath", jarPath);
            return result;
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("status", "FAILED");
            result.put("message", "启动失败: " + e.getMessage());
            return result;
        }
    }
}
