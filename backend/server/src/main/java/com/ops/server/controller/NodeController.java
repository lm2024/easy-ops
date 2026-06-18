package com.ops.server.controller;

import com.ops.common.constant.SystemConstant;
import com.ops.common.enums.NodeStatus;
import com.ops.common.model.NodeModel;
import com.ops.common.response.Result;
import com.ops.server.interceptor.AuthInterceptor;
import com.ops.server.mapper.NodeMapper;
import com.ops.server.mapper.OperationLogMapper;
import com.ops.server.service.AlarmService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/nodes")
public class NodeController {

    @Autowired
    private NodeMapper nodeMapper;

    @Autowired
    private OperationLogMapper operationLogMapper;

    @Autowired
    private AlarmService alarmService;

    @Autowired
    private AuthInterceptor authInterceptor;

    /**
     * GET /api/nodes - 节点列表 (支持分页和状态筛选)
     */
    @GetMapping
    public Result<?> listNodes(
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String keyword) {
        List<NodeModel> nodes = nodeMapper.findByStatus(status, page, pageSize, keyword);
        Long total = nodeMapper.countByStatus(status, keyword);
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("list", nodes);
        data.put("total", total);
        return Result.success(data);
    }

    /**
     * GET /api/nodes/{id} - 节点详情
     */
    @GetMapping("/{id}")
    public Result<?> getNode(@PathVariable Long id) {
        NodeModel node = nodeMapper.findById(id);
        return node != null ? Result.success(node) : Result.error(1002, "节点不存在");
    }

    /**
     * POST /api/nodes - 新增节点
     */
    @PostMapping
    public Result<?> addNode(@RequestBody NodeModel node, HttpServletRequest httpRequest) {
        if (nodeMapper.findByName(node.getName()) != null) {
            return Result.paramError("节点名称已存在");
        }
        node.setStatus(NodeStatus.ONLINE.getCode());
        node.setCreateTime(System.currentTimeMillis());
        node.setUpdateTime(System.currentTimeMillis());
        nodeMapper.insert(node);

        // Log operation
        logOperation(node.getId(), "NODE", "ADD", "添加节点: " + node.getName(), httpRequest.getRemoteAddr());
        return Result.success();
    }

    /**
     * PUT /api/nodes/{id} - 修改节点
     */
    @PutMapping("/{id}")
    public Result<?> updateNode(@PathVariable Long id, @RequestBody NodeModel node) {
        NodeModel existing = nodeMapper.findById(id);
        if (existing == null) {
            return Result.error(1002, "节点不存在");
        }
        node.setId(id);
        node.setCreateTime(existing.getCreateTime());
        node.setUpdateTime(System.currentTimeMillis());
        nodeMapper.update(node);
        return Result.success();
    }

    /**
     * DELETE /api/nodes/{id} - 删除节点
     */
    @DeleteMapping("/{id}")
    public Result<?> deleteNode(@PathVariable Long id) {
        if (nodeMapper.countByNodeId(id) > 0) {
            return Result.error(1003, "该节点下有项目绑定，无法删除");
        }
        nodeMapper.deleteById(id);
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

        nodeMapper.updateHeartbeat(Long.parseLong(nodeId), System.currentTimeMillis(),
                ip, osInfo, javaVersion);

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
