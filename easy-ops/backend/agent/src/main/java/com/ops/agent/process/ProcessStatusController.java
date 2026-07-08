package com.ops.agent.process;

import com.ops.common.response.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 进程状态与健康探针 REST 接口。
 */
@RestController
@RequestMapping("/process")
public class ProcessStatusController {

    private final ProcessStatusChecker statusChecker = new ProcessStatusChecker();
    private final HttpHealthProber healthProber = new HttpHealthProber();
    private final ProcessMetricsHelper metricsHelper = new ProcessMetricsHelper(statusChecker);

    /**
     * GET /process/status — 进程存活检测（ps grep deployDir + jarName）。
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> status(@RequestParam String deployDir,
                                              @RequestParam String jarName) {
        try {
            return Result.success(statusChecker.checkStatus(deployDir, jarName));
        } catch (Exception e) {
            return Result.error(500, "进程状态检测失败: " + e.getMessage());
        }
    }

    /**
     * GET /process/probe — HTTP 健康探针（GET/POST/HEAD）。
     */
    @GetMapping("/probe")
    public Result<Map<String, Object>> probe(
            @RequestParam(defaultValue = "GET") String method,
            @RequestParam String url,
            @RequestParam(defaultValue = "200") int expectedStatus,
            @RequestParam(defaultValue = "3000") int timeoutMs,
            @RequestParam(required = false) String body,
            @RequestParam(required = false) String headers) {
        try {
            return Result.success(healthProber.probe(method, url, expectedStatus, timeoutMs, body, headers));
        } catch (Exception e) {
            return Result.error(500, "健康探针失败: " + e.getMessage());
        }
    }

    /**
     * GET /process/metrics — 进程 CPU/内存指标。
     */
    @GetMapping("/metrics")
    public Result<Map<String, Object>> metrics(@RequestParam String deployDir,
                                                @RequestParam String jarName) {
        try {
            return Result.success(metricsHelper.getProcessMetrics(deployDir, jarName));
        } catch (Exception e) {
            return Result.error(500, "进程指标采集失败: " + e.getMessage());
        }
    }

    /**
     * GET /process/jvm — JVM 指标（jstat，需有效 PID）。
     */
    @GetMapping("/jvm")
    public Result<Map<String, Object>> jvm(@RequestParam long pid) {
        if (pid <= 0) {
            return Result.paramError("pid 必须大于 0");
        }
        try {
            return Result.success(metricsHelper.getJvmMetrics(pid));
        } catch (Exception e) {
            return Result.error(500, "JVM 指标采集失败: " + e.getMessage());
        }
    }
}
