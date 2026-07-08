package com.ops.server.controller;

import com.ops.common.enums.FileType;
import com.ops.common.exception.BusinessException;
import com.ops.common.model.FileAccessLogModel;
import com.ops.common.model.NodeModel;
import com.ops.common.response.Result;
import com.ops.server.client.AgentClient;
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

    @Autowired
    private AgentClient agentClient;

    @Value("${server.path:./data}")
    private String serverPath;

    /**
     * GET /api/files/log - 日志文件查看（代理 Agent）
     */
    @GetMapping("/log")
    public Result<?> viewLog(
            @RequestParam Long nodeId,
            @RequestParam(name = "logPath", defaultValue = "") String logPath,
            @RequestParam(defaultValue = "0") Integer offset,
            @RequestParam(defaultValue = "200") Integer lines) {

        NodeModel node = nodeMapper.findById(nodeId);
        if (!isValidNode(node)) {
            return Result.error(1002, "节点不存在或离线");
        }

        try {
            Map<String, String> params = new HashMap<>();
            params.put("logPath", logPath);
            params.put("offset", String.valueOf(offset));
            params.put("lines", String.valueOf(lines));
            String content = agentClient.extractDataString(agentClient.getForMap(node, "/file/log", params));
            logFileAccess(nodeId, logPath, "view");
            return Result.success(content);
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            return Result.error(500, "读取日志失败: " + e.getMessage());
        }
    }

    /**
     * GET /api/files/config - 读取YML配置（代理 Agent）
     */
    @GetMapping("/config")
    public Result<?> viewConfig(@RequestParam Long nodeId,
                                 @RequestParam(name = "configPath") String configPath) {
        NodeModel node = nodeMapper.findById(nodeId);
        if (!isValidNode(node)) {
            return Result.error(1002, "节点不存在或离线");
        }

        try {
            Map<String, String> params = new HashMap<>();
            params.put("configPath", configPath);
            String content = agentClient.extractDataString(agentClient.getForMap(node, "/file/config", params));
            logFileAccess(nodeId, configPath, "view");
            return Result.success(content);
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            return Result.error(500, "读取配置失败: " + e.getMessage());
        }
    }

    /**
     * POST /api/files/config - 保存YML配置（代理 Agent）
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

        NodeModel node = nodeMapper.findById(nodeId);
        if (!isValidNode(node)) {
            return Result.error(1002, "节点不存在或离线");
        }

        if (!configPath.endsWith(".yml") && !configPath.endsWith(".yaml")) {
            return Result.error(1007, "仅支持.yml和.yaml文件");
        }

        try {
            Map<String, Object> agentBody = new HashMap<>();
            agentBody.put("configPath", configPath);
            agentBody.put("content", content);
            agentBody.put("backup", true);
            agentClient.postForMap(node, "/file/config", agentBody);
            logFileAccess(nodeId, configPath, "edit");
            return Result.success();
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
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
                    Path batchSafe = getSafePath(path);
                    if (batchSafe == null) continue;
                    String fileName = new File(path).getName();
                    zos.putNextEntry(new ZipEntry(fileName));
                    byte[] fileContent = Files.readAllBytes(batchSafe);
                    zos.write(fileContent);
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

    private boolean isValidNode(NodeModel node) {
        return node != null && node.getStatus() != null && node.getStatus() == 1;
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

    /**
     * SEC-007: 路径遍历防护（批量下载仍使用 Server 本地路径）
     */
    private Path getSafePath(String requestedPath) {
        try {
            Path base = Paths.get(serverPath).toAbsolutePath().normalize();
            Path target = Paths.get(requestedPath).toAbsolutePath().normalize();
            if (!target.startsWith(base)) {
                return null;
            }
            return target;
        } catch (Exception e) {
            return null;
        }
    }
}
