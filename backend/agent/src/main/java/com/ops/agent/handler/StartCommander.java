package com.ops.agent.handler;

import com.ops.agent.daemon.AutoRestartDaemon;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 启动命令处理器
 * 执行项目启动脚本，注册进程到自动重启监控
 */
public class StartCommander {

    private final AutoRestartDaemon autoRestartDaemon;
    private final String serverPath;

    public StartCommander(AutoRestartDaemon autoRestartDaemon, String serverPath) {
        this.autoRestartDaemon = autoRestartDaemon;
        this.serverPath = serverPath;
    }

    /**
     * 执行启动命令
     *
     * @param projectId  项目ID
     * @param jarName    Jar包名
     * @param jvmOpts    JVM参数
     * @param envVars    环境变量
     * @return 启动结果
     */
    public Map<String, Object> execute(String projectId, String jarName,
                                        String jvmOpts, String envVars) {
        try {
            String jarPath = serverPath + "/versions/" + projectId + "/" + jarName;
            File jarFile = new File(jarPath);

            if (!jarFile.exists()) {
                return Map.of("status", "FAILED", "message", "Jar包不存在: " + jarPath);
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
            long pid = process.pid();

            // Register for auto-restart monitoring
            autoRestartDaemon.registerProcess(projectId, pid);

            return Map.of(
                    "status", "SUCCESS",
                    "message", "Project started successfully",
                    "processId", pid,
                    "jarPath", jarPath
            );
        } catch (Exception e) {
            return Map.of(
                    "status", "FAILED",
                    "message", "启动失败: " + e.getMessage()
            );
        }
    }
}
