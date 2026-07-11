package com.ops.agent.controller;

import com.ops.agent.upgrade.AgentUpgradeService;
import com.ops.common.response.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Agent 系统升级接口。
 */
@RestController
@RequestMapping("/system")
public class SystemUpgradeController {

    @Autowired
    private AgentUpgradeService upgradeService;

    /**
     * GET /system/version - 当前 Agent 版本。
     */
    @GetMapping("/version")
    public Result<Map<String, Object>> version() {
        return Result.success(upgradeService.versionInfo());
    }

    /**
     * POST /system/upgrade - 上传新 Jar 并触发自升级重启。
     */
    @PostMapping("/upgrade")
    public Result<Map<String, Object>> upgrade(@RequestParam("file") MultipartFile file,
                                               @RequestParam(required = false) String sha256) {
        try {
            return Result.success(upgradeService.upgrade(file, sha256));
        } catch (Exception e) {
            return Result.error(500, "Agent 升级失败: " + e.getMessage());
        }
    }
}
