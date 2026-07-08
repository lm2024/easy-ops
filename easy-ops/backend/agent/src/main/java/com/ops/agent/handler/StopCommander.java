package com.ops.agent.handler;

import com.ops.agent.daemon.AutoRestartDaemon;

import java.util.HashMap;
import java.util.Map;

/**
 * 停止命令处理器
 */
public class StopCommander {

    private final AutoRestartDaemon autoRestartDaemon;

    public StopCommander(AutoRestartDaemon autoRestartDaemon) {
        this.autoRestartDaemon = autoRestartDaemon;
    }

    public Map<String, Object> execute(String projectId, long processId) {
        try {
            // In Java 8, we can't use ProcessHandle directly
            // Use a platform-specific approach
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("windows")) {
                Runtime.getRuntime().exec("taskkill /PID " + processId + " /F");
            } else {
                Runtime.getRuntime().exec("kill -9 " + processId);
            }
            autoRestartDaemon.unregisterProcess(projectId);

            Map<String, Object> result = new HashMap<>();
            result.put("status", "SUCCESS");
            result.put("message", "Project stopped successfully");
            result.put("processId", processId);
            return result;
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("status", "FAILED");
            result.put("message", "停止失败: " + e.getMessage());
            return result;
        }
    }
}
