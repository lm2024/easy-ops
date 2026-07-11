package com.ops.server.controller;

import com.ops.common.response.Result;
import com.ops.server.config.GlobalPathProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 系统全局配置（路径等）
 */
@RestController
@RequestMapping("/system")
public class SystemSettingsController {

    @Autowired
    private GlobalPathProperties globalPathProperties;

    /**
     * GET /api/system/paths - 获取全局路径配置
     */
    @GetMapping("/paths")
    public Result<?> getGlobalPaths() {
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("deployBaseDir", globalPathProperties.getDeployBaseDir());
        data.put("logSubDir", globalPathProperties.getLogSubDir());
        data.put("configSubDir", globalPathProperties.getConfigSubDir());
        data.put("frontendSubDir", globalPathProperties.getFrontendSubDir());
        return Result.success(data);
    }
}
