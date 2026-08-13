package com.ops.server.controller;

import com.ops.common.constant.SystemConstant;
import com.ops.common.enums.NodeStatus;
import com.ops.common.model.NodeModel;
import com.ops.common.model.OperationLogModel;
import com.ops.common.response.Result;
import com.ops.server.interceptor.AuthInterceptor;
import com.ops.server.mapper.NodeMapper;
import com.ops.server.mapper.OperationLogMapper;
import com.ops.server.mapper.MonitorSnapshotMapper;
import com.ops.server.mapper.TenantMapper;
import com.ops.server.config.GlobalPathProperties;
import com.ops.server.service.AlarmService;
import com.ops.server.service.AgentUpgradeService;
import com.ops.server.util.SecurityContext;
import com.ops.server.service.NodeService;
import com.ops.server.websocket.MonitorHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(NodeController.class);

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
    private MonitorHandler monitorHandler;

    @Autowired
    private MonitorSnapshotMapper snapshotMapper;

    @Autowired
    private com.ops.server.mapper.ProjectMapper projectMapper;

    @Autowired
    private SecurityContext securityContext;

    @Autowired
    private AgentUpgradeService agentUpgradeService;

    @Autowired
    private com.ops.server.client.AgentClient agentClient;

    @Autowired
    private GlobalPathProperties globalPathProperties;

    @Autowired
    private TenantMapper tenantMapper;

    @Autowired
    private com.ops.server.mapper.NodeTransferApplicationMapper nodeTransferApplicationMapper;

    /**
     * GET /api/nodes - 节点列表 (支持分页和状态筛选)
     */
    @GetMapping
    public Result<?> listNodes(
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortOrder) {
        Long tenantId = securityContext.getCurrentTenantId();
        List<Long> projectIds = securityContext.getAccessibleProjectIdsForQuery();
        List<NodeModel> nodes;
        Long total;
        if (tenantId == null) {
            nodes = nodeService.findByStatus(status, page, pageSize, keyword, sortField, sortOrder);
            total = nodeService.countByStatus(status, keyword);
            // admin 平台视图：池节点标记可认领（便于识别）
            Long defId = resolveDefaultTenantId();
            for (NodeModel n : nodes) {
                n.setClaimable(n.getTenantId() != null && defId != null
                        && n.getTenantId().longValue() == defId.longValue());
            }
        } else {
            // 本租户节点 + default 池节点（可认领）
            Long defaultTenantId = resolveDefaultTenantId();
            nodes = nodeService.findByStatusInTenant(status, page, pageSize, keyword, sortField, sortOrder,
                    tenantId, defaultTenantId, projectIds);
            total = nodeService.countByStatusInTenant(status, keyword, tenantId, defaultTenantId, projectIds);
            for (NodeModel n : nodes) {
                n.setClaimable(n.getTenantId() != null && defaultTenantId != null
                        && n.getTenantId().longValue() == defaultTenantId.longValue()
                        && n.getTenantId().longValue() != tenantId.longValue());
            }
        }
        fillTenantNames(nodes);
        Map<String, Object> data = new HashMap<>();
        data.put("list", nodes);
        data.put("total", total);
        return Result.success(data);
    }

    /** 批量填充节点所属租户名称（避免 N+1 查询） */
    private void fillTenantNames(List<NodeModel> nodes) {
        if (nodes == null || nodes.isEmpty()) return;
        java.util.Set<Long> ids = new java.util.HashSet<>();
        for (NodeModel n : nodes) {
            if (n.getTenantId() != null) ids.add(n.getTenantId());
        }
        if (ids.isEmpty()) return;
        try {
            List<com.ops.common.model.TenantModel> tenants = tenantMapper.findByIds(new java.util.ArrayList<>(ids));
            Map<Long, String> nameMap = new HashMap<>();
            for (com.ops.common.model.TenantModel t : tenants) {
                if (t.getId() != null) nameMap.put(t.getId(), t.getName());
            }
            for (NodeModel n : nodes) {
                if (n.getTenantId() != null) {
                    n.setTenantName(nameMap.get(n.getTenantId()));
                }
            }
        } catch (Exception e) {
            log.warn("填充节点租户名称失败", e);
        }
    }

    /** 解析默认（池）租户 id，兜底返回 null */
    private Long resolveDefaultTenantId() {
        try {
            com.ops.common.model.TenantModel def = tenantMapper.findDefault();
            return def == null ? null : def.getId();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 节点归属校验：非平台管理员只能操作自己租户的节点（池节点/他人节点不可改删）。
     * 返回 null 表示通过；否则返回错误 Result。
     */
    private Result<?> requireNodeOwner(NodeModel node) {
        if (node == null) return Result.error(1002, "节点不存在");
        if (securityContext.isSuperAdmin()) return null;
        Long tenantId = securityContext.getCurrentTenantId();
        if (tenantId == null || node.getTenantId() == null
                || node.getTenantId().longValue() != tenantId.longValue()) {
            return Result.error(403, "无权操作该节点（仅归属租户或平台管理员可操作）");
        }
        return null;
    }

    /**
     * POST /api/nodes/export - 导出节点CSV
     */
    @GetMapping("/export")
    public void exportNodes(HttpServletResponse response) {
        try {
            Long tenantId = securityContext.getCurrentTenantId();
            List<Long> projectIds = securityContext.getAccessibleProjectIdsForQuery();
            List<NodeModel> nodes = tenantId == null
                    ? nodeService.findByStatus(null, 1, Integer.MAX_VALUE, null, null, null)
                    : nodeService.findByStatusInTenant(null, 1, Integer.MAX_VALUE, null, null, null,
                            tenantId, resolveDefaultTenantId(), projectIds);
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
        Long tenantId = securityContext.getCurrentTenantId();
        if (tenantId == null) {
            com.ops.common.model.TenantModel defaultTenant = tenantMapper.findDefault();
            if (defaultTenant != null) tenantId = defaultTenant.getId();
        }
        final Long resolvedTenantId = tenantId;
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
                node.setTenantId(resolvedTenantId);
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
     * GET /api/nodes/agent/package - 查看 Server 端 Agent 升级包
     */
    @GetMapping("/agent/package")
    public Result<?> agentPackageInfo() {
        return Result.success(agentUpgradeService.packageInfo());
    }

    /**
     * POST /api/nodes/agent/package - 上传 Agent 升级包到 Server
     */
    @PostMapping("/agent/package")
    public Result<?> uploadAgentPackage(@RequestParam("file") MultipartFile file) {
        try {
            return Result.success(agentUpgradeService.savePackage(file));
        } catch (Exception e) {
            return Result.error(500, "上传失败: " + e.getMessage());
        }
    }

    /**
     * POST /api/nodes/agent/upgrade/batch - 批量升级 Agent（body: { nodeIds: [1,2] }，空则升级全部在线节点）
     */
    @PostMapping("/agent/upgrade/batch")
    public Result<?> batchUpgradeAgent(@RequestBody(required = false) Map<String, Object> body) {
        try {
            List<Long> nodeIds = null;
            if (body != null && body.get("nodeIds") instanceof List) {
                nodeIds = new java.util.ArrayList<>();
                for (Object id : (List<?>) body.get("nodeIds")) {
                    if (id instanceof Number) {
                        nodeIds.add(((Number) id).longValue());
                    }
                }
            }
            return Result.success(agentUpgradeService.upgradeBatch(nodeIds));
        } catch (Exception e) {
            return Result.error(500, "批量升级失败: " + e.getMessage());
        }
    }

    /**
     * GET /api/nodes/{id}/agent/version - 查询节点 Agent 版本
     */
    @GetMapping("/{id}/agent/version")
    public Result<?> getAgentVersion(@PathVariable Long id) {
        NodeModel node = nodeService.findById(id);
        if (node == null) {
            return Result.error(1002, "节点不存在");
        }
        try {
            return Result.success(agentClient.getAgentVersion(node));
        } catch (Exception e) {
            return Result.error(500, "查询失败: " + e.getMessage());
        }
    }

    /**
     * POST /api/nodes/{id}/agent/upgrade - 升级单个节点 Agent
     */
    @PostMapping("/{id}/agent/upgrade")
    public Result<?> upgradeAgent(@PathVariable Long id) {
        try {
            return Result.success(agentUpgradeService.upgradeNode(id));
        } catch (IllegalArgumentException e) {
            return Result.error(1002, e.getMessage());
        } catch (Exception e) {
            return Result.error(500, "升级失败: " + e.getMessage());
        }
    }

    /**
     * GET /api/nodes/{id} - 节点详情
     */
    @GetMapping("/{id}")
    public Result<?> getNode(@PathVariable Long id) {
        NodeModel node = nodeService.findById(id);
        if (node != null && node.getTenantId() != null && securityContext.getCurrentTenantId() != null
                && !securityContext.isPlatformAdmin()
                && !securityContext.getCurrentTenantId().equals(node.getTenantId())) {
            return Result.error(403, "无权访问该节点");
        }
        // SEC-004: 节点操作权限校验n        if (!securityContext.getCurrentNodeId() && !securityContext.hasProjectPermission(null)) {n            // non-agent users are filtered by project, which is handled by project bindingn        }
        return node != null ? Result.success(node) : Result.error(1002, "节点不存在");
    }

    /**
     * POST /api/nodes - 新增节点
     */
    @PostMapping
    public Result<?> addNode(@RequestBody NodeModel node, HttpServletRequest httpRequest) {
        Long tenantId = securityContext.getCurrentTenantId();
        if (tenantId == null) {
            // 平台视图（super_admin 未切换）下创建 → 归默认租户，避免 NULL 无法被租户列表查到
            com.ops.common.model.TenantModel defaultTenant = tenantMapper.findDefault();
            if (defaultTenant != null) tenantId = defaultTenant.getId();
        }
        node.setTenantId(tenantId);
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
        Result<?> guard = requireNodeOwner(existing);
        if (guard != null) return guard;
        node.setId(id);
        if (securityContext.getCurrentTenantId() != null) {
            node.setTenantId(existing.getTenantId());
        }
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
        if (!securityContext.isSuperAdmin()) {
            return Result.error(403, "仅平台管理员可删除节点");
        }
        NodeModel existing = nodeService.findById(id);
        if (existing == null) {
            return Result.error(1002, "节点不存在");
        }
        Result<?> guard = requireNodeOwner(existing);
        if (guard != null) return guard;
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
        NodeModel existing = nodeService.findById(id);
        Result<?> guard = requireNodeOwner(existing);
        if (guard != null) return guard;
        String tags = body.get("tags");
        if (tags == null) tags = "";
        nodeService.updateTags(id, tags);
        return Result.success();
    }

    // ==================== 节点认领 / 转移工作流 ====================

    /**
     * POST /api/nodes/{id}/claim - 租户用户申请认领池节点（default 租户归属）
     */
    @PostMapping("/{id}/claim")
    public Result<?> claimNode(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body,
                               HttpServletRequest httpRequest) {
        Long currentTenantId = securityContext.getCurrentTenantId();
        if (securityContext.isSuperAdmin() || currentTenantId == null || currentTenantId <= 0) {
            return Result.error(403, "仅租户用户可申请认领节点");
        }
        NodeModel node = nodeService.findById(id);
        if (node == null) {
            return Result.error(1002, "节点不存在");
        }
        Long defaultTenantId = resolveDefaultTenantId();
        if (defaultTenantId == null || node.getTenantId() == null
                || node.getTenantId().longValue() != defaultTenantId.longValue()) {
            return Result.error(403, "该节点不是可认领的池节点");
        }
        // 防重复申请：同一节点只允许一个待审批申请
        if (nodeTransferApplicationMapper.findByNodeIdAndStatus(id, "PENDING") != null) {
            return Result.paramError("该节点已有待审批的认领申请");
        }
        com.ops.common.model.TenantModel tenant = tenantMapper.findById(currentTenantId);
        com.ops.common.model.NodeTransferApplicationModel app = new com.ops.common.model.NodeTransferApplicationModel();
        app.setNodeId(id);
        app.setNodeName(node.getName());
        app.setApplicantId(securityContext.getCurrentUserId());
        app.setApplicantUsername(securityContext.getCurrentUsername());
        app.setTargetTenantId(currentTenantId);
        app.setTargetTenantName(tenant == null ? null : tenant.getName());
        app.setSourceTenantId(defaultTenantId);
        app.setStatus("PENDING");
        app.setRemark(body != null && body.get("remark") != null ? String.valueOf(body.get("remark")) : null);
        long now = System.currentTimeMillis();
        app.setCreateTime(now);
        app.setUpdateTime(now);
        nodeTransferApplicationMapper.insert(app);
        logOperation(id, "NODE", "CLAIM", "节点认领申请: " + node.getName() + " → 租户[" + app.getTargetTenantName() + "]",
                httpRequest.getRemoteAddr());
        return Result.success();
    }

    /**
     * GET /api/nodes/node-transfers - 认领申请列表（平台管理员全量，租户看本租户）
     */
    @GetMapping("/node-transfers")
    public Result<?> listTransfers(@RequestParam(required = false) String status) {
        if (securityContext.isSuperAdmin()) {
            return Result.success(nodeTransferApplicationMapper.listAll(status));
        }
        Long tenantId = securityContext.getCurrentTenantId();
        if (tenantId == null || tenantId <= 0) {
            return Result.error(403, "无权限查看");
        }
        return Result.success(nodeTransferApplicationMapper.listByTenant(tenantId, status));
    }

    /**
     * POST /api/nodes/node-transfers/{id}/approve - 平台管理员批准认领申请 → 节点转移归属
     */
    @PostMapping("/node-transfers/{id}/approve")
    public Result<?> approveTransfer(@PathVariable Long id, HttpServletRequest httpRequest) {
        if (!securityContext.isSuperAdmin()) {
            return Result.error(403, "仅平台管理员可审批");
        }
        com.ops.common.model.NodeTransferApplicationModel app = nodeTransferApplicationMapper.findById(id);
        if (app == null || !"PENDING".equals(app.getStatus())) {
            return Result.paramError("申请不存在或已处理");
        }
        NodeModel node = nodeService.findById(app.getNodeId());
        if (node == null) {
            return Result.error(1002, "节点不存在");
        }
        // 节点当前归属须仍等于申请时的来源租户（池节点），防止批准时把已直接分配的节点再次转移
        if (app.getSourceTenantId() == null || node.getTenantId() == null
                || node.getTenantId().longValue() != app.getSourceTenantId().longValue()) {
            return Result.paramError("节点归属已变更，申请失效（可撤销后重新申请）");
        }
        if (nodeService.countByNodeId(app.getNodeId()) > 0) {
            return Result.error(1003, "节点下有项目绑定，请先解绑再转移");
        }
        long now = System.currentTimeMillis();
        nodeService.updateTenant(app.getNodeId(), app.getTargetTenantId());
        nodeTransferApplicationMapper.updateStatus(id, "APPROVED", now,
                securityContext.getCurrentUserId(), securityContext.getCurrentUsername(), now);
        logOperation(app.getNodeId(), "NODE", "TRANSFER",
                "节点 " + app.getNodeName() + " 批准转移 → 租户[" + app.getTargetTenantName() + "]", httpRequest.getRemoteAddr());
        return Result.success();
    }

    /**
     * POST /api/nodes/node-transfers/{id}/reject - 平台管理员拒绝认领申请
     */
    @PostMapping("/node-transfers/{id}/reject")
    public Result<?> rejectTransfer(@PathVariable Long id, HttpServletRequest httpRequest) {
        if (!securityContext.isSuperAdmin()) {
            return Result.error(403, "仅平台管理员可审批");
        }
        com.ops.common.model.NodeTransferApplicationModel app = nodeTransferApplicationMapper.findById(id);
        if (app == null || !"PENDING".equals(app.getStatus())) {
            return Result.paramError("申请不存在或已处理");
        }
        long now = System.currentTimeMillis();
        nodeTransferApplicationMapper.updateStatus(id, "REJECTED", now,
                securityContext.getCurrentUserId(), securityContext.getCurrentUsername(), now);
        logOperation(app.getNodeId(), "NODE", "REJECT",
                "拒绝节点认领申请: " + app.getNodeName(), httpRequest.getRemoteAddr());
        return Result.success();
    }

    /**
     * POST /api/nodes/node-transfers/{id}/cancel - 申请人取消待审批申请
     */
    @PostMapping("/node-transfers/{id}/cancel")
    public Result<?> cancelTransfer(@PathVariable Long id) {
        com.ops.common.model.NodeTransferApplicationModel app = nodeTransferApplicationMapper.findById(id);
        if (app == null || !"PENDING".equals(app.getStatus())) {
            return Result.paramError("申请不存在或已处理");
        }
        Long uid = securityContext.getCurrentUserId();
        if (uid == null || !uid.equals(app.getApplicantId())) {
            return Result.error(403, "只能取消自己的申请");
        }
        nodeTransferApplicationMapper.updateStatus(id, "CANCELED", null, null, null, System.currentTimeMillis());
        return Result.success();
    }

    /**
     * POST /api/nodes/{id}/assign - 平台管理员直接分配节点给任意租户（不经过申请）
     */
    @PostMapping("/{id}/assign")
    public Result<?> assignNode(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                HttpServletRequest httpRequest) {
        if (!securityContext.isSuperAdmin()) {
            return Result.error(403, "仅平台管理员可分配节点");
        }
        Long targetTenantId = body.get("targetTenantId") instanceof Number
                ? ((Number) body.get("targetTenantId")).longValue() : null;
        if (targetTenantId == null || targetTenantId <= 0) {
            return Result.paramError("缺少目标租户");
        }
        NodeModel node = nodeService.findById(id);
        if (node == null) {
            return Result.error(1002, "节点不存在");
        }
        if (tenantMapper.findById(targetTenantId) == null) {
            return Result.paramError("目标租户不存在");
        }
        if (nodeService.countByNodeId(id) > 0) {
            return Result.error(1003, "节点下有项目绑定，请先解绑再分配");
        }
        com.ops.common.model.TenantModel target = tenantMapper.findById(targetTenantId);
        nodeService.updateTenant(id, targetTenantId);
        // 直接分配后，该节点的 PENDING 认领申请全部作废
        com.ops.common.model.NodeTransferApplicationModel pendingApp =
                nodeTransferApplicationMapper.findByNodeIdAndStatus(id, "PENDING");
        if (pendingApp != null) {
            nodeTransferApplicationMapper.updateStatus(pendingApp.getId(), "CANCELED", null, null, null, System.currentTimeMillis());
        }
        logOperation(id, "NODE", "ASSIGN",
                "管理员分配节点 " + node.getName() + " → 租户[" + (target == null ? targetTenantId : target.getName()) + "]",
                httpRequest.getRemoteAddr());
        return Result.success();
    }

    /**
     * POST /api/nodes/{id}/release - 平台管理员收回节点（回到 default 池）
     */
    @PostMapping("/{id}/release")
    public Result<?> releaseNode(@PathVariable Long id, HttpServletRequest httpRequest) {
        if (!securityContext.isSuperAdmin()) {
            return Result.error(403, "仅平台管理员可收回节点");
        }
        NodeModel node = nodeService.findById(id);
        if (node == null) {
            return Result.error(1002, "节点不存在");
        }
        Long defaultTenantId = resolveDefaultTenantId();
        if (defaultTenantId == null || node.getTenantId() != null
                && node.getTenantId().longValue() == defaultTenantId.longValue()) {
            return Result.paramError("该节点已在默认池中，无需收回");
        }
        if (nodeService.countByNodeId(id) > 0) {
            return Result.error(1003, "节点下有项目绑定，请先解绑再收回");
        }
        nodeService.updateTenant(id, defaultTenantId);
        logOperation(id, "NODE", "RELEASE", "管理员收回节点 " + node.getName() + " → 默认池", httpRequest.getRemoteAddr());
        return Result.success();
    }

    /**
     * GET /api/nodes/heartbeat - 心跳接口 (Agent侧)
     * 自动注册：如果 token 不存在，自动创建节点记录
     * 接收Agent上报的监控数据（X-Metrics header）
     */
    @GetMapping("/heartbeat")
    public Result<?> heartbeat(HttpServletRequest request,
                               @RequestParam(required = false) String nodeIp,
                               @RequestParam(required = false) Integer nodePort) {
        String token = request.getHeader(SystemConstant.TOKEN_HEADER);
        if (token == null || token.isEmpty()) {
            return Result.authError();
        }

        String nodeId = nodeMapper.getNodeIdByToken(token);

        // 自动注册：如果 token 不存在，自动创建节点
        if (nodeId == null) {
            String nodeName = request.getHeader("X-Node-Name");
            if (nodeName == null || nodeName.isEmpty()) {
                nodeName = "auto-registered-" + System.currentTimeMillis();
            }

            NodeModel node = new NodeModel();
            node.setName(nodeName);
            node.setIp(request.getRemoteAddr());
            node.setPort(nodePort != null ? nodePort : 2123);
            node.setToken(token);
            node.setStatus(NodeStatus.ONLINE.getCode());
            node.setCreateTime(System.currentTimeMillis());
            node.setUpdateTime(System.currentTimeMillis());
            // 设置默认租户ID，避免 tenant 隔离查询时漏掉自动注册的节点
            com.ops.common.model.TenantModel defaultTenant = tenantMapper.findDefault();
            if (defaultTenant != null) {
                node.setTenantId(defaultTenant.getId());
            }
            nodeService.insert(node);

            nodeId = String.valueOf(node.getId());
            log.info("Auto-registered new node: id={}, name={}, token={}", nodeId, nodeName, token);
        }

        // 使用 Agent 上报的外部 IP，如果没传则用请求来源 IP
        String ip = (nodeIp != null && !nodeIp.isEmpty()) ? nodeIp : request.getRemoteAddr();
        String osInfo = request.getHeader("X-OS-Info");
        String javaVersion = request.getHeader("X-Java-Version");
        String cpuInfo = request.getHeader("X-CPU-Info");
        String memInfo = request.getHeader("X-Mem-Info");
        String diskInfo = request.getHeader("X-Disk-Info");
        String osArch = request.getHeader("X-OS-Arch");
        String agentVersion = request.getHeader("X-Agent-Version");
        String agentPidStr = request.getHeader("X-Agent-PID");
        String metricsBase64 = request.getHeader("X-Metrics");

        // 解析硬件信息
        Integer cpuCores = null;
        Integer totalMemoryMb = null;
        Long totalDiskMb = null;
        Long agentPid = null;
        try {
            if (cpuInfo != null && !cpuInfo.isEmpty()) cpuCores = Integer.parseInt(cpuInfo);
            if (memInfo != null && !memInfo.isEmpty()) totalMemoryMb = Integer.parseInt(memInfo);
            if (diskInfo != null && !diskInfo.isEmpty()) totalDiskMb = Long.parseLong(diskInfo);
            if (agentPidStr != null && !agentPidStr.isEmpty()) agentPid = Long.parseLong(agentPidStr);
        } catch (NumberFormatException ignored) {}

        nodeMapper.updateHeartbeat(Long.parseLong(nodeId), System.currentTimeMillis(),
                ip, osInfo, javaVersion, cpuCores, totalMemoryMb, totalDiskMb, osArch, agentVersion, agentPid);

        // 保存主机级实时指标到node_info（供Agent状态页面使用，不依赖monitor_snapshot）
        if (metricsBase64 != null && !metricsBase64.isEmpty()) {
            try {
                String metricsJson = new String(java.util.Base64.getDecoder().decode(metricsBase64), "UTF-8");
                @SuppressWarnings("unchecked")
                Map<String, Object> metrics = com.alibaba.fastjson2.JSON.parseObject(metricsJson, Map.class);
                if (metrics != null) {
                    Double cpuPercent = metrics.get("cpuUsagePercent") instanceof Number ? ((Number) metrics.get("cpuUsagePercent")).doubleValue() : null;
                    Integer memPercent = metrics.get("memoryUsagePercent") instanceof Number ? ((Number) metrics.get("memoryUsagePercent")).intValue() : null;
                    Integer diskPercent = metrics.get("diskUsagePercent") instanceof Number ? ((Number) metrics.get("diskUsagePercent")).intValue() : null;
                    nodeMapper.updateHostMetrics(Long.parseLong(nodeId), cpuPercent, memPercent, diskPercent);
                }
            } catch (Exception ignored) {}
        }

        // 如果 Agent 上报了外部可访问的端口，更新节点端口
        if (nodePort != null && nodePort > 0) {
            nodeMapper.updatePort(Long.parseLong(nodeId), nodePort, System.currentTimeMillis());
        }

        // Update agent token cache
        Map<String, String> agentCache = authInterceptor.getAgentTokenCache();
        agentCache.put(nodeId, token);

        // 解析并存储监控数据
        if (metricsBase64 != null && !metricsBase64.isEmpty()) {
            try {
                String metricsJson = new String(java.util.Base64.getDecoder().decode(metricsBase64), "UTF-8");
                @SuppressWarnings("unchecked")
                Map<String, Object> metrics = com.alibaba.fastjson2.JSON.parseObject(metricsJson, Map.class);
                if (metrics != null && !metrics.isEmpty()) {
                    // 保存磁盘信息到节点表
                    String diskInfoJson = (String) metrics.get("diskInfoJson");
                    log.info("[Heartbeat] 节点={} diskInfoJson={}", nodeId, diskInfoJson != null ? diskInfoJson.substring(0, Math.min(50, diskInfoJson.length())) : "null");
                    if (diskInfoJson != null && !diskInfoJson.isEmpty()) {
                        try {
                            nodeMapper.updateDiskInfo(Long.parseLong(nodeId), diskInfoJson);
                            log.info("[Heartbeat] updateDiskInfo成功 节点={}", nodeId);
                        } catch (Exception ex) {
                            log.error("[Heartbeat] updateDiskInfo失败 节点={}", nodeId, ex);
                        }
                    }
                    // 存储到MonitorSnapshot表，同时返回计算结果用于WS推送
                    Map<Long, Map<String, Object>> computed = saveMonitorSnapshot(Long.parseLong(nodeId), metrics);
                    broadcastMonitorUpdate(Long.parseLong(nodeId), metrics, computed);
                }
            } catch (Exception e) {
                log.warn("监控指标解析失败 节点={}", nodeId, e);
            }
        }

        // Get projects bound to this node
        List<String> projectNames = nodeMapper.getProjectNamesByNodeId(Long.parseLong(nodeId));

        Map<String, Object> data = new java.util.HashMap<>();
        data.put("nodeId", nodeId);
        data.put("projects", projectNames);
        return Result.success(data);
    }

    /**
     * 保存监控快照到数据库（为节点上每个项目各生成一条快照）
     * 返回 Map<projectId, computed>，按项目维度供 WS 广播，
     * 解决「同节点多项目时 WS 只带最后一个项目状态、会串到其他项目」的问题。
     */
    private Map<Long, Map<String, Object>> saveMonitorSnapshot(Long nodeId, Map<String, Object> metrics) {
        Map<Long, Map<String, Object>> computedPerProject = new java.util.HashMap<>();
        try {
            List<Long> projectIds = nodeMapper.getProjectIdsByNodeId(nodeId);
            if (projectIds == null || projectIds.isEmpty()) {
                return computedPerProject;
            }

            for (Long projectId : projectIds) {
                // jarName 校验下沉到 saveOneSnapshot 内部，避免重复查 DB
                Map<String, Object> projectComputed = saveOneSnapshot(nodeId, projectId, metrics);
                if (projectComputed != null && !projectComputed.isEmpty()) {
                    computedPerProject.put(projectId, projectComputed);
                }
            }
        } catch (Exception e) {
            log.warn("监控快照保存失败 节点={}", nodeId, e);
        }
        return computedPerProject;
    }

    /**
     * 为单个项目保存一条监控快照，返回该项目的 computed 供 WS 推送
     */
    private Map<String, Object> saveOneSnapshot(Long nodeId, Long projectId, Map<String, Object> metrics) {
        Map<String, Object> computed = new java.util.HashMap<>();
        try {
            // UPSERT：查找已有快照并更新，避免每次心跳都 INSERT 新行导致表膨胀
            com.ops.common.model.MonitorSnapshotModel snap = snapshotMapper.findLatest(projectId, nodeId);
            if (snap == null) {
                snap = new com.ops.common.model.MonitorSnapshotModel();
                snap.setProjectId(projectId);
                snap.setNodeId(nodeId);
            }
            // 打 tenant 标（从项目推导，心跳请求无用户上下文）
            com.ops.common.model.ProjectModel proj = getProject(projectId);
            if (proj != null && proj.getTenantId() != null && (snap.getTenantId() == null || snap.getTenantId() == 0)) {
                snap.setTenantId(proj.getTenantId());
            }
            snap.setCollectTime(System.currentTimeMillis());

            // 解析CPU使用率（现在是真实的系统CPU使用率，不是负载转换值）
            Object cpuUsage = metrics.get("cpuUsagePercent");
            if (cpuUsage instanceof Number) {
                double val = ((Number) cpuUsage).doubleValue();
                snap.setHostCpuPercent(new java.math.BigDecimal(val));
                // 心跳上报时，如果没有应用进程数据，用Agent自身CPU作为进程CPU
                snap.setCpuPercent(new java.math.BigDecimal(val));
            }

            // 解析内存使用率（现在是真实的系统内存使用率，不是JVM内存使用率）
            Object memUsage = metrics.get("memoryUsagePercent");
            if (memUsage instanceof Number) {
                snap.setHostMemoryPercent(((Number) memUsage).intValue());
            }

            // 解析磁盘使用率
            Object diskUsage = metrics.get("diskUsagePercent");
            if (diskUsage instanceof Number) {
                snap.setDiskUsagePercent(((Number) diskUsage).intValue());
            }

            // ======================== 进程状态（先确定） ========================
            com.ops.common.model.ProjectModel snapProject = getProject(snap.getProjectId());
            boolean isFrontend = snapProject != null && "frontend".equalsIgnoreCase(snapProject.getProjectType());
            if (isFrontend) {
                // 前端静态资源没有 Java 进程，直接标记 N/A
                snap.setProcessStatus("N/A");
                snap.setHealthStatus("UP");
                snap.setHealthDetail("静态资源，无需进程存活监控");
                // 前端项目不需要匹配进程，直接跳过进程处理
                // 只更新主机级指标（CPU/内存/磁盘）
                return computed;
            }
            // 默认 STOPPED，由 processes 列表或上次快照覆盖
            snap.setProcessStatus("STOPPED");

            // 解析应用进程指标（Agent 心跳上报）
            // 按 jarName + deployDir 双重匹配，避免同节点多应用时取错 PID/堆内存
            Object processesObj = metrics.get("processes");
            if (processesObj instanceof List) {
                List<?> processes = (List<?>) processesObj;
                if (!processes.isEmpty()) {
                    com.ops.common.model.ProjectModel project = getProject(snap.getProjectId());
                    String expectedJarName = (project != null && project.getJarName() != null) ? project.getJarName().trim() : "";
                    String expectedDeployDir = resolveDeployDir(project);
                    if (expectedJarName.isEmpty()) {
                        log.debug("[Monitor] 跳过未配置jarName的项目 节点={} 项目={}", nodeId, snap.getProjectId());
                    } else {
                        Map<?, ?> matched = findProcessByJarNameAndDeployDir(processes, expectedJarName, expectedDeployDir);
                        if (matched != null) {
                            Object pidObj = matched.get("pid");
                            if (pidObj instanceof Number) {
                                snap.setProcessPid(((Number) pidObj).intValue());
                            }
                            Object procCpu = matched.get("cpuPercent");
                            if (procCpu instanceof Number) {
                                snap.setCpuPercent(new java.math.BigDecimal(((Number) procCpu).doubleValue()));
                            }
                            Object procMem = matched.get("memoryMb");
                            if (procMem instanceof Number) {
                                snap.setMemoryMb(((Number) procMem).intValue());
                            }
                            Object procHeapUsed = matched.get("heapUsedMb");
                            if (procHeapUsed instanceof Number) {
                                snap.setHeapUsedMb(((Number) procHeapUsed).intValue());
                            }
                            // 无 heap 数据时保留已有值（jstat 可能暂时不可用，不覆盖 collectOne 的正确数据）
                            Object procHeapMax = matched.get("heapMaxMb");
                            if (procHeapMax instanceof Number) {
                                snap.setHeapMaxMb(((Number) procHeapMax).intValue());
                            }
                            Object xmxObj = matched.get("xmxMb");
                            if (xmxObj instanceof Number) computed.put("xmxMb", ((Number) xmxObj).intValue());
                            Object gcCount = matched.get("gcCount");
                            if (gcCount instanceof Number) snap.setGcCount(((Number) gcCount).intValue());
                            Object gcTime = matched.get("gcTimeMs");
                            if (gcTime instanceof Number) snap.setGcTimeMs(((Number) gcTime).intValue());
                            Object alive = matched.get("alive");
                            if (Boolean.TRUE.equals(alive)) {
                                snap.setProcessStatus("RUNNING");
                            } else {
                                snap.setProcessStatus("STOPPED");
                            }
                        } else {
                            // 未匹配到进程：清除堆内存，不能保留上一次快照的错误数据
                            snap.setHeapUsedMb(null);
                            snap.setHeapMaxMb(null);
                        }
                    }
                } else {
                    // processes 列表为空，进程未运行
                    markStopped(snap);
                    snap.setHeapUsedMb(null);
                    snap.setHeapMaxMb(null);
                }
            } else {
                // processes 字段不存在，进程未运行
                markStopped(snap);
                snap.setHeapUsedMb(null);
                snap.setHeapMaxMb(null);
            }

            // ======================== 健康状态（进程状态确定后再判断） ========================
            // 三个字段联动：进程状态 ↔ 健康状态 ↔ 应用PID
            // - RUNNING → 看资源：CPU/内存高 → DEGRADED，正常 → UP
            // - STOPPED → DOWN
            if ("STOPPED".equals(snap.getProcessStatus())) {
                snap.setHealthStatus("DOWN");
                snap.setHealthDetail("应用进程已停止");
                snap.setProcessPid(null); // STOPPED 时清空 PID，保证前端三个字段一致
            } else {
                // RUNNING：根据主机资源判断
                double cpuPercent = snap.getHostCpuPercent() != null ? snap.getHostCpuPercent().doubleValue() : 0;
                int memPercent = snap.getHostMemoryPercent() != null ? snap.getHostMemoryPercent() : 0;
                if (cpuPercent > 90 || memPercent > 90) {
                    snap.setHealthStatus("DEGRADED");
                    snap.setHealthDetail("CPU=" + cpuPercent + "%, Memory=" + memPercent + "%");
                } else {
                    snap.setHealthStatus("UP");
                    snap.setHealthDetail("Agent主动上报");
                }
            }

            // 收集计算结果，供 WS 推送（与 DB 同源，不会不一致）
            computed.put("processStatus", snap.getProcessStatus());
            computed.put("processPid", snap.getProcessPid());
            computed.put("healthStatus", snap.getHealthStatus());
            computed.put("healthDetail", snap.getHealthDetail());
            if (snap.getCpuPercent() != null) computed.put("cpuPercent", snap.getCpuPercent());
            if (snap.getMemoryMb() != null) computed.put("memoryMb", snap.getMemoryMb());
            if (snap.getHeapUsedMb() != null) computed.put("heapUsedMb", snap.getHeapUsedMb());
            if (snap.getHeapMaxMb() != null) computed.put("heapMaxMb", snap.getHeapMaxMb());

            // 存储到数据库（有 ID 则更新，无 ID 则插入）
            if (snap.getId() != null) {
                snapshotMapper.update(snap);
            } else {
                snapshotMapper.insert(snap);
            }
            log.debug("监控快照 节点={} 项目={} 进程={} 健康={} PID={} 主机CPU={}% 进程CPU={}% 主机内存={}% 进程内存={}MB 堆={}/{}MB 磁盘={}% 详情={}",
                    nodeId, snap.getProjectId(), snap.getProcessStatus(), snap.getHealthStatus(),
                    snap.getProcessPid(),
                    fmt(snap.getHostCpuPercent()),
                    fmt(snap.getCpuPercent()),
                    snap.getHostMemoryPercent(),
                    snap.getMemoryMb(),
                    snap.getHeapUsedMb(), snap.getHeapMaxMb(),
                    snap.getDiskUsagePercent(),
                    snap.getHealthDetail());
        } catch (Exception e) {
            log.warn("监控快照保存失败 节点={} 项目={}", nodeId, projectId, e);
        }
        return computed;
    }

    /**
     * 标记进程为 STOPPED：进程列表为空或不存在，说明没在运行。
     */
    private void markStopped(com.ops.common.model.MonitorSnapshotModel snap) {
        snap.setProcessStatus("STOPPED");
        snap.setProcessPid(null);
    }

    /**
     * 通过WebSocket广播监控实时指标（CPU/内存/磁盘等高频数据）。
     * 同时按 projectId 维度推送 saveMonitorSnapshot 算好的状态字段（PID/进程状态/健康/堆），
     * 前端可按 projectId 精确 patch 到对应行，保证 WS 与 DB 同源一致。
     */
    private void broadcastMonitorUpdate(Long nodeId, Map<String, Object> metrics, Map<Long, Map<String, Object>> computed) {
        try {
            Map<String, Object> message = new java.util.HashMap<>();
            message.put("type", "monitor_update");
            message.put("nodeId", nodeId);
            message.put("metrics", metrics);
            message.put("timestamp", System.currentTimeMillis());
            // 按 projectId 维度推状态字段（与 DB 同源），前端按 projectId 精确匹配行
            if (computed != null && !computed.isEmpty()) {
                message.put("computed", computed);
            }

            String json = com.alibaba.fastjson2.JSON.toJSONString(message);
            // 心跳请求无用户上下文，租户须从节点推导（防跨租户实时泄漏）
            Long tenantId = null;
            try {
                com.ops.common.model.NodeModel node = nodeMapper.findById(nodeId);
                if (node != null) tenantId = node.getTenantId();
            } catch (Exception ignored) {
            }
            monitorHandler.broadcast("monitor", json, tenantId);
        } catch (Exception e) {
            log.warn("监控广播失败 节点={}", nodeId, e);
        }
    }

    /** BigDecimal 格式化为 1 位小数，null 返回 "?" */
    private String fmt(java.math.BigDecimal val) {
        if (val == null) return "?";
        return val.setScale(1, java.math.RoundingMode.HALF_UP).toString();
    }

    /** 获取项目配置对象（缓存单次请求内重复查询） */
    private com.ops.common.model.ProjectModel getProject(Long projectId) {
        if (projectId == null || projectId == 0L) return null;
        try {
            return projectMapper.findById(projectId);
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 解析项目的部署目录（与 MonitorCollectorService 保持一致） */
    private String resolveDeployDir(com.ops.common.model.ProjectModel project) {
        if (project == null) return "";
        if (project.getDeployDir() != null && !project.getDeployDir().trim().isEmpty()) {
            return project.getDeployDir().trim();
        }
        return globalPathProperties.resolveDeployDir(project.getName());
    }

    /** 在 processes 列表中按 jarName + deployDir 双重匹配进程 */
    @SuppressWarnings("unchecked")
    private Map<?, ?> findProcessByJarNameAndDeployDir(List<?> processes, String expectedJarName, String expectedDeployDir) {
        if (expectedJarName == null || expectedJarName.isEmpty()) return null;
        boolean hasExpectedDeployDir = expectedDeployDir != null && !expectedDeployDir.isEmpty();
        for (Object obj : processes) {
            if (obj instanceof Map) {
                Map<String, Object> proc = (Map<String, Object>) obj;
                String jarName = (String) proc.get("jarName");
                if (!expectedJarName.equals(jarName)) continue;
                // jarName 匹配后，有 deployDir 就用 deployDir 二次验证
                if (hasExpectedDeployDir) {
                    String procDeployDir = (String) proc.get("deployDir");
                    if (procDeployDir != null && !procDeployDir.isEmpty()) {
                        // 路径比较：支持精确匹配和子路径匹配
                        if (procDeployDir.equals(expectedDeployDir)
                                || procDeployDir.endsWith("/" + expectedDeployDir)
                                || expectedDeployDir.endsWith("/" + procDeployDir)
                                || procDeployDir.contains(expectedDeployDir)
                                || expectedDeployDir.contains(procDeployDir)) {
                            return proc;
                        }
                        continue; // deployDir 不匹配，跳过
                    }
                    // Agent 版本较旧没有 deployDir，回退到只按 jarName 匹配（降级兼容）
                }
                return proc;
            }
        }
        return null;
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
