package com.ops.server.configmgmt.controller;

import com.ops.common.model.GlobalScriptFileModel;
import com.ops.common.response.Result;
import com.ops.server.configmgmt.service.GlobalScriptMgmtService;
import com.ops.server.service.AuditLogService;
import com.ops.server.util.SecurityContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 全局脚本文件管理接口
 * 管理所有 Agent 节点的脚本/配置文件，不绑定项目
 * 用于管理 Agent 自身的脚本（如 start.sh、stop.sh）
 */
@RestController
@RequestMapping("/global-script")
public class GlobalScriptMgmtController {

    @Autowired
    private GlobalScriptMgmtService scriptMgmtService;

    @Autowired
    private SecurityContext securityContext;

    @Autowired
    private AuditLogService auditLog;

    /**
     * GET /api/global-script/files - 查询全局脚本文件列表
     */
    @GetMapping("/files")
    public Result<?> listFiles() {
        return Result.success(scriptMgmtService.listFiles());
    }

    /**
     * POST /api/global-script/files - 新增脚本文件定义
     */
    @PostMapping("/files")
    public Result<?> createFile(@RequestBody GlobalScriptFileModel model) {
        auditLog.log("GLOBAL_SCRIPT", "CREATE", "新增全局脚本文件: " + model.getFilePath());
        return Result.success(scriptMgmtService.createFile(model));
    }

    /**
     * PUT /api/global-script/files/{id} - 更新脚本文件定义
     */
    @PutMapping("/files/{id}")
    public Result<?> updateFile(@PathVariable Long id, @RequestBody GlobalScriptFileModel model) {
        model.setId(id);
        auditLog.log("GLOBAL_SCRIPT", "UPDATE", "修改全局脚本文件: " + model.getFilePath() + " (ID=" + id + ")");
        return Result.success(scriptMgmtService.updateFile(model));
    }

    /**
     * DELETE /api/global-script/files/{id} - 删除脚本文件定义
     */
    @DeleteMapping("/files/{id}")
    public Result<?> deleteFile(@PathVariable Long id) {
        scriptMgmtService.deleteFile(id);
        auditLog.log("GLOBAL_SCRIPT", "DELETE", "删除全局脚本文件: ID=" + id);
        return Result.success();
    }

    /**
     * POST /api/global-script/scan - 扫描指定目录下的脚本文件并导入
     * 扫描所有在线 Agent 节点的指定目录
     */
    @PostMapping("/scan")
    public Result<?> scanScriptFiles(@RequestParam String scanDir) {
        auditLog.log("GLOBAL_SCRIPT", "SCAN", "扫描全局脚本文件: 目录=" + scanDir);
        return Result.success(scriptMgmtService.scanAndImport(scanDir));
    }

    /**
     * GET /api/global-script/content - 读取指定节点脚本内容
     */
    @GetMapping("/content")
    public Result<?> getContent(@RequestParam Long nodeId, @RequestParam Long scriptFileId) {
        return Result.success(scriptMgmtService.getContent(nodeId, scriptFileId));
    }

    /**
     * GET /api/global-script/content/auto - 自动选在线节点读取脚本内容
     */
    @GetMapping("/content/auto")
    public Result<?> getContentAuto(@RequestParam Long scriptFileId) {
        return Result.success(scriptMgmtService.getContentAuto(scriptFileId));
    }

    /**
     * GET /api/global-script/snapshot - 获取各节点脚本快照
     */
    @GetMapping("/snapshot")
    public Result<?> getSnapshot(@RequestParam Long scriptFileId) {
        return Result.success(scriptMgmtService.getSnapshot(scriptFileId));
    }

    /**
     * POST /api/global-script/distribute - 分发脚本文件到指定节点
     */
    @PostMapping("/distribute")
    public Result<?> distribute(@RequestBody Map<String, Object> body) {
        Long scriptFileId = toLong(body.get("scriptFileId"));
        String content = body.get("content") != null ? body.get("content").toString() : "";
        List<Long> targetNodeIds = toLongList(body.get("targetNodeIds"));
        boolean setExecutable = Boolean.TRUE.equals(body.get("setExecutable"));
        boolean autoBackup = !Boolean.FALSE.equals(body.get("autoBackup")); // 默认 true

        auditLog.log("GLOBAL_SCRIPT", "DISTRIBUTE", "分发全局脚本: 脚本文件ID=" + scriptFileId + ", 节点数=" + targetNodeIds.size());
        return Result.success(scriptMgmtService.distribute(scriptFileId, content,
                targetNodeIds, setExecutable, autoBackup, securityContext.getCurrentUserId()));
    }

    /**
     * POST /api/global-script/refresh - 刷新所有节点快照哈希
     */
    @PostMapping("/refresh")
    public Result<?> refresh(@RequestBody Map<String, Object> body) {
        Long scriptFileId = toLong(body.get("scriptFileId"));
        return Result.success(scriptMgmtService.refreshSnapshots(scriptFileId));
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
