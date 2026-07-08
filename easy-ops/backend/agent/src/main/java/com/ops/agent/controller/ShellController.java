package com.ops.agent.controller;

import com.ops.common.response.Result;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Agent Shell命令执行接口
 * 接收Server转发的控制台命令并执行，返回结果
 */
@RestController
@RequestMapping("/shell")
public class ShellController {

    /**
     * POST /api/shell/exec - 执行Shell命令
     */
    @PostMapping("/exec")
    public Result<Map<String, Object>> exec(@RequestBody Map<String, String> request) {
        String command = request.get("command");
        if (command == null || command.trim().isEmpty()) {
            return Result.paramError("命令不能为空");
        }

        try {
            String os = System.getProperty("os.name").toLowerCase();
            String[] cmdArray;
            if (os.contains("windows")) {
                cmdArray = new String[]{"cmd.exe", "/c", command};
            } else {
                cmdArray = new String[]{"/bin/sh", "-c", command};
            }

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

            // 等待执行完成，最多30秒超时
            boolean finished = process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
            int exitCode;
            if (!finished) {
                process.destroyForcibly();
                output.append("\n[执行超时 - 30秒]");
                exitCode = -1;
            } else {
                exitCode = process.exitValue();
            }
            long elapsed = System.currentTimeMillis() - start;

            Map<String, Object> data = new HashMap<>();
            data.put("stdout", output.toString().trim());
            data.put("exitCode", exitCode);
            data.put("elapsed", elapsed);

            return Result.success(data);
        } catch (Exception e) {
            Map<String, Object> data = new HashMap<>();
            data.put("stdout", "执行失败: " + e.getMessage());
            data.put("exitCode", -1);
            data.put("elapsed", 0);
            return Result.success(data);
        }
    }
}
