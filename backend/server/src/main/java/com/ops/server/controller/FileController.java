package com.ops.server.controller;

import com.ops.common.enums.FileAction;
import com.ops.common.enums.FileType;
import com.ops.common.model.FileAccessLogModel;
import com.ops.common.model.NodeModel;
import com.ops.common.response.Result;
import com.ops.server.mapper.FileAccessLogMapper;
import com.ops.server.mapper.NodeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 文件管理接口 (T-01-31 ~ T-01-38)
 */
@RestController
@RequestMapping("/files")
public class FileController {

    @Autowired
    private NodeMapper nodeMapper;

    @Autowired
    private FileAccessLogMapper fileAccessLogMapper;

    @Value("${server.path:./data}")
    private String serverPath;

    /**
     * GET /api/files/log - 日志文件查看
     */
    @GetMapping("/log")
    public Result<?> viewLog(
            @RequestParam Long nodeId,
            @RequestParam(name = "logPath", defaultValue = "") String logPath,
            @RequestParam(defaultValue = "0") Integer offset,
            @RequestParam(defaultValue = "200") Integer lines) {

        if (!isValidNode(nodeId)) {
            return Result.error(1002, "节点不存在或离线");
        }

        String content = readLogFileContent(logPath, offset, lines);
        return Result.success(content);
    }

    /**
     * GET /api/files/config - 读取YML配置
     */
    @GetMapping("/config")
    public Result<?> viewConfig(@RequestParam Long nodeId,
                                 @RequestParam(name = "configPath") String configPath) {
        if (!isValidNode(nodeId)) {
            return Result.error(1002, "节点不存在或离线");
        }

        String content = readFileContent(configPath);
        return Result.success(content);
    }

    /**
     * POST /api/files/config - 保存YML配置
     */
    @PostMapping("/config")
    public Result<?> saveConfig(@RequestBody Map<String, Object> body) {
        Long nodeId = body.get("nodeId") != null ? Long.valueOf(body.get("nodeId").toString()) : null;
        Object configPathObj = body.get("configPath");
        Object contentObj = body.get("content");

        if (nodeId == null || configPathObj == null || contentObj == null) {
            return Result.paramError("nodeId、configPath、content 不能为空");
        }

        String configPath = configPathObj.toString();
        String content = contentObj.toString();

        if (!isValidNode(nodeId)) {
            return Result.error(1002, "节点不存在或离线");
        }

        // Validate file extension
        if (!configPath.endsWith(".yml") && !configPath.endsWith(".yaml")) {
            return Result.error(1007, "仅支持.yml和.yaml文件");
        }

        try {
            Files.write(Paths.get(configPath), content.getBytes("UTF-8"));
            logFileAccess(nodeId, configPath, "view");
            return Result.success();
        } catch (IOException e) {
            return Result.error(500, "保存失败: " + e.getMessage());
        }
    }

    /**
     * POST /api/files/batch-download - 批量下载
     */
    @PostMapping("/batch-download")
    public ResponseEntity<StreamingResponseBody> batchDownload(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<Map<String, String>> items = (List<Map<String, String>>) request.get("items");

        StreamingResponseBody body = outputStream -> {
            try (ZipOutputStream zos = new ZipOutputStream(outputStream)) {
                for (Map<String, String> item : items) {
                    String path = item.get("path");
                    String fileName = new File(path).getName();
                    zos.putNextEntry(new ZipEntry(fileName));
                    byte[] content = Files.readAllBytes(Paths.get(path));
                    zos.write(content);
                    zos.closeEntry();
                    logFileAccess(
                            Long.parseLong(item.get("nodeId")),
                            path,
                            "download");
                }
            }
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=files.zip")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(body);
    }

    private boolean isValidNode(Long nodeId) {
        NodeModel node = nodeMapper.findById(nodeId);
        return node != null && node.getStatus() == 1;
    }

    private String readLogFileContent(String path, int offset, int lines) {
        if (path.isEmpty()) return "";
        try {
            List<String> allLines = Files.readAllLines(Paths.get(path));
            int start = Math.min(offset, allLines.size());
            int end = Math.min(start + lines, allLines.size());
            StringBuilder sb = new StringBuilder();
            for (int i = start; i < end; i++) {
                sb.append(allLines.get(i)).append("\n");
            }
            return sb.toString().trim();
        } catch (IOException e) {
            return "";
        }
    }

    private String readFileContent(String path) {
        try {
            return new String(Files.readAllBytes(Paths.get(path)), "UTF-8");
        } catch (IOException e) {
            return "";
        }
    }

    private void logFileAccess(Long nodeId, String path, String action) {
        Map<String, Object> log = new HashMap<>();
        log.put("nodeId", nodeId);
        log.put("filePath", path);
        log.put("action", action);
        log.put("fileType", FileType.LOG.getExt());
        log.put("createTime", System.currentTimeMillis());
        fileAccessLogMapper.insert(log);
    }
}
