package com.ops.agent.daemon;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程异常自动重启 Daemon (Agent侧)
 * (Task 4: 使用真实 PID 检查进程状态)
 *
 * @deprecated 已由 Server 侧 SelfHeal 模块统一调度，Agent 仅提供 /process/status 等检测接口
 */
@Deprecated
@Component
public class AutoRestartDaemon implements CommandLineRunner {

    @Value("${agent.server-url:http://localhost:8081/api}")
    private String serverUrl;

    @Value("${agent.token:}")
    private String agentToken;

    @Value("${agent.check-interval:30}")
    private int checkInterval;

    private final Map<String, Long> monitoredProcesses = new ConcurrentHashMap<>();
    private volatile boolean autoRestartEnabled = false;

    /**
     * 启动时校验 Token 必须配置 (Task 2 新增)
     */
    @Override
    public void run(String... args) {
        if (agentToken == null || agentToken.trim().isEmpty()) {
            throw new IllegalStateException(
                "SECURITY VIOLATION: AGENT_TOKEN is not configured in AutoRestartDaemon. " +
                "Please set the AGENT_TOKEN environment variable."
            );
        }
    }

    public void registerProcess(String projectId, long processId) {
        monitoredProcesses.put(projectId, processId);
    }

    public void unregisterProcess(String projectId) {
        monitoredProcesses.remove(projectId);
    }

    public void setAutoRestartEnabled(boolean enabled) {
        this.autoRestartEnabled = enabled;
    }

    @Scheduled(fixedDelayString = "${agent.check-interval:30}000")
    public void checkAndRestart() {
        if (!autoRestartEnabled) return;

        for (Map.Entry<String, Long> entry : monitoredProcesses.entrySet()) {
            String projectId = entry.getKey();
            long processId = entry.getValue();

            if (!isProcessAlive(processId)) {
                try {
                    System.out.println("[AutoRestart] Process " + processId
                            + " for project " + projectId + " is dead, restarting...");
                    restartProject(projectId);
                } catch (Exception e) {
                    System.err.println("[AutoRestart] Restart failed: " + e.getMessage());
                }
            }
        }
    }

    /**
     * (Task 4) 使用真实 PID 检查进程存活状态
     * 替代原来的 kill -0 <hashCode> 错误逻辑
     */
    boolean isProcessAlive(long processId) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("linux") || os.contains("darwin") || os.contains("mac")) {
                // Linux/macOS: 检查 /proc/<pid> 是否存在（Linux）或用 kill -0
                if (new File("/proc/" + processId).exists()) {
                    return true;
                }
                // Fallback: kill -0 只用于检查权限（不真正 kill）
                Process p = Runtime.getRuntime().exec("kill -0 " + processId);
                int exitCode = p.waitFor();
                return exitCode == 0;
            } else if (os.contains("windows")) {
                // Windows: 通过 tasklist 检查
                Process tl = Runtime.getRuntime().exec(
                    new String[]{"cmd.exe", "/c", "tasklist /FI \"PID eq " + processId + "\"" }
                );
                String line = new BufferedReader(new InputStreamReader(tl.getInputStream())).readLine();
                tl.waitFor();
                return line != null && line.contains(String.valueOf(processId));
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    private void restartProject(String projectId) {
        System.out.println("[AutoRestart] Restart requested for project: " + projectId);
    }
}
