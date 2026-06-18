package com.ops.server.controller;

import com.ops.common.response.Result;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 进程监控接口 (T-01-39)
 */
@RestController
@RequestMapping("/monitor")
public class MonitorController {

    /**
     * GET /api/monitor/process - 进程监控
     */
    @GetMapping("/process")
    public Result<?> getProcessMonitor(
            @RequestParam Long projectId,
            @RequestParam Long nodeId) {
        Map<String, Object> data = new HashMap<>();
        data.put("status", "RUNNING");
        data.put("cpu", "15.5");
        data.put("memory", "512MB");
        return Result.success(data);
    }
}
