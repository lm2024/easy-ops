package com.ops.server.websocket;

import com.ops.common.model.NodeModel;
import com.ops.server.mapper.NodeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 控制台与 Agent Shell 的 HTTP 桥接。
 */
@Component
public class ConsoleAgentClient {

    private static final Logger log = LoggerFactory.getLogger(ConsoleAgentClient.class);

    @Autowired
    private NodeMapper nodeMapper;

    @Autowired
    private RestTemplate restTemplate;

    /**
     * 解析 Agent 初始工作目录（在 Agent 容器/主机上执行 pwd，绝不使用 Server 本机路径）。
     */
    public String resolveCwd(String nodeId) {
        Map<String, Object> response = exec(nodeId, "cd ~ 2>/dev/null || cd /; pwd");
        String stdout = extractStdout(response);
        Integer exitCode = extractExitCode(response);
        if (stdout == null || stdout.trim().isEmpty() || (exitCode != null && exitCode != 0)) {
            throw new IllegalStateException("Agent 未返回有效工作目录");
        }
        String cwd = stdout.trim().split("\n")[0].trim();
        log.info("Console cwd resolved for node {} -> {}", nodeId, cwd);
        return cwd;
    }

    /**
     * 在 Agent 上执行命令。
     */
    public Map<String, Object> exec(String nodeId, String command) {
        NodeModel node = requireNode(nodeId);
        Map<String, String> request = new HashMap<>();
        request.put("command", command);
        @SuppressWarnings("unchecked")
        Map<String, Object> response = restTemplate.postForObject(shellUrl(node), request, Map.class);
        return response != null ? response : new HashMap<>();
    }

    /**
     * 请求 Tab 补全候选。
     */
    @SuppressWarnings("unchecked")
    public List<String> complete(String nodeId, String cwd, String line, int cursor) {
        NodeModel node = requireNode(nodeId);
        Map<String, String> request = new HashMap<>();
        request.put("cwd", cwd);
        request.put("line", line);
        request.put("cursor", String.valueOf(cursor));
        Map<String, Object> response = restTemplate.postForObject(completeUrl(node), request, Map.class);
        if (response == null) {
            return java.util.Collections.emptyList();
        }
        Object dataObj = response.get("data");
        if (dataObj instanceof Map) {
            Object candidates = ((Map<String, Object>) dataObj).get("candidates");
            if (candidates instanceof List) {
                return (List<String>) candidates;
            }
        }
        return java.util.Collections.emptyList();
    }

    public NodeModel findNode(String nodeId) {
        return nodeMapper.findById(Long.parseLong(nodeId));
    }

    public String extractStdout(Map<String, Object> response) {
        if (response == null) {
            return null;
        }
        Object stdout = unwrapData(response).get("stdout");
        return stdout != null ? stdout.toString() : null;
    }

    public Integer extractExitCode(Map<String, Object> response) {
        if (response == null) {
            return null;
        }
        Object code = unwrapData(response).get("exitCode");
        return code instanceof Number ? ((Number) code).intValue() : null;
    }

    public Long extractElapsed(Map<String, Object> response) {
        if (response == null) {
            return null;
        }
        Object elapsed = unwrapData(response).get("elapsed");
        return elapsed instanceof Number ? ((Number) elapsed).longValue() : null;
    }

    private NodeModel requireNode(String nodeId) {
        NodeModel node = findNode(nodeId);
        if (node == null) {
            throw new IllegalStateException("节点不存在 (ID: " + nodeId + ")");
        }
        return node;
    }

    private String shellUrl(NodeModel node) {
        return baseUrl(node) + "/shell/exec";
    }

    private String completeUrl(NodeModel node) {
        return baseUrl(node) + "/shell/complete";
    }

    private String baseUrl(NodeModel node) {
        String ip = node.getIp() != null ? node.getIp() : "127.0.0.1";
        int port = node.getPort() != null ? node.getPort() : 2123;
        return "http://" + ip + ":" + port + "/api";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrapData(Map<String, Object> response) {
        Object dataObj = response.get("data");
        if (dataObj instanceof Map) {
            return (Map<String, Object>) dataObj;
        }
        return response;
    }
}
