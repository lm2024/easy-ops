package com.ops.server.controller;

import com.ops.common.response.Result;
import com.ops.server.arthas.ArthasDiagnoseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.util.Map;

/**
 * Arthas 诊断接口
 * 由前端调用，负责诊断会话生命周期管理和命令透传
 */
@RestController
@RequestMapping("/arthas")
public class ArthasDiagnoseController {
    private static final Logger log = LoggerFactory.getLogger(ArthasDiagnoseController.class);

    @Autowired
    private ArthasDiagnoseService diagnoseService;

    /**
     * POST /api/arthas/diagnose/start - 启动诊断会话
     */
    @PostMapping("/diagnose/start")
    public Result<?> start(@RequestBody Map<String, Object> body) {
        Long projectId = Long.parseLong(body.get("projectId").toString());
        Long nodeId = Long.parseLong(body.get("nodeId").toString());
        long pid = Long.parseLong(body.get("pid").toString());
        try {
            return Result.success(diagnoseService.startDiagnose(projectId, nodeId, pid));
        } catch (Exception e) {
            log.error("启动诊断失败: projectId={}, nodeId={}, pid={}, error={}", projectId, nodeId, pid, e.getMessage());
            return Result.error(500, "启动诊断失败: " + e.getMessage());
        }
    }

    /**
     * POST /api/arthas/diagnose/stop - 结束诊断会话
     */
    @PostMapping("/diagnose/stop")
    public Result<?> stop(@RequestBody Map<String, Object> body) {
        String sessionId = body.get("sessionId").toString();
        try {
            return Result.success(diagnoseService.stopDiagnose(sessionId));
        } catch (Exception e) {
            log.error("结束诊断失败: sessionId={}, error={}", sessionId, e.getMessage());
            return Result.error(500, "结束诊断失败: " + e.getMessage());
        }
    }

    /**
     * GET /api/arthas/diagnose/status - 查询会话状态
     */
    @GetMapping("/diagnose/status")
    public Result<?> status(@RequestParam String sessionId) {
        try {
            return Result.success(diagnoseService.getStatus(sessionId));
        } catch (Exception e) {
            return Result.error(500, "查询状态失败: " + e.getMessage());
        }
    }

    /**
     * POST /api/arthas/diagnose/exec - 执行命令
     */
    @PostMapping("/diagnose/exec")
    public Result<?> exec(@RequestBody Map<String, Object> body) {
        String sessionId = body.get("sessionId").toString();
        String command = body.get("command") != null ? body.get("command").toString() : "";
        int timeoutMs = body.get("timeoutMs") != null
                ? Integer.parseInt(body.get("timeoutMs").toString()) : 30000;
        if (command.isEmpty()) {
            return Result.paramError("命令不能为空");
        }
        try {
            return Result.success(diagnoseService.execCommand(sessionId, command, timeoutMs));
        } catch (Exception e) {
            log.error("执行命令失败: sessionId={}, command={}, error={}", sessionId, command, e.getMessage());
            return Result.error(500, "命令执行失败: " + e.getMessage());
        }
    }

    /**
     * GET /api/arthas/diagnose/history - 诊断历史列表
     */
    @GetMapping("/diagnose/history")
    public Result<?> history(@RequestParam Long projectId,
                              @RequestParam(required = false) Long nodeId,
                              @RequestParam(required = false) String status,
                              @RequestParam(required = false) Long startTime,
                              @RequestParam(required = false) Long endTime,
                              @RequestParam(defaultValue = "1") int page,
                              @RequestParam(defaultValue = "20") int pageSize) {
        try {
            return Result.success(
                    diagnoseService.getHistory(projectId, nodeId, status, startTime, endTime, page, pageSize));
        } catch (Exception e) {
            return Result.error(500, "查询历史失败: " + e.getMessage());
        }
    }

    /**
     * GET /api/arthas/diagnose/detail - 诊断详情
     */
    @GetMapping("/diagnose/detail")
    public Result<?> detail(@RequestParam Long id) {
        try {
            return Result.success(diagnoseService.getDetail(id));
        } catch (Exception e) {
            return Result.error(500, "查询详情失败: " + e.getMessage());
        }
    }

    /**
     * DELETE /api/arthas/diagnose/delete - 删除诊断记录及其命令结果
     */
    @DeleteMapping("/diagnose/delete")
    public Result<?> delete(@RequestParam Long id) {
        try {
            diagnoseService.deleteDiagnose(id);
            return Result.success(null);
        } catch (Exception e) {
            log.error("删除诊断记录失败: id={}, error={}", id, e.getMessage());
            return Result.error(500, "删除失败: " + e.getMessage());
        }
    }

    /**
     * GET /api/arthas/diagnose/flamegraph-list - 火焰图历史文件列表
     */
    @GetMapping("/diagnose/flamegraph-list")
    public Result<?> flamegraphList(@RequestParam String sessionId) {
        try {
            return Result.success(diagnoseService.getFlamegraphList(sessionId));
        } catch (Exception e) {
            return Result.error(500, "获取火焰图历史列表失败: " + e.getMessage());
        }
    }

    /**
     * POST /api/arthas/diagnose/auto - 一键诊断（自动分析内存问题）
     */
    @PostMapping("/diagnose/auto")
    public Result<?> autoDiagnose(@RequestBody Map<String, Object> body) {
        String sessionId = body.get("sessionId").toString();
        String diagnoseType = body.get("type") != null ? body.get("type").toString() : "jmap-histo";
        try {
            return Result.success(diagnoseService.autoDiagnose(sessionId, diagnoseType));
        } catch (Exception e) {
            log.error("一键诊断失败: sessionId={}, type={}, error={}", sessionId, diagnoseType, e.getMessage());
            return Result.error(500, "诊断失败: " + e.getMessage());
        }
    }

    /**
     * GET /api/arthas/diagnose/flamegraph/download - 下载火焰图文件
     */
    @GetMapping("/diagnose/flamegraph/download")
    public void downloadFlamegraph(@RequestParam String sessionId,
                                    @RequestParam String fileName,
                                    HttpServletResponse response) {
        try {
            // 通过 Service 获取下载 URL，然后重定向
            String url = diagnoseService.getFlamegraphDownloadUrl(sessionId, fileName);
            response.sendRedirect(url);
        } catch (Exception e) {
            response.setStatus(500);
            log.error("下载火焰图失败: sessionId={}, fileName={}, error={}", sessionId, fileName, e.getMessage());
        }
    }
}
