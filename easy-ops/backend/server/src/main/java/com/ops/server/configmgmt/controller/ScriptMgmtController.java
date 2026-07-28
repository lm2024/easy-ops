package com.ops.server.configmgmt.controller;

import com.ops.common.model.ProjectScriptFileModel;
import com.ops.common.response.Result;
import com.ops.server.configmgmt.service.ScriptMgmtService;
import com.ops.server.service.AuditLogService;
import com.ops.server.util.SecurityContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 脚本文件管理接口
 * 支持任意目录的脚本/配置文件管理
 */
@RestController
@RequestMapping("/script")
public class ScriptMgmtController {

    @Autowired
    private ScriptMgmtService scriptMgmtService;

    @Autowired
    private SecurityContext securityContext;

    @Autowired
    private AuditLogService auditLog;

    /**
     * GET /api/script/files - 查询项目脚本文件列表
     */
    @GetMapping("/files")
    public Result<?> listFiles(@RequestParam Long projectId) {
        if (!securityContext.hasProjectPermission(projectId)) {
            return Result.error(403, "无权访问该项目");
        }
        return Result.success(scriptMgmtService.listFiles(projectId));
    }

    /**
     * POST /api/script/files - 新增脚本文件定义
     */
    @PostMapping("/files")
    public Result<?> createFile(@RequestBody ProjectScriptFileModel model) {
        if (!securityContext.hasProjectPermission(model.getProjectId())) {
            return Result.error(403, "无权访问该项目");
        }
        auditLog.log("SCRIPT", "CREATE", "新增脚本文件: " + model.getFilePath() + ", 项目ID=" + model.getProjectId());
        return Result.success(scriptMgmtService.createFile(model));
    }

    /**
     * PUT /api/script/files/{id} - 更新脚本文件定义
     */
    @PutMapping("/files/{id}")
    public Result<?> updateFile(@PathVariable Long id, @RequestBody ProjectScriptFileModel model) {
        model.setId(id);
        if (!securityContext.hasProjectPermission(model.getProjectId())) {
            return Result.error(403, "无权访问该项目");
        }
        auditLog.log("SCRIPT", "UPDATE", "修改脚本文件: " + model.getFilePath() + " (ID=" + id + ")");
        return Result.success(scriptMgmtService.updateFile(model));
    }

    /**
     * DELETE /api/script/files/{id} - 删除脚本文件定义
     */
    @DeleteMapping("/files/{id}")
    public Result<?> deleteFile(@PathVariable Long id, @RequestParam Long projectId) {
        if (!securityContext.hasProjectPermission(projectId)) {
            return Result.error(403, "无权访问该项目");
        }
        scriptMgmtService.deleteFile(id);
        auditLog.log("SCRIPT", "DELETE", "删除脚本文件: ID=" + id + ", 项目ID=" + projectId);
        return Result.success();
    }

    /**
     * POST /api/script/scan - 扫描指定目录下的脚本文件并导入
     */
    @PostMapping("/scan")
    public Result<?> scanScriptFiles(@RequestParam Long projectId, @RequestParam String scanDir) {
        if (!securityContext.hasProjectPermission(projectId)) {
            return Result.error(403, "无权访问该项目");
        }
        auditLog.log("SCRIPT", "SCAN", "扫描脚本文件: 项目ID=" + projectId + ", 目录=" + scanDir);
        return Result.success(scriptMgmtService.scanAndImport(projectId, scanDir));
    }

    /**
     * GET /api/script/content - 读取指定节点脚本内容
     */
    @GetMapping("/content")
    public Result<?> getContent(@RequestParam Long projectId,
                                @RequestParam Long nodeId,
                                @RequestParam Long scriptFileId) {
        if (!securityContext.hasProjectPermission(projectId)) {
            return Result.error(403, "无权访问该项目");
        }
        return Result.success(scriptMgmtService.getContent(projectId, nodeId, scriptFileId));
    }

    /**
     * GET /api/script/content/auto - 自动选在线节点读取脚本内容
     */
    @GetMapping("/content/auto")
    public Result<?> getContentAuto(@RequestParam Long projectId,
                                    @RequestParam Long scriptFileId) {
        if (!securityContext.hasProjectPermission(projectId)) {
            return Result.error(403, "无权访问该项目");
        }
        return Result.success(scriptMgmtService.getContentAuto(projectId, scriptFileId));
    }

    /**
     * GET /api/script/snapshot - 获取各节点脚本快照
     */
    @GetMapping("/snapshot")
    public Result<?> getSnapshot(@RequestParam Long projectId, @RequestParam Long scriptFileId) {
        if (!securityContext.hasProjectPermission(projectId)) {
            return Result.error(403, "无权访问该项目");
        }
        return Result.success(scriptMgmtService.getSnapshot(projectId, scriptFileId));
    }

    /**
     * POST /api/script/distribute - 分发脚本文件到指定节点
     */
    @PostMapping("/distribute")
    public Result<?> distribute(@RequestBody Map<String, Object> body) {
        Long projectId = toLong(body.get("projectId"));
        if (!securityContext.hasProjectPermission(projectId)) {
            return Result.error(403, "无权访问该项目");
        }
        Long scriptFileId = toLong(body.get("scriptFileId"));
        String content = body.get("content") != null ? body.get("content").toString() : "";
        List<Long> targetNodeIds = toLongList(body.get("targetNodeIds"));
        boolean setExecutable = Boolean.TRUE.equals(body.get("setExecutable"));
        boolean autoBackup = !Boolean.FALSE.equals(body.get("autoBackup")); // 默认 true

        auditLog.log("SCRIPT", "DISTRIBUTE", "分发脚本: 脚本文件ID=" + scriptFileId + ", 项目ID=" + projectId + ", 节点数=" + targetNodeIds.size());
        return Result.success(scriptMgmtService.distribute(projectId, scriptFileId, content,
                targetNodeIds, setExecutable, autoBackup, securityContext.getCurrentUserId()));
    }

    /**
     * POST /api/script/refresh - 刷新所有节点快照哈希
     */
    @PostMapping("/refresh")
    public Result<?> refresh(@RequestBody Map<String, Object> body) {
        Long projectId = toLong(body.get("projectId"));
        Long scriptFileId = toLong(body.get("scriptFileId"));
        if (!securityContext.hasProjectPermission(projectId)) {
            return Result.error(403, "无权访问该项目");
        }
        return Result.success(scriptMgmtService.refreshSnapshots(projectId, scriptFileId));
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).longValue();
        return Long.parseLong(value.toString());
    }

    @SuppressWarnings("unchecked")
    private List<Long> toLongList(Object value) {
        if (value == null) return new ArrayList<>();
        if (value instanceof List) {
            return (List<Long>) ((List<?>) value).stream()
                    .map(this::toLong)
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
