package com.ops.server.arthas;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ops.common.model.NodeModel;
import com.ops.server.mapper.NodeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Arthas Agent 调用代理
 * 封装对 Agent 侧 /api/arthas/* 接口的调用
 */
@Component
public class ArthasAgentProxy {
    private static final Logger log = LoggerFactory.getLogger(ArthasAgentProxy.class);

    @Autowired
    private NodeMapper nodeMapper;

    private final RestTemplate restTemplate;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public ArthasAgentProxy() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(40000);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * attach 到目标 PID
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> attach(Long nodeId, long pid, Long projectId) {
        NodeModel node = getNode(nodeId);
        String url = buildUrl(node, "/arthas/attach");
        Map<String, Object> body = new HashMap<>();
        body.put("pid", pid);
        body.put("projectId", projectId);
        body.put("nodeId", nodeId);
        return post(url, body);
    }

    /**
     * detach
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> detach(Long nodeId, long pid) {
        NodeModel node = getNode(nodeId);
        String url = buildUrl(node, "/arthas/detach");
        Map<String, Object> body = new HashMap<>();
        body.put("pid", pid);
        return post(url, body);
    }

    /**
     * 执行命令
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> exec(Long nodeId, long pid, String command, int timeoutMs) {
        NodeModel node = getNode(nodeId);
        String url = buildUrl(node, "/arthas/exec");
        Map<String, Object> body = new HashMap<>();
        body.put("pid", pid);
        body.put("command", command);
        body.put("timeoutMs", timeoutMs);
        return post(url, body);
    }

    /**
     * 查询状态
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> status(Long nodeId, Long pid) {
        NodeModel node = getNode(nodeId);
        String url = buildUrl(node, "/arthas/status");
        if (pid != null) {
            url += "?pid=" + pid;
        }
        try {
            Map<String, Object> resp = restTemplate.getForObject(url, Map.class);
            return extractData(resp);
        } catch (Exception e) {
            log.error("Arthas status 失败: nodeId={}, error={}", nodeId, e.getMessage());
            return null;
        }
    }

    /**
     * 获取火焰图历史文件列表
     */
    @SuppressWarnings("unchecked")
    public java.util.List<Map<String, Object>> flamegraphList(Long nodeId, long pid) {
        NodeModel node = getNode(nodeId);
        String url = buildUrl(node, "/arthas/flamegraph-list?pid=" + pid);
        try {
            Map<String, Object> resp = restTemplate.getForObject(url, Map.class);
            if (resp == null) {
                return new java.util.ArrayList<>();
            }
            Object code = resp.get("code");
            if (code != null && !"0".equals(code.toString()) && !"200".equals(code.toString())
                    && !Integer.valueOf(0).equals(code) && !Integer.valueOf(200).equals(code)) {
                String msg = resp.get("message") != null ? resp.get("message").toString() : "未知错误";
                throw new RuntimeException("Agent 返回错误: " + msg);
            }
            Object data = resp.get("data");
            if (data instanceof java.util.List) {
                return (java.util.List<Map<String, Object>>) data;
            }
            return new java.util.ArrayList<>();
        } catch (Exception e) {
            log.error("获取火焰图历史列表失败: nodeId={}, pid={}, error={}", nodeId, pid, e.getMessage());
            throw new RuntimeException("获取火焰图历史列表失败: " + e.getMessage(), e);
        }
    }

    /**
     * 构建火焰图下载 URL
     */
    public String buildFlamegraphDownloadUrl(Long nodeId, long pid, String fileName) {
        NodeModel node = getNode(nodeId);
        return buildUrl(node, "/arthas/flamegraph/download?pid=" + pid + "&fileName=" + fileName);
    }

    // ===== 私有方法 =====

    private NodeModel getNode(Long nodeId) {
        NodeModel node = nodeMapper.findById(nodeId);
        if (node == null) {
            throw new RuntimeException("节点不存在: nodeId=" + nodeId);
        }
        return node;
    }

    private String buildUrl(NodeModel node, String path) {
        return "http://" + node.getIp() + ":" + node.getPort() + "/api" + path;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String url, Map<String, Object> body) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(MAPPER.writeValueAsString(body), headers);
            Map<String, Object> resp = restTemplate.postForObject(url, entity, Map.class);
            return extractData(resp);
        } catch (Exception e) {
            log.error("Arthas Agent 请求失败: url={}, body={}, error={}", url, body, e.getMessage());
            throw new RuntimeException("Agent 请求失败: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractData(Map<String, Object> resp) {
        if (resp == null) {
            return null;
        }
        Object code = resp.get("code");
        // Agent 的 Result 用 code=200 表示成功，也兼容 code=0
        if (code != null && !"0".equals(code.toString()) && !"200".equals(code.toString())
                && !Integer.valueOf(0).equals(code) && !Integer.valueOf(200).equals(code)) {
            String msg = resp.get("message") != null ? resp.get("message").toString() : "未知错误";
            throw new RuntimeException("Agent 返回错误: " + msg);
        }
        Object data = resp.get("data");
        if (data instanceof Map) {
            return (Map<String, Object>) data;
        }
        return new HashMap<>();
    }
}
