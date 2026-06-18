package com.ops.agent.handler;

import com.ops.agent.daemon.AutoRestartDaemon;

import java.util.Map;

/**
 * 停止命令处理器
 * 执行项目停止脚本，从自动重启监控中移除
 */
public class StopCommander {

    private final AutoRestartDaemon autoRestartDaemon;

    public StopCommander(AutoRestartDaemon autoRestartDaemon) {
        this.autoRestartDaemon = autoRestartDaemon;
    }

    /**
     * 执行停止命令
     *
     * @param projectId  项目ID
     * @param processId  进程ID
     * @return 停止结果
     */
    public Map<String, Object> execute(String projectId, long processId) {
        try {
            ProcessHandle.of((long) processId).ifPresent(Process::destroy);
            autoRestartDaemon.unregisterProcess(projectId);

            return Map.of(
                    "status", "SUCCESS",
                    "message", "Project stopped successfully",
                    "processId", processId
            );
        } catch (Exception e) {
            return Map.of(
                    "status", "FAILED",
                    "message", "停止失败: " + e.getMessage()
            );
        }
    }
}
