package com.ops.agent.daemon;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程异常自动重启 Daemon (Agent侧)
 */
@Component
public class AutoRestartDaemon {

    @Value("${agent.server-url}")
    private String serverUrl;

    private final Map<String, Long> monitoredProcesses = new ConcurrentHashMap<>();
    private volatile boolean autoRestartEnabled = false;

    @Value("${agent.check-interval:30}")
    private int checkInterval;

    public void registerProcess(String projectId, long processId) {
        monitoredProcesses.put(projectId, processId);
    }

    public void unregisterProcess(String projectId) {
        monitoredProcesses.remove(projectId);
    }

    public void setAutoRestartEnabled(boolean enabled) {
        this.autoRestartEnabled = enabled;
    }

    @Scheduled(fixedRateString = "#{checkInterval * 1000}")
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

    private boolean isProcessAlive(long processId) {
        try {
            // Try to kill with signal 0 (doesn't actually kill, just checks)
            String os = System.getProperty("os.name").toLowerCase();
            Process p;
            if (os.contains("windows")) {
                p = Runtime.getRuntime().exec("tasklist /FI \"PID eq " + processId + "\"");
            } else {
                p = Runtime.getRuntime().exec("kill -0 " + processId);
            }
            int exitCode = p.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void restartProject(String projectId) {
        System.out.println("[AutoRestart] Restart requested for project: " + projectId);
    }
}
