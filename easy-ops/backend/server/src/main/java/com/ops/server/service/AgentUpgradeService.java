package com.ops.server.service;

import com.ops.common.enums.NodeStatus;
import com.ops.common.model.NodeModel;
import com.ops.server.client.AgentClient;
import com.ops.server.mapper.NodeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Server 端 Agent 批量自升级服务。
 */
@Service
public class AgentUpgradeService {

    private static final Logger log = LoggerFactory.getLogger(AgentUpgradeService.class);
    private static final String AGENT_JAR_NAME = "ops-platform-agent.jar";

    @Value("${server.path:./data}")
    private String dataPath;

    @Autowired
    private NodeMapper nodeMapper;

    @Autowired
    private AgentClient agentClient;

    /**
     * 上传并保存 Agent 升级包到 Server 本地。
     */
    public Map<String, Object> savePackage(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IOException("升级包不能为空");
        }
        File dir = new File(dataPath, "agent");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("无法创建 agent 目录");
        }
        File target = new File(dir, AGENT_JAR_NAME);
        String sha256 = writeWithSha256(file.getInputStream(), target);
        Map<String, Object> info = packageInfo();
        info.put("sha256", sha256);
        info.put("message", "Agent 升级包已保存");
        return info;
    }

    /**
     * 获取当前 Server 存储的 Agent 升级包信息。
     */
    public Map<String, Object> packageInfo() {
        File jar = getPackageFile();
        Map<String, Object> info = new HashMap<>();
        info.put("exists", jar.exists());
        info.put("path", jar.getAbsolutePath());
        if (jar.exists()) {
            info.put("size", jar.length());
            info.put("lastModified", jar.lastModified());
        }
        return info;
    }

    /**
     * 升级单个节点上的 Agent。
     */
    public Map<String, Object> upgradeNode(Long nodeId) {
        NodeModel node = nodeMapper.findById(nodeId);
        if (node == null) {
            throw new IllegalArgumentException("节点不存在");
        }
        return upgradeOne(node);
    }

    /**
     * 批量升级多个节点上的 Agent。
     */
    public Map<String, Object> upgradeBatch(List<Long> nodeIds) {
        File jar = getPackageFile();
        if (!jar.exists()) {
            throw new IllegalStateException("请先上传 Agent 升级包");
        }
        List<Map<String, Object>> results = new ArrayList<>();
        int success = 0;
        int failed = 0;
        if (nodeIds == null || nodeIds.isEmpty()) {
            List<NodeModel> online = nodeMapper.findByStatus(String.valueOf(NodeStatus.ONLINE.getCode()), 1, 10000, null);
            if (online != null) {
                for (NodeModel node : online) {
                    Map<String, Object> item = upgradeOneSafe(node);
                    results.add(item);
                    if (Boolean.TRUE.equals(item.get("success"))) {
                        success++;
                    } else {
                        failed++;
                    }
                }
            }
        } else {
            for (Long nodeId : nodeIds) {
                NodeModel node = nodeMapper.findById(nodeId);
                Map<String, Object> item = upgradeOneSafe(node);
                results.add(item);
                if (Boolean.TRUE.equals(item.get("success"))) {
                    success++;
                } else {
                    failed++;
                }
            }
        }
        Map<String, Object> summary = new HashMap<>();
        summary.put("success", success);
        summary.put("failed", failed);
        summary.put("total", results.size());
        summary.put("results", results);
        return summary;
    }

    private Map<String, Object> upgradeOneSafe(NodeModel node) {
        Map<String, Object> item = new HashMap<>();
        if (node == null) {
            item.put("success", false);
            item.put("message", "节点不存在");
            return item;
        }
        item.put("nodeId", node.getId());
        item.put("nodeName", node.getName());
        try {
            item.putAll(upgradeOne(node));
            item.put("success", true);
        } catch (Exception e) {
            log.warn("Agent upgrade failed for node {}: {}", node.getName(), e.getMessage());
            item.put("success", false);
            item.put("message", e.getMessage());
        }
        return item;
    }

    private Map<String, Object> upgradeOne(NodeModel node) {
        if (node.getStatus() == null || node.getStatus() != NodeStatus.ONLINE.getCode()) {
            throw new IllegalStateException("节点离线，无法升级");
        }
        File jar = getPackageFile();
        if (!jar.exists()) {
            throw new IllegalStateException("请先上传 Agent 升级包");
        }
        String sha256 = sha256Of(jar);
        Map<String, Object> response = agentClient.postMultipart(node, "/system/upgrade", jar, sha256);
        agentClient.ensureAgentSuccess(response);
        return agentClient.extractDataMap(response);
    }

    private File getPackageFile() {
        return new File(new File(dataPath, "agent"), AGENT_JAR_NAME);
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

    private String writeWithSha256(InputStream in, File dest) throws IOException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new IOException("SHA-256 不可用", e);
        }
        if (dest.exists() && !dest.delete()) {
            throw new IOException("无法覆盖旧升级包");
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

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
