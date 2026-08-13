package com.ops.server.controller;

import com.ops.common.enums.NodeStatus;
import com.ops.common.model.AgentUpgradeRecordModel;
import com.ops.common.model.NodeModel;
import com.ops.common.response.Result;
import com.ops.server.client.AgentClient;
import com.ops.server.mapper.AgentUpgradeRecordMapper;
import com.ops.server.mapper.NodeMapper;
import com.ops.server.service.TenantResourceAccessService;
import com.ops.server.util.SecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 升级管理接口
 */
@RestController
@RequestMapping("/agent-upgrade")
public class AgentUpgradeController {

    private static final Logger log = LoggerFactory.getLogger(AgentUpgradeController.class);

    @Value("${server.path:./data}")
    private String dataPath;

    @Autowired
    private NodeMapper nodeMapper;

    @Autowired
    private AgentUpgradeRecordMapper upgradeRecordMapper;

    @Autowired
    private AgentClient agentClient;

    @Autowired
    private TenantResourceAccessService tenantResourceAccessService;

    @Autowired
    private SecurityContext securityContext;

    @Autowired
    private com.ops.server.mapper.TenantMapper tenantMapper;

    /** 解析默认（池）租户 id，兜底返回 null */
    private Long resolveDefaultTenantId() {
        try {
            com.ops.common.model.TenantModel def = tenantMapper.findDefault();
            return def == null ? null : def.getId();
        } catch (Exception e) {
            return null;
        }
    }

    /** 正在进行的升级批次 */
    private final ConcurrentHashMap<String, Boolean> activeBatches = new ConcurrentHashMap<>();

    /**
     * POST /api/agent-upgrade/upload - 上传 Agent 升级包
     * 自动用文件大小作为版本号，方便人机对比验证
     */
    @PostMapping("/upload")
    public Result<?> upload(@RequestParam("file") MultipartFile file,
                           @RequestParam(required = false) String version) {
        try {
            if (file == null || file.isEmpty()) {
                return Result.paramError("升级包不能为空");
            }
            String originalName = file.getOriginalFilename();
            if (originalName == null || !originalName.toLowerCase().endsWith(".jar")) {
                return Result.paramError("仅支持 .jar 格式的 Agent 升级包");
            }

            File agentDir = new File(dataPath, "agent-packages");
            if (!agentDir.exists() && !agentDir.mkdirs()) {
                return Result.error(500, "无法创建存储目录");
            }

            // 先保存文件到临时位置获取大小
            File tempFile = new File(agentDir, "temp-upload.jar");
            String sha256 = writeWithSha256(file.getInputStream(), tempFile);
            long fileSize = tempFile.length();

            // 用文件大小作为版本号（用户可自定义覆盖）
            if (version == null || version.trim().isEmpty()) {
                version = String.valueOf(fileSize);
            }

            // 重命名为 {version}.jar
            File target = new File(agentDir, version + ".jar");
            if (target.exists()) {
                target.delete();
            }
            tempFile.renameTo(target);

            Map<String, Object> info = new LinkedHashMap<>();
            info.put("version", version);
            info.put("fileName", originalName);
            info.put("size", fileSize);
            info.put("sha256", sha256);
            info.put("path", target.getAbsolutePath());
            log.info("[AgentUpgrade] 上传升级包: version={}, size={}, file={}", version, fileSize, originalName);
            return Result.success(info);
        } catch (IOException e) {
            log.error("[AgentUpgrade] 上传失败", e);
            return Result.error(500, "上传失败: " + e.getMessage());
        }
    }

    /**
     * GET /api/agent-upgrade/packages - 获取已上传的 Agent 包列表
     */
    @GetMapping("/packages")
    public Result<?> listPackages() {
        File agentDir = new File(dataPath, "agent-packages");
        List<Map<String, Object>> packages = new ArrayList<>();
        if (agentDir.exists()) {
            File[] files = agentDir.listFiles((dir, name) -> name.endsWith(".jar"));
            if (files != null) {
                Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                for (File f : files) {
                    Map<String, Object> info = new LinkedHashMap<>();
                    String name = f.getName();
                    info.put("version", name.replace(".jar", ""));
                    info.put("fileName", name);
                    info.put("size", f.length());
                    info.put("lastModified", f.lastModified());
                    packages.add(info);
                }
            }
        }
        return Result.success(packages);
    }

    /**
     * DELETE /api/agent-upgrade/packages/{version} - 删除指定版本的升级包
     */
    @DeleteMapping("/packages/{version}")
    public Result<?> deletePackage(@PathVariable String version) {
        File agentDir = new File(dataPath, "agent-packages");
        File target = new File(agentDir, version + ".jar");
        if (!target.exists()) {
            return Result.error(1004, "升级包不存在: " + version);
        }
        if (target.delete()) {
            log.info("[AgentUpgrade] 删除升级包: version={}", version);
            return Result.success("已删除: " + version);
        }
        return Result.error(500, "删除失败: " + version);
    }

    /**
     * GET /api/agent-upgrade/nodes - 获取节点列表（含版本和升级状态）
     */
    @GetMapping("/nodes")
    public Result<?> listNodes() {
        Long tenantId = securityContext.getCurrentTenantId();
        List<NodeModel> allNodes = tenantId == null
                ? nodeMapper.findByStatus(null, 1, 10000, null, null, null)
                : nodeMapper.findByStatusInTenant(null, 1, 10000, null, null, null, tenantId,
                        resolveDefaultTenantId(), securityContext.getAccessibleProjectIdsForQuery());
        List<Map<String, Object>> nodeList = new ArrayList<>();
        if (allNodes != null) {
            for (NodeModel node : allNodes) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", node.getId());
                item.put("name", node.getName());
                item.put("ip", node.getIp());
                item.put("port", node.getPort());
                item.put("status", node.getStatus());
                item.put("agentVersion", node.getAgentVersion());
                item.put("lastHeartbeat", node.getLastHeartbeat());
                nodeList.add(item);
            }
        }
        return Result.success(nodeList);
    }

    /**
     * POST /api/agent-upgrade/upgrade - 执行升级（灰度/批量）
     * body: { "version": "2.0.0", "nodeIds": [1,2,3] }
     */
    @PostMapping("/upgrade")
    public Result<?> upgrade(@RequestBody Map<String, Object> request) {
        String version = (String) request.get("version");
        @SuppressWarnings("unchecked")
        List<Number> nodeIdsRaw = (List<Number>) request.get("nodeIds");

        if (version == null || version.trim().isEmpty()) {
            return Result.paramError("请指定目标版本");
        }
        if (nodeIdsRaw != null) {
            for (Number raw : nodeIdsRaw) {
                tenantResourceAccessService.requireNode(raw.longValue());
            }
        }
        if (nodeIdsRaw == null || nodeIdsRaw.isEmpty()) {
            return Result.paramError("请选择要升级的节点");
        }

        File agentJar = new File(new File(dataPath, "agent-packages"), version + ".jar");
        if (!agentJar.exists()) {
            return Result.error(1004, "升级包不存在: " + version);
        }

        String sha256 = sha256Of(agentJar);
        String batchId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        List<Long> nodeIds = new ArrayList<>();
        for (Number n : nodeIdsRaw) {
            nodeIds.add(n.longValue());
        }
        Long tenantId = securityContext.getCurrentTenantId();

        log.info("[AgentUpgrade] 开始升级: batchId={}, version={}, nodes={}", batchId, version, nodeIds.size());
        activeBatches.put(batchId, true);

        // 异步执行升级
        new Thread(() -> {
            try {
                doUpgradeAsync(batchId, version, nodeIds, agentJar, sha256, tenantId);
            } catch (Exception e) {
                log.error("[AgentUpgrade] 升级异常: batchId={}", batchId, e);
            } finally {
                activeBatches.remove(batchId);
            }
        }, "agent-upgrade-" + batchId).start();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("batchId", batchId);
        data.put("version", version);
        data.put("nodeCount", nodeIds.size());
        data.put("status", "PROCESSING");
        return Result.success(data);
    }

    /**
     * GET /api/agent-upgrade/status/{batchId} - 查询升级状态
     */
    @GetMapping("/status/{batchId}")
    public Result<?> getStatus(@PathVariable String batchId) {
        List<AgentUpgradeRecordModel> records = upgradeRecordMapper.findByBatchId(batchId, securityContext.getCurrentTenantId());
        if (records == null || records.isEmpty()) {
            return Result.error(1004, "未找到升级记录");
        }

        int total = records.size();
        long success = records.stream().filter(r -> r.getStatus() == 2).count();
        long failed = records.stream().filter(r -> r.getStatus() == 3).count();
        long processing = records.stream().filter(r -> r.getStatus() == 1).count();
        long pending = records.stream().filter(r -> r.getStatus() == 0).count();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("batchId", batchId);
        data.put("targetVersion", records.get(0).getTargetVersion());
        data.put("total", total);
        data.put("success", success);
        data.put("failed", failed);
        data.put("processing", processing);
        data.put("pending", pending);
        data.put("completed", success + failed);
        data.put("status", processing > 0 ? "PROCESSING" : (failed > 0 ? "PARTIAL_FAILED" : "COMPLETED"));
        data.put("details", records);
        return Result.success(data);
    }

    /**
     * GET /api/agent-upgrade/records - 查询升级历史
     */
    @GetMapping("/records")
    public Result<?> listRecords(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "50") Integer pageSize) {
        Long tenantId = securityContext.getCurrentTenantId();
        List<AgentUpgradeRecordModel> records = upgradeRecordMapper.findAll(page, pageSize, tenantId);
        Long total = upgradeRecordMapper.countAll(tenantId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", records != null ? records : Collections.emptyList());
        data.put("total", total);
        return Result.success(data);
    }

    // ======================== 内部方法 ========================

    private void doUpgradeAsync(String batchId, String version, List<Long> nodeIds,
                                File agentJar, String sha256, Long tenantId) {
        for (Long nodeId : nodeIds) {
            NodeModel node = nodeMapper.findById(nodeId);
            if (node == null) {
                continue;
            }

            AgentUpgradeRecordModel record = new AgentUpgradeRecordModel();
            record.setUpgradeBatchId(batchId);
            record.setTenantId(tenantId);
            record.setTargetVersion(version);
            record.setNodeId(nodeId);
            record.setNodeName(node.getName());
            record.setOldVersion(node.getAgentVersion());
            record.setStatus(1); // 升级中
            record.setStartTime(System.currentTimeMillis());
            record.setCreateTime(System.currentTimeMillis());
            upgradeRecordMapper.insert(record);

            try {
                log.info("[AgentUpgrade] 升级节点: nodeId={}, node={}, version={}", nodeId, node.getName(), version);
                Map<String, Object> response = agentClient.postMultipart(node, "/system/upgrade", agentJar, sha256, version);
                agentClient.ensureAgentSuccess(response);
                upgradeRecordMapper.updateStatus(record.getId(), 2, null, System.currentTimeMillis(), tenantId);
                log.info("[AgentUpgrade] 节点升级成功: nodeId={}", nodeId);
            } catch (Exception e) {
                log.warn("[AgentUpgrade] 节点升级失败: nodeId={}, error={}", nodeId, e.getMessage());
                upgradeRecordMapper.updateStatus(record.getId(), 3, e.getMessage(), System.currentTimeMillis(), tenantId);
            }
        }
    }

    private String extractVersionFromFilename(String filename) {
        // easy-ops-agent-2.0.0.jar -> 2.0.0
        if (filename == null) return null;
        String name = filename.replace(".jar", "");
        // 尝试匹配 x.y.z 格式
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+\\.\\d+\\.\\d+[\\w.-]*)").matcher(name);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private String writeWithSha256(InputStream in, File dest) throws IOException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new IOException("SHA-256 不可用", e);
        }
        if (dest.exists() && !dest.delete()) {
            throw new IOException("无法覆盖旧文件");
        }
        try (OutputStream out = Files.newOutputStream(dest.toPath())) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
                md.update(buffer, 0, len);
            }
        }
        return bytesToHex(md.digest());
    }

    private String sha256Of(File file) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file.toPath())) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    md.update(buffer, 0, len);
                }
            }
            return bytesToHex(md.digest());
        } catch (Exception e) {
            return "";
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
