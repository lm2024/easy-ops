package com.ops.server.configmgmt.controller;

import com.ops.common.model.ProjectConfigFileModel;
import com.ops.common.response.Result;
import com.ops.server.configmgmt.service.ConfigMgmtService;
import com.ops.server.service.AuditLogService;
import com.ops.server.util.SecurityContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 配置文件管理接口
 */
@RestController
@RequestMapping("/config")
public class ConfigMgmtController {

    @Autowired
    private ConfigMgmtService configMgmtService;

    @Autowired
    private SecurityContext securityContext;

    @Autowired
    private AuditLogService auditLog;

    /**
     * GET /api/config/files - 查询项目配置文件列表
     */
    @GetMapping("/files")
    public Result<?> listFiles(@RequestParam Long projectId) {
        if (!securityContext.hasProjectPermission(projectId)) {
            return Result.error(403, "无权访问该项目");
        }
        return Result.success(configMgmtService.listFiles(projectId));
    }

    /**
     * POST /api/config/files - 新增配置文件定义
     */
    @PostMapping("/files")
    public Result<?> createFile(@RequestBody ProjectConfigFileModel model) {
        if (!securityContext.hasProjectPermission(model.getProjectId())) {
            return Result.error(403, "无权访问该项目");
        }
        auditLog.log("CONFIG", "CREATE", "新增配置文件: " + model.getRelativePath() + ", 项目ID=" + model.getProjectId());
        return Result.success(configMgmtService.createFile(model));
    }

    /**
     * PUT /api/config/files/{id} - 更新配置文件定义
     */
    @PutMapping("/files/{id}")
    public Result<?> updateFile(@PathVariable Long id, @RequestBody ProjectConfigFileModel model) {
        model.setId(id);
        if (!securityContext.hasProjectPermission(model.getProjectId())) {
            return Result.error(403, "无权访问该项目");
        }
        auditLog.log("CONFIG", "UPDATE", "修改配置文件: " + model.getRelativePath() + " (ID=" + id + ")");
        return Result.success(configMgmtService.updateFile(model));
    }

    /**
     * DELETE /api/config/files/{id} - 删除配置文件定义
     */
    @DeleteMapping("/files/{id}")
    public Result<?> deleteFile(@PathVariable Long id, @RequestParam Long projectId) {
        if (!securityContext.hasProjectPermission(projectId)) {
            return Result.error(403, "无权访问该项目");
        }
        configMgmtService.deleteFile(id);
        auditLog.log("CONFIG", "DELETE", "删除配置文件: ID=" + id + ", 项目ID=" + projectId);
        return Result.success();
    }

    /**
     * GET /api/config/snapshot - 获取各节点配置快照
     */
    @GetMapping("/snapshot")
    public Result<?> getSnapshot(@RequestParam Long projectId, @RequestParam Long configFileId) {
        if (!securityContext.hasProjectPermission(projectId)) {
            return Result.error(403, "无权访问该项目");
        }
        return Result.success(configMgmtService.getSnapshot(projectId, configFileId));
    }

    /**
     * GET /api/config/content - 读取指定节点配置内容
     */
    @GetMapping("/content")
    public Result<?> getContent(@RequestParam Long projectId,
                                @RequestParam Long nodeId,
                                @RequestParam Long configFileId) {
        if (!securityContext.hasProjectPermission(projectId)) {
            return Result.error(403, "无权访问该项目");
        }
        return Result.success(configMgmtService.getContent(projectId, nodeId, configFileId));
    }

    /**
     * GET /api/config/content/auto - 自动选在线节点读取配置内容
     * 不需要指定 nodeId，自动找第一个在线节点读取
     */
    @GetMapping("/content/auto")
    public Result<?> getContentAuto(@RequestParam Long projectId,
                                    @RequestParam Long configFileId) {
        if (!securityContext.hasProjectPermission(projectId)) {
            return Result.error(403, "无权访问该项目");
        }
        return Result.success(configMgmtService.getContentAuto(projectId, configFileId));
    }

    /**
     * POST /api/config/compare - 多节点配置对比
     */
    @PostMapping("/compare")
    public Result<?> compare(@RequestBody Map<String, Object> body) {
        Long projectId = toLong(body.get("projectId"));
        Long configFileId = toLong(body.get("configFileId"));
        Long baseNodeId = toLong(body.get("baseNodeId"));
        List<Long> targetNodeIds = toLongList(body.get("targetNodeIds"));
        if (!securityContext.hasProjectPermission(projectId)) {
            return Result.error(403, "无权访问该项目");
        }
        return Result.success(configMgmtService.compare(projectId, configFileId, baseNodeId, targetNodeIds));
    }

    /**
     * POST /api/config/distribute - 批量/单独分发配置
     */
    @PostMapping("/distribute")
    public Result<?> distribute(@RequestBody Map<String, Object> body) {
        Long projectId = toLong(body.get("projectId"));
        if (!securityContext.hasProjectPermission(projectId)) {
            return Result.error(403, "无权访问该项目");
        }
        Long configFileId = toLong(body.get("configFileId"));
        String content = body.get("content") != null ? body.get("content").toString() : "";
        List<Long> targetNodeIds = toLongList(body.get("targetNodeIds"));
        String distributeType = body.get("distributeType") != null
                ? body.get("distributeType").toString() : "BATCH";
        boolean restartAfter = Boolean.TRUE.equals(body.get("restartAfter"));
        auditLog.log("CONFIG", "DISTRIBUTE", "分发配置: 配置文件ID=" + configFileId + ", 项目ID=" + projectId + ", 节点数=" + targetNodeIds.size() + ", 重启=" + restartAfter);
        return Result.success(configMgmtService.distribute(projectId, configFileId, content,
                targetNodeIds, distributeType, restartAfter, securityContext.getCurrentUserId()));
    }

    /**
     * POST /api/config/refresh - 刷新所有节点快照哈希
     */
    @PostMapping("/refresh")
    public Result<?> refresh(@RequestBody Map<String, Object> body) {
        Long projectId = toLong(body.get("projectId"));
        Long configFileId = toLong(body.get("configFileId"));
        if (!securityContext.hasProjectPermission(projectId)) {
            return Result.error(403, "无权访问该项目");
        }
        return Result.success(configMgmtService.refreshSnapshots(projectId, configFileId));
    }

    /**
     * POST /api/config/scan - 自动扫描 Agent 节点的配置文件并导入
     * 遍历项目的所有在线节点，扫描 config/ 目录下的配置文件，
     * 若发现尚未在 DB 中注册的文件则自动创建记录。
     */
    @PostMapping("/scan")
    public Result<?> scanConfigFiles(@RequestParam Long projectId) {
        if (!securityContext.hasProjectPermission(projectId)) {
            return Result.error(403, "无权访问该项目");
        }
        return Result.success(configMgmtService.scanAndImport(projectId));
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

    @SuppressWarnings("unchecked")
    private List<Long> toLongList(Object value) {
        if (value == null) {
            return new ArrayList<>();
        }
        if (value instanceof List) {
            return (List<Long>) ((List<?>) value).stream()
                    .map(this::toLong)
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
