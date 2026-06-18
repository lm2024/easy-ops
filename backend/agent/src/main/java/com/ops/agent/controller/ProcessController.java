package com.ops.agent.controller;

import com.ops.common.response.Result;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Agent进程管理接口
 */
@RestController
@RequestMapping("/process")
public class ProcessController {

    @PostMapping("/{projectId}/start")
    public Result<Map<String, Object>> start(@PathVariable String projectId,
                                              @RequestParam String jarPath,
                                              @RequestParam(required = false) String jvmOpts,
                                              @RequestParam(required = false) String envVars) {
        try {
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
                pb.environment().putAll(parseEnvVars(envVars));
            }

            pb.command(shell, flag, cmd.toString());
            pb.redirectErrorStream(true);
            Process process = pb.start();

            Map<String, Object> data = new HashMap<>();
            data.put("processId", process.hashCode());
            data.put("projectId", projectId);
            data.put("status", "RUNNING");
            return Result.success(data);
        } catch (Exception e) {
            return Result.error(500, "启动失败: " + e.getMessage());
        }
    }

    @PostMapping("/{projectId}/stop")
    public Result<Map<String, Object>> stop(@PathVariable String projectId,
                                             @RequestParam long processId) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("windows")) {
                Runtime.getRuntime().exec("taskkill /PID " + processId + " /F");
            } else {
                Runtime.getRuntime().exec("kill -9 " + processId);
            }
            Map<String, Object> data = new HashMap<>();
            data.put("processId", processId);
            data.put("projectId", projectId);
            data.put("status", "STOPPED");
            return Result.success(data);
        } catch (Exception e) {
            return Result.error(500, "停止失败: " + e.getMessage());
        }
    }

    @PostMapping("/{projectId}/restart")
    public Result<Map<String, Object>> restart(@PathVariable String projectId,
                                                @RequestParam String jarPath,
                                                @RequestParam(required = false) String jvmOpts) {
        try {
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

            pb.command(shell, flag, cmd.toString());
            pb.redirectErrorStream(true);
            Process process = pb.start();

            Map<String, Object> data = new HashMap<>();
            data.put("processId", process.hashCode());
            data.put("projectId", projectId);
            data.put("status", "RUNNING");
            return Result.success(data);
        } catch (Exception e) {
            return Result.error(500, "重启失败: " + e.getMessage());
        }
    }

    @GetMapping("/{projectId}/status")
    public Result<Map<String, Object>> status(@PathVariable String projectId,
                                               @RequestParam long processId) {
        try {
            // In Java 8, we can't use ProcessHandle
            // Just return the status based on the processId
            Map<String, Object> data = new HashMap<>();
            data.put("processId", processId);
            data.put("projectId", projectId);
            data.put("status", "UNKNOWN");
            return Result.success(data);
        } catch (Exception e) {
            return Result.error(500, "查询状态失败: " + e.getMessage());
        }
    }

    private Map<String, String> parseEnvVars(String envVars) {
        Map<String, String> map = new HashMap<>();
        if (envVars == null || envVars.isEmpty()) return map;
        String[] pairs = envVars.split(";");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                map.put(kv[0].trim(), kv[1].trim());
            }
        }
        return map;
    }
}
