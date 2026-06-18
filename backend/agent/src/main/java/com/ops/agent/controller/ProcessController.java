package com.ops.agent.controller;

import com.ops.common.response.Result;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;

/**
 * Agent进程管理接口
 * 接收Server指令，执行项目启停
 */
@RestController
@RequestMapping("/process")
public class ProcessController {

    /**
     * 启动项目
     * 执行startScript，返回process ID
     */
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

            Map<String, Object> data = Map.of(
                    "processId", process.pid(),
                    "projectId", projectId,
                    "status", "RUNNING"
            );
            return Result.success(data);
        } catch (Exception e) {
            return Result.error("启动失败: " + e.getMessage());
        }
    }

    /**
     * 停止项目
     * 通过进程ID停止
     */
    @PostMapping("/{projectId}/stop")
    public Result<Map<String, Object>> stop(@PathVariable String projectId,
                                             @RequestParam long processId) {
        try {
            ProcessHandle.of((long) processId).ifPresent(Process::destroy);
            return Result.success(Map.of(
                    "processId", processId,
                    "projectId", projectId,
                    "status", "STOPPED"
            ));
        } catch (Exception e) {
            return Result.error("停止失败: " + e.getMessage());
        }
    }

    /**
     * 重启项目
     * 先stop再start
     */
    @PostMapping("/{projectId}/restart")
    public Result<Map<String, Object>> restart(@PathVariable String projectId,
                                                @RequestParam String jarPath,
                                                @RequestParam(required = false) String jvmOpts) {
        try {
            // Get current process list for this project
            // For simplicity, just restart by finding the jar process
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

            return Result.success(Map.of(
                    "processId", process.pid(),
                    "projectId", projectId,
                    "status", "RUNNING"
            ));
        } catch (Exception e) {
            return Result.error("重启失败: " + e.getMessage());
        }
    }

    /**
     * 获取进程状态
     */
    @GetMapping("/{projectId}/status")
    public Result<Map<String, Object>> status(@PathVariable String projectId,
                                               @RequestParam long processId) {
        try {
            ProcessHandle.of((long) processId).ifPresent(process -> {
                // Process exists
            });
            boolean alive = ProcessHandle.of((long) processId).isPresent();
            return Result.success(Map.of(
                    "processId", processId,
                    "projectId", projectId,
                    "status", alive ? "RUNNING" : "STOPPED"
            ));
        } catch (Exception e) {
            return Result.error("查询状态失败: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> parseEnvVars(String envVars) {
        Map<String, String> map = new java.util.HashMap<>();
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
