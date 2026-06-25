package com.ops.server.controller;

import com.ops.common.response.Result;
import com.ops.server.monitorapp.controller.AppMonitorController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 监控接口入口（兼容旧 /monitor/process，应用监控委托 AppMonitorController）
 */
@RestController
@RequestMapping("/monitor")
public class MonitorController {

    @Autowired
    private AppMonitorController appMonitorController;

    /**
     * GET /api/monitor/process - 进程监控（委托单节点详情）
     */
    @GetMapping("/process")
    public Result<?> getProcessMonitor(
            @RequestParam Long projectId,
            @RequestParam Long nodeId) {
        return appMonitorController.nodeDetail(projectId, nodeId);
    }
}
