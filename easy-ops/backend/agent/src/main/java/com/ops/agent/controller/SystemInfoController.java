package com.ops.agent.controller;

import com.ops.agent.service.SystemInfoCollector;
import com.ops.common.response.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Agent 系统信息接口
 * 返回详细的 CPU、内存、磁盘、系统等硬件信息
 */
@RestController
@RequestMapping("/sys")
public class SystemInfoController {

    @Autowired
    private SystemInfoCollector systemInfoCollector;

    /**
     * GET /api/sys/info - 获取系统详细信息（CPU/内存/磁盘/系统）
     */
    @GetMapping("/info")
    public Result<Map<String, Object>> getSystemInfo() {
        return Result.success(systemInfoCollector.collect());
    }
}
