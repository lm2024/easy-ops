package com.ops.server.dump;

import com.ops.common.response.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Dump 文件分析接口
 */
@RestController
@RequestMapping("/dump")
public class DumpAnalyzeController {
    private static final Logger log = LoggerFactory.getLogger(DumpAnalyzeController.class);

    @Autowired
    private DumpAnalyzerService dumpAnalyzerService;

    /**
     * POST /api/dump/analyze - 上传并分析 dump 文件
     */
    @PostMapping("/analyze")
    public Result<?> analyze(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return Result.paramError("文件不能为空");
        }

        String fileName = file.getOriginalFilename();
        if (fileName == null || (!fileName.endsWith(".hprof") && !fileName.endsWith(".core"))) {
            return Result.paramError("只支持 .hprof 或 .core 格式的文件");
        }

        // 检查文件大小（限制 500MB）
        long maxSize = 500 * 1024 * 1024;
        if (file.getSize() > maxSize) {
            return Result.paramError("文件大小超过限制（最大 500MB）");
        }

        String fileId = UUID.randomUUID().toString().replace("-", "");

        try {
            log.info("[Dump分析] 收到文件: fileName={}, size={}, fileId={}", fileName, file.getSize(), fileId);

            // 异步分析（这里简化为同步，实际生产环境应该异步）
            DumpAnalyzerService.DumpAnalysisResult result = dumpAnalyzerService.analyze(
                    fileId, file.getInputStream(), fileName);

            // 构建返回结果
            Map<String, Object> data = new HashMap<>();
            data.put("fileId", fileId);
            data.put("fileName", fileName);
            data.put("fileSize", file.getSize());
            data.put("fileSizeFormatted", formatBytes(file.getSize()));
            data.put("success", result.isSuccess());
            data.put("status", result.getStatus());
            data.put("errorMsg", result.getErrorMsg());
            data.put("durationMs", result.getDurationMs());

            if (result.isSuccess()) {
                data.put("totalInstances", result.getTotalInstances());
                data.put("totalSize", result.getTotalSize());
                data.put("totalSizeFormatted", result.getTotalSizeFormatted());
                data.put("classCount", result.getClassCount());
                data.put("topClasses", result.getClassStatsList());
            }

            return Result.success(data);
        } catch (IOException e) {
            log.error("[Dump分析] 读取文件失败: {}", e.getMessage(), e);
            return Result.error(500, "读取文件失败: " + e.getMessage());
        }
    }

    /**
     * GET /api/dump/result/{fileId} - 获取分析结果
     */
    @GetMapping("/result/{fileId}")
    public Result<?> getResult(@PathVariable String fileId) {
        DumpAnalyzerService.DumpAnalysisResult result = dumpAnalyzerService.getResult(fileId);
        if (result == null) {
            return Result.error(404, "分析结果不存在");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("fileId", result.getFileId());
        data.put("success", result.isSuccess());
        data.put("status", result.getStatus());
        data.put("errorMsg", result.getErrorMsg());
        data.put("fileSize", result.getFileSize());
        data.put("totalInstances", result.getTotalInstances());
        data.put("totalSize", result.getTotalSize());
        data.put("totalSizeFormatted", result.getTotalSizeFormatted());
        data.put("classCount", result.getClassCount());
        data.put("topClasses", result.getClassStatsList());
        data.put("durationMs", result.getDurationMs());

        return Result.success(data);
    }

    /**
     * DELETE /api/dump/result/{fileId} - 删除分析结果
     */
    @DeleteMapping("/result/{fileId}")
    public Result<?> deleteResult(@PathVariable String fileId) {
        dumpAnalyzerService.deleteResult(fileId);
        return Result.success(null);
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
