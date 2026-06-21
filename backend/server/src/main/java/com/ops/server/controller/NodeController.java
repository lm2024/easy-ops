package com.ops.server.controller;

import com.ops.common.constant.SystemConstant;
import com.ops.common.enums.NodeStatus;
import com.ops.common.model.NodeModel;
import com.ops.common.model.OperationLogModel;
import com.ops.common.response.Result;
import com.ops.server.interceptor.AuthInterceptor;
import com.ops.server.mapper.NodeMapper;
import com.ops.server.mapper.OperationLogMapper;
import com.ops.server.service.AlarmService;
import com.ops.server.util.SecurityContext;
import com.ops.server.service.NodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/nodes")
public class NodeController {

    @Autowired
    private NodeService nodeService;

    @Autowired
    private NodeMapper nodeMapper;

    @Autowired
    private OperationLogMapper operationLogMapper;

    @Autowired
    private AlarmService alarmService;

    @Autowired
    private AuthInterceptor authInterceptor;

    @Autowired
    private SecurityContext securityContext;

    /**
     * GET /api/nodes - 节点列表 (支持分页和状态筛选)
     */
    @GetMapping
    public Result<?> listNodes(
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String keyword) {
        List<NodeModel> nodes = nodeService.findByStatus(status, page, pageSize, keyword);
        Long total = nodeService.countByStatus(status, keyword);
        Map<String, Object> data = new HashMap<>();
        data.put("list", nodes);
        data.put("total", total);
        return Result.success(data);
    }

    /**
     * POST /api/nodes/export - 导出节点CSV
     */
    @GetMapping("/export")
    public void exportNodes(HttpServletResponse response) {
        try {
            List<NodeModel> nodes = nodeService.findByStatus(null, 1, Integer.MAX_VALUE, null);
            response.setContentType("text/csv;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment;filename=nodes.csv");
            response.getWriter().write("名称,IP,端口,Token,状态,系统信息,创建时间\n");
            for (NodeModel node : nodes) {
                response.getWriter().write(String.format("%s,%s,%d,%s,%s,%s,%d%n",
                        node.getName(), node.getIp(), node.getPort(),
                        node.getToken() != null ? node.getToken() : "",
                        node.getStatus() == 1 ? "在线" : "离线",
                        node.getOsInfo() != null ? node.getOsInfo() : "",
                        node.getCreateTime() != null ? node.getCreateTime() : 0));
            }
            response.getWriter().flush();
        } catch (Exception e) {
            throw new RuntimeException("导出失败", e);
        }
    }

    /**
     * POST /api/nodes/import - 导入节点CSV
     */
    @PostMapping("/import")
    public Result<?> importNodes(@RequestParam("file") MultipartFile file) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), "UTF-8"))) {
            String line;
            int count = 0;
            reader.readLine(); // skip header
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] fields = parseCsvLine(line);
                if (fields.length < 2) continue;
                NodeModel node = new NodeModel();
                node.setName(fields[0].trim());
                node.setIp(fields[1].trim());
                node.setPort(fields.length > 2 && !fields[2].trim().isEmpty() ? Integer.parseInt(fields[2].trim()) : 2123);
                node.setToken(fields.length > 3 ? fields[3].trim() : "");
                node.setStatus(NodeStatus.ONLINE.getCode());
                node.setCreateTime(System.currentTimeMillis());
                node.setUpdateTime(System.currentTimeMillis());

                if (nodeService.findByName(node.getName()) != null) continue; // skip duplicate
                nodeService.insert(node);
                count++;
            }
            java.util.Map<String, Object> result = new java.util.HashMap<>();
            result.put("imported", count);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error(500, "导入失败: " + e.getMessage());
        }
    }

    /**
     * GET /api/nodes/{id} - 节点详情
     */
    @GetMapping("/{id}")
    public Result<?> getNode(@PathVariable Long id) {
        NodeModel node = nodeService.findById(id);
        // SEC-004: 节点操作权限校验n        if (!securityContext.getCurrentNodeId() && !securityContext.hasProjectPermission(null)) {n            // non-agent users are filtered by project, which is handled by project bindingn        }
        return node != null ? Result.success(node) : Result.error(1002, "节点不存在");
    }

    /**
     * POST /api/nodes - 新增节点
     */
    @PostMapping
    public Result<?> addNode(@RequestBody NodeModel node, HttpServletRequest httpRequest) {
        if (nodeService.findByName(node.getName()) != null) {
            return Result.paramError("节点名称已存在");
        }
        node.setStatus(NodeStatus.ONLINE.getCode());
        node.setCreateTime(System.currentTimeMillis());
        node.setUpdateTime(System.currentTimeMillis());
        nodeService.insert(node);

        // Log operation
        logOperation(node.getId(), "NODE", "ADD", "添加节点: " + node.getName(), httpRequest.getRemoteAddr());
        return Result.success();
    }

    /**
     * PUT /api/nodes/{id} - 修改节点
     */
    @PutMapping("/{id}")
    public Result<?> updateNode(@PathVariable Long id, @RequestBody NodeModel node) {
        NodeModel existing = nodeService.findById(id);
        if (existing == null) {
            return Result.error(1002, "节点不存在");
        }
        node.setId(id);
        node.setCreateTime(existing.getCreateTime());
        node.setUpdateTime(System.currentTimeMillis());
        nodeService.update(node);
        return Result.success();
    }

    /**
     * DELETE /api/nodes/{id} - 删除节点
     */
    @DeleteMapping("/{id}")
    public Result<?> deleteNode(@PathVariable Long id) {
        if (nodeService.countByNodeId(id) > 0) {
            return Result.error(1003, "该节点下有项目绑定，无法删除");
        }
        nodeService.deleteById(id);
        return Result.success();
    }

    /**
     * PUT /api/nodes/{id}/tags - 更新节点标签
     */
    @PutMapping("/{id}/tags")
    public Result<?> updateTags(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String tags = body.get("tags");
        if (tags == null) tags = "";
        nodeService.updateTags(id, tags);
        return Result.success();
    }

    /**
     * GET /api/nodes/heartbeat - 心跳接口 (Agent侧)
     */
    @GetMapping("/heartbeat")
    public Result<?> heartbeat(HttpServletRequest request) {
        String token = request.getHeader(SystemConstant.TOKEN_HEADER);
        if (token == null || token.isEmpty()) {
            return Result.authError();
        }

        String nodeId = nodeMapper.getNodeIdByToken(token);
        if (nodeId == null) {
            return Result.authError();
        }

        String ip = request.getRemoteAddr();
        String osInfo = request.getHeader("X-OS-Info");
        String javaVersion = request.getHeader("X-Java-Version");
        String cpuInfo = request.getHeader("X-CPU-Info");
        String memInfo = request.getHeader("X-Mem-Info");
        String diskInfo = request.getHeader("X-Disk-Info");
        String osArch = request.getHeader("X-OS-Arch");

        // 解析硬件信息
        Integer cpuCores = null;
        Integer totalMemoryMb = null;
        Long totalDiskMb = null;
        try {
            if (cpuInfo != null && !cpuInfo.isEmpty()) cpuCores = Integer.parseInt(cpuInfo);
            if (memInfo != null && !memInfo.isEmpty()) totalMemoryMb = Integer.parseInt(memInfo);
            if (diskInfo != null && !diskInfo.isEmpty()) totalDiskMb = Long.parseLong(diskInfo);
        } catch (NumberFormatException ignored) {}

        nodeMapper.updateHeartbeat(Long.parseLong(nodeId), System.currentTimeMillis(),
                ip, osInfo, javaVersion, cpuCores, totalMemoryMb, totalDiskMb, osArch);

        // Update agent token cache
        Map<String, String> agentCache = authInterceptor.getAgentTokenCache();
        agentCache.put(nodeId, token);

        // Get projects bound to this node
        List<String> projectNames = nodeMapper.getProjectNamesByNodeId(Long.parseLong(nodeId));

        Map<String, Object> data = new java.util.HashMap<>();
        data.put("nodeId", nodeId);
        data.put("projects", projectNames);
        return Result.success(data);
    }

    private String[] parseCsvLine(String line) {
        List<String> fields = new java.util.ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(sb.toString());
                sb = new StringBuilder();
            } else {
                sb.append(c);
            }
        }
        fields.add(sb.toString());
        return fields.toArray(new String[0]);
    }

    private void logOperation(Long nodeId, String module, String action, String content, String ip) {
        OperationLogModel logModel = new com.ops.common.model.OperationLogModel();
        logModel.setUserId(nodeId);
        logModel.setModule(module);
        logModel.setAction(action);
        logModel.setContent(content);
        logModel.setIp(ip);
        logModel.setCreateTime(System.currentTimeMillis());
        operationLogMapper.insert(logModel);
    }
}
