package com.ops.agent.controller;

import com.ops.agent.service.ShellCompletionService;
import com.ops.common.response.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent Shell 命令执行与补全接口。
 */
@RestController
@RequestMapping("/shell")
public class ShellController {

    @Autowired
    private ShellCompletionService shellCompletionService;

    /**
     * POST /api/shell/exec - 执行 Shell 命令
     */
    @PostMapping("/exec")
    public Result<Map<String, Object>> exec(@RequestBody Map<String, String> request) {
        String command = request.get("command");
        if (command == null || command.trim().isEmpty()) {
            return Result.paramError("命令不能为空");
        }

        try {
            String os = System.getProperty("os.name").toLowerCase();
            String[] cmdArray = os.contains("windows")
                    ? new String[]{"cmd.exe", "/c", command}
                    : new String[]{"/bin/bash", "-c", command};

            ProcessBuilder pb = new ProcessBuilder(cmdArray);
            pb.redirectErrorStream(true);

            long start = System.currentTimeMillis();
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS);
            int exitCode;
            if (!finished) {
                process.destroyForcibly();
                output.append("\n[执行超时 - 60秒]");
                exitCode = -1;
            } else {
                exitCode = process.exitValue();
            }
            long elapsed = System.currentTimeMillis() - start;

            Map<String, Object> data = new HashMap<>();
            data.put("stdout", output.toString());
            data.put("exitCode", exitCode);
            data.put("elapsed", elapsed);
            return Result.success(data);
        } catch (Exception e) {
            Map<String, Object> data = new HashMap<>();
            data.put("stdout", "执行失败: " + e.getMessage() + "\n");
            data.put("exitCode", -1);
            data.put("elapsed", 0);
            return Result.success(data);
        }
    }

    /**
     * POST /api/shell/complete - Tab 补全候选
     */
    @PostMapping("/complete")
    public Result<Map<String, Object>> complete(@RequestBody Map<String, String> request) {
        String cwd = request.getOrDefault("cwd", "/");
        String line = request.getOrDefault("line", "");
        int cursor = parseCursor(request.get("cursor"), line.length());
        List<String> candidates = shellCompletionService.complete(cwd, line, cursor);
        Map<String, Object> data = new HashMap<>();
        data.put("candidates", candidates);
        return Result.success(data);
    }

    private int parseCursor(String cursor, int defaultValue) {
        if (cursor == null || cursor.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Math.max(0, Integer.parseInt(cursor.trim()));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
