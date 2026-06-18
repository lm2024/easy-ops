package com.ops.agent.daemon;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程异常自动重启 Daemon (Agent侧)
 * 定时检查受管进程状态，异常则自动重启
 */
@Component
public class AutoRestartDaemon {

    @Value("${agent.server-url}")
    private String serverUrl;

    /** 受管进程监控: projectId -> processId */
    private final Map<String, Long> monitoredProcesses = new ConcurrentHashMap<>();

    /** 自动重启开关 (由Server配置控制) */
    private volatile boolean autoRestartEnabled = false;

    /** 检查间隔 (秒) */
    @Value("${agent.check-interval:30}")
    private int checkInterval;

    /**
     * 注册受管进程
     */
    public void registerProcess(String projectId, long processId) {
        monitoredProcesses.put(projectId, processId);
    }

    /**
     * 取消监控
     */
    public void unregisterProcess(String projectId) {
        monitoredProcesses.remove(projectId);
    }

    /**
     * 设置自动重启开关
     */
    public void setAutoRestartEnabled(boolean enabled) {
        this.autoRestartEnabled = enabled;
    }

    /**
     * 定时检查进程状态，异常则重启
     */
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

    /**
     * 检查进程是否存活
     */
    private boolean isProcessAlive(long processId) {
        return ProcessHandle.of((long) processId).map(p -> {
            ProcessHandle.Info info = p.info();
            return !info.state().equals(ProcessHandle.State.TERMINATED);
        }).orElse(false);
    }

    /**
     * 重启项目 (通过调用ProcessController)
     */
    private void restartProject(String projectId) {
        // In a real implementation, this would call the local ProcessController
        // or execute the start script. For now, this is a placeholder.
        System.out.println("[AutoRestart] Restart requested for project: " + projectId);
    }
}
