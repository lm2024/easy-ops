package com.ops.server.logmgmt.controller;

import com.ops.common.model.ProjectLogProfileModel;
import com.ops.common.response.Result;
import com.ops.server.logmgmt.service.LogMgmtService;
import com.ops.server.util.SecurityContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 日志管理接口
 */
@RestController
@RequestMapping("/logs")
public class LogMgmtController {

    @Autowired
    private LogMgmtService logMgmtService;

    @Autowired
    private SecurityContext securityContext;

    /**
     * GET /api/logs/profile - 获取项目日志配置
     */
    @GetMapping("/profile")
    public Result<?> getProfile(@RequestParam Long projectId) {
        if (!securityContext.hasProjectPermission(projectId)) {
            return Result.error(403, "无权访问该项目");
        }
        return Result.success(logMgmtService.getProfile(projectId));
    }

    /**
     * POST /api/logs/profile - 保存项目日志配置
     */
    @PostMapping("/profile")
    public Result<?> saveProfile(@RequestBody ProjectLogProfileModel profile) {
        if (!securityContext.hasProjectPermission(profile.getProjectId())) {
            return Result.error(403, "无权访问该项目");
        }
        return Result.success(logMgmtService.saveProfile(profile));
    }

    /**
     * GET /api/logs/files - 列出节点日志文件
     */
    @GetMapping("/files")
    public Result<?> listFiles(@RequestParam Long projectId, @RequestParam Long nodeId) {
        if (!securityContext.hasProjectPermission(projectId)) {
            return Result.error(403, "无权访问该项目");
        }
        return Result.success(logMgmtService.listLogFiles(projectId, nodeId));
    }

    /**
     * GET /api/logs/view - 单节点分页查看
     */
    @GetMapping("/view")
    public Result<?> viewLog(@RequestParam Long projectId,
                             @RequestParam Long nodeId,
                             @RequestParam(required = false) String fileName,
                             @RequestParam(defaultValue = "0") Integer offset,
                             @RequestParam(defaultValue = "200") Integer lines) {
        if (!securityContext.hasProjectPermission(projectId)) {
            return Result.error(403, "无权访问该项目");
        }
        return Result.success(logMgmtService.viewLog(projectId, nodeId, fileName, offset, lines));
    }

    /**
     * GET /api/logs/aggregate - 多节点聚合日志
     */
    @GetMapping("/aggregate")
    public Result<?> aggregate(@RequestParam Long projectId,
                               @RequestParam(required = false) List<Long> nodeIds,
                               @RequestParam(defaultValue = "1") Integer page,
                               @RequestParam(defaultValue = "100") Integer pageSize,
                               @RequestParam(required = false) Long since) {
        if (!securityContext.hasProjectPermission(projectId)) {
            return Result.error(403, "无权访问该项目");
        }
        return Result.success(logMgmtService.aggregate(projectId, nodeIds, page, pageSize, since));
    }

    /**
     * POST /api/logs/search - 关键词搜索
     */
    @PostMapping("/search")
    public Result<?> search(@RequestBody Map<String, Object> body) {
        Long projectId = toLong(body.get("projectId"));
        if (!securityContext.hasProjectPermission(projectId)) {
            return Result.error(403, "无权访问该项目");
        }
        String keyword = body.get("keyword") != null ? body.get("keyword").toString() : "";
        String scope = body.get("scope") != null ? body.get("scope").toString() : "AGGREGATE";
        @SuppressWarnings("unchecked")
        List<Long> nodeIds = (List<Long>) body.get("nodeIds");
        int contextLines = body.get("contextLines") != null
                ? Integer.parseInt(body.get("contextLines").toString()) : 3;
        int maxResults = body.get("maxResults") != null
                ? Integer.parseInt(body.get("maxResults").toString()) : 200;
        return Result.success(logMgmtService.search(projectId, keyword, scope,
                nodeIds, contextLines, maxResults));
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.parseLong(value.toString());
    }
}
