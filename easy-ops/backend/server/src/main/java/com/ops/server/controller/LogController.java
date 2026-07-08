package com.ops.server.controller;

import com.ops.common.response.Result;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 日志查看接口 (T-01-29, T-01-30, T-01-31)
 */
@RestController
@RequestMapping("/logs")
public class LogController {

    /**
     * GET /api/logs/file - 分页读取项目日志
     */
    @GetMapping("/file")
    public Result<?> getLogFile(
            @RequestParam Long nodeId,
            @RequestParam(required = false) String path,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "100") Integer pageSize,
            @RequestParam(required = false) String keyword) {

        List<String> lines = new ArrayList<>();
        int total = 0;

        // In production: call Agent /api/log/tail?nodeId={}&path={}&page={}&pageSize={}
        Map<String, Object> data = new HashMap<>();
        data.put("lines", lines);
        data.put("total", total);
        return Result.success(data);
    }

    /**
     * GET /api/logs/console - 实时控制台 (WebSocket endpoint handled by ConsoleHandler)
     */
    @GetMapping("/console")
    public Map<String, Object> getConsole(
            @RequestParam Long projectId,
            @RequestParam Long nodeId) {
        Map<String, Object> data = new HashMap<>();
        data.put("message", "WebSocket连接请使用 /ws/console 端点");
        return data;
    }
}
