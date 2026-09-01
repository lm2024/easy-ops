package com.ops.server.filemgr;

import com.ops.common.response.Result;
import com.ops.server.client.AgentClient;
import com.ops.server.service.TenantResourceAccessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件管理接口（FTP 式浏览 / 单文件下载 / ZIP 分卷压缩下载 / 上传）。
 * 真实文件操作在 Agent 侧，Server 仅做权限校验与流式中转。
 */
@RestController
@RequestMapping("/filemgr")
public class FileMgrController {

    @Autowired
    private FileMgrService fileMgrService;
    @Autowired
    private AgentClient agentClient;
    @Autowired
    private TenantResourceAccessService tenantResourceAccessService;

    /** 可访问根目录。 */
    @GetMapping("/roots")
    public Result<List<String>> roots(@RequestParam Long nodeId) {
        tenantResourceAccessService.requireNode(nodeId);
        return Result.success(fileMgrService.roots(nodeId));
    }

    /** 列目录。 */
    @GetMapping("/list")
    public Result<Map<String, Object>> list(@RequestParam Long nodeId,
                                            @RequestParam(required = false) String path) {
        tenantResourceAccessService.requireNode(nodeId);
        return Result.success(fileMgrService.list(nodeId, path));
    }

    /** 文件/目录信息。 */
    @GetMapping("/info")
    public Result<Map<String, Object>> info(@RequestParam Long nodeId,
                                            @RequestParam String path) {
        tenantResourceAccessService.requireNode(nodeId);
        return Result.success(fileMgrService.info(nodeId, path));
    }

    /** 单文件直接流式下载（小文件，Agent 流式代理）。 */
    @GetMapping("/direct")
    public ResponseEntity<StreamingResponseBody> direct(@RequestParam Long nodeId,
                                                        @RequestParam String path) {
        tenantResourceAccessService.requireNode(nodeId);
        Map<String, Object> meta = fileMgrService.info(nodeId, path);
        String fileName = meta.get("name") != null ? meta.get("name").toString() : "download";
        StreamingResponseBody body = os -> {
            try {
                agentClient.streamTo(fileMgrService.requireOnlineNode(nodeId),
                        "/filemgr/raw", singleParam("path", path), os);
            } catch (java.io.IOException e) {
                // 客户端断开等，忽略
            }
        };
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, attachment(fileName))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(body);
    }

    // ==================== 压缩下载任务 ====================

    /** 创建压缩下载任务（多文件/目录/大文件 → ZIP 分卷）。 */
    @PostMapping("/task/create")
    public Result<Map<String, Object>> createTask(@RequestBody Map<String, Object> body) {
        Long nodeId = toLong(body.get("nodeId"));
        if (nodeId == null) {
            return Result.paramError("nodeId 不能为空");
        }
        tenantResourceAccessService.requireNode(nodeId);
        Object pathsObj = body.get("paths");
        if (!(pathsObj instanceof List)) {
            return Result.paramError("paths 不能为空");
        }
        @SuppressWarnings("unchecked")
        List<String> paths = new ArrayList<>();
        for (Object o : (List<?>) pathsObj) {
            if (o != null) {
                paths.add(o.toString());
            }
        }
        String baseName = body.get("baseName") != null ? body.get("baseName").toString() : null;
        return Result.success(fileMgrService.createTask(nodeId, paths, baseName));
    }

    /** 节点下载任务列表（实时状态）。 */
    @GetMapping("/task/list")
    public Result<List<Map<String, Object>>> listTasks(@RequestParam Long nodeId) {
        tenantResourceAccessService.requireNode(nodeId);
        return Result.success(fileMgrService.listTasks(nodeId));
    }

    /** 任务详情。 */
    @GetMapping("/task/{id}")
    public Result<Map<String, Object>> getTask(@RequestParam Long nodeId, @PathVariable String id) {
        tenantResourceAccessService.requireNode(nodeId);
        return Result.success(fileMgrService.getTask(nodeId, id));
    }

    /** 取消任务（优雅终止）。 */
    @PostMapping("/task/{id}/cancel")
    public Result<Map<String, Object>> cancelTask(@RequestParam Long nodeId, @PathVariable String id) {
        tenantResourceAccessService.requireNode(nodeId);
        return Result.success(fileMgrService.cancelTask(nodeId, id));
    }

    /** 删除任务（清理产物）。 */
    @PostMapping("/task/{id}/delete")
    public Result<Boolean> deleteTask(@RequestParam Long nodeId, @PathVariable String id) {
        tenantResourceAccessService.requireNode(nodeId);
        fileMgrService.deleteTask(nodeId, id);
        return Result.success(true);
    }

    /** 下载指定分卷（流式代理，支持大文件）。 */
    @GetMapping("/task/{id}/part/{index}")
    public ResponseEntity<StreamingResponseBody> downloadPart(@RequestParam Long nodeId,
                                                              @PathVariable String id,
                                                              @PathVariable int index) {
        tenantResourceAccessService.requireNode(nodeId);
        Map<String, Object> partMeta = fileMgrService.taskPartMeta(nodeId, id, index);
        String fileName = partMeta != null && partMeta.get("name") != null
                ? partMeta.get("name").toString() : ("part_" + index);
        StreamingResponseBody body = os -> {
            try {
                agentClient.streamTo(fileMgrService.requireOnlineNode(nodeId),
                        "/filemgr/task/" + id + "/part/" + index, null, os);
            } catch (java.io.IOException e) {
                // 客户端断开等，忽略
            }
        };
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, attachment(fileName))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(body);
    }

    // ==================== 上传 ====================

    /** 上传文件到节点指定目录（流式转发，不落 Server 磁盘）。 */
    @PostMapping("/upload")
    public Result<Map<String, Object>> upload(@RequestParam Long nodeId,
                                              @RequestParam String dir,
                                              @RequestParam MultipartFile file) {
        tenantResourceAccessService.requireNode(nodeId);
        try {
            String fileName = file.getOriginalFilename();
            if (fileName == null || fileName.trim().isEmpty()) {
                fileName = "upload.bin";
            }
            Map<String, String> params = new HashMap<>();
            params.put("dir", dir);
            Map<String, Object> resp = agentClient.postMultipartStream(
                    fileMgrService.requireOnlineNode(nodeId), "/filemgr/upload",
                    "file", fileName, file.getInputStream(), file.getSize(), params);
            agentClient.ensureAgentSuccess(resp);
            return Result.success(agentClient.extractDataMap(resp));
        } catch (Exception e) {
            return Result.error(500, "上传失败: " + e.getMessage());
        }
    }

    private Map<String, String> singleParam(String key, String value) {
        Map<String, String> m = new HashMap<>();
        m.put(key, value);
        return m;
    }

    private Long toLong(Object o) {
        if (o == null) {
            return null;
        }
        return o instanceof Number ? ((Number) o).longValue() : Long.parseLong(o.toString());
    }

    private String attachment(String fileName) {
        try {
            String encoded = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20");
            return "attachment; filename*=UTF-8''" + encoded;
        } catch (java.io.UnsupportedEncodingException e) {
            return "attachment";
        }
    }
}
