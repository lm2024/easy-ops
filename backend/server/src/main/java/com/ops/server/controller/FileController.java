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
            @RequestParam String path,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "100") Integer pageSize,
            @RequestParam(required = false) String keyword) {

        if (!isValidNode(nodeId)) {
            return Result.error(1002, "节点不存在或离线");
        }

        List<String> lines = readLogFile(path, page, pageSize, keyword);
        Map<String, Object> data = new HashMap<>();
        data.put("lines", lines);
        data.put("total", lines.size());
        return Result.success(data);
    }

    /**
     * GET /api/files/config - 读取YML配置
     */
    @GetMapping("/config")
    public Result<?> viewConfig(@RequestParam Long nodeId, @RequestParam String path) {
        if (!isValidNode(nodeId)) {
            return Result.error(1002, "节点不存在或离线");
        }

        String content = readFileContent(path);
        Map<String, Object> data = new HashMap<>();
        data.put("content", content);
        return Result.success(data);
    }

    /**
     * POST /api/files/config - 保存YML配置
     */
    @PostMapping("/config")
    public Result<?> saveConfig(@RequestParam Long nodeId, @RequestParam String path,
                                 @RequestBody String content) {
        if (!isValidNode(nodeId)) {
            return Result.error(1002, "节点不存在或离线");
        }

        // Validate file extension
        if (!path.endsWith(".yml") && !path.endsWith(".yaml")) {
            return Result.error(1007, "仅支持.yml和.yaml文件");
        }

        try {
            Files.write(Paths.get(path), content.getBytes("UTF-8"));
            logFileAccess(nodeId, path, "view");
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

    private List<String> readLogFile(String path, int page, int pageSize, String keyword) {
        List<String> allLines = new ArrayList<>();
        try {
            List<String> lines = Files.readAllLines(Paths.get(path));
            int start = (page - 1) * pageSize;
            int end = Math.min(start + pageSize, lines.size());
            for (int i = start; i < end; i++) {
                if (keyword == null || keyword.isEmpty() || lines.get(i).contains(keyword)) {
                    allLines.add(lines.get(i));
                }
            }
        } catch (IOException e) {
            return allLines;
        }
        return allLines;
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
