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

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final int CONNECT_TIMEOUT_MS = 5000;

    /**
     * 读超时档位（毫秒）。
     *
     * <p>命令超时是变化的：dashboard 只要几秒，而 profiler 采样可能要几分钟。
     * 若用固定超时，短命令等太久、长命令必然被切断。
     * 这里预建几档 RestTemplate，按命令超时选最小可用档位，
     * 既避免每条命令重建连接工厂，也避免长命令被提前判死。
     */
    private static final int[] READ_TIMEOUT_LEVELS = {30000, 60000, 120000, 300000};

    /**
     * 命令超时之外追加的缓冲，覆盖 Agent 侧处理与网络传输耗时
     */
    private static final int READ_TIMEOUT_BUFFER_MS = 10000;

    private final RestTemplate[] restTemplates;

    /**
     * 节点信息缓存。节点 IP/端口变更极少，而 exec 每次都要用它拼 URL，
     * 逐条命令查一次库在高频诊断场景下是纯粹的浪费。
     */
    private final java.util.concurrent.ConcurrentHashMap<Long, NodeCacheEntry> nodeCache = new java.util.concurrent.ConcurrentHashMap<>();

    private static final long NODE_CACHE_TTL_MS = 60000;

    public ArthasAgentProxy() {
        this.restTemplates = new RestTemplate[READ_TIMEOUT_LEVELS.length];
        for (int i = 0; i < READ_TIMEOUT_LEVELS.length; i++) {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
            factory.setReadTimeout(READ_TIMEOUT_LEVELS[i]);
            this.restTemplates[i] = new RestTemplate(factory);
        }
    }

    /**
     * 按命令超时挑选合适的 RestTemplate
     */
    private RestTemplate pickRestTemplate(int timeoutMs) {
        int need = timeoutMs + READ_TIMEOUT_BUFFER_MS;
        for (int i = 0; i < READ_TIMEOUT_LEVELS.length; i++) {
            if (READ_TIMEOUT_LEVELS[i] >= need) {
                return restTemplates[i];
            }
        }
        return restTemplates[restTemplates.length - 1];
    }

    /**
     * 节点信息缓存条目
     */
    private static class NodeCacheEntry {
        final NodeModel node;
        final long expireAt;

        NodeCacheEntry(NodeModel node, long expireAt) {
            this.node = node;
            this.expireAt = expireAt;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expireAt;
        }
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
        // attach 要在 Agent 侧启动进程并等待端口就绪，按最长就绪时间预留
        return post(url, body, pickRestTemplate(ATTACH_RESERVE_MS));
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
        return post(url, body, pickRestTemplate(0));
    }

    /**
     * 执行命令。按命令自身的超时挑选读超时档位，
     * 保证 profiler 这类长命令不会被 Server 侧提前切断。
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> exec(Long nodeId, long pid, String command, int timeoutMs) {
        NodeModel node = getNode(nodeId);
        String url = buildUrl(node, "/arthas/exec");
        Map<String, Object> body = new HashMap<>();
        body.put("pid", pid);
        body.put("command", command);
        body.put("timeoutMs", timeoutMs);
        return post(url, body, pickRestTemplate(timeoutMs));
    }

    /** attach 预留给 Agent 侧启动与就绪检测的时间 */
    private static final int ATTACH_RESERVE_MS = 45000;

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
            Map<String, Object> resp = restTemplates[0].getForObject(url, Map.class);
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
            Map<String, Object> resp = restTemplates[0].getForObject(url, Map.class);
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

    /**
     * 执行 JVM 诊断命令（自动解析结果）
     * @param type 诊断类型：jmap-histo, thread-print, gc-stats
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> diagnose(Long nodeId, long pid, String type) {
        NodeModel node = getNode(nodeId);
        String url = buildUrl(node, "/arthas/diagnose");
        Map<String, Object> body = new HashMap<>();
        body.put("pid", pid);
        body.put("type", type);
        return post(url, body, pickRestTemplate(30000));
    }

    // ===== 私有方法 =====

    /**
     * 获取节点信息（带短时缓存）。
     * 节点 IP/端口几乎不变，但每次命令都要用它拼 URL，
     * 缓存后可以避免高频诊断下的重复数据库查询。
     */
    private NodeModel getNode(Long nodeId) {
        NodeCacheEntry cached = nodeCache.get(nodeId);
        if (cached != null && !cached.isExpired()) {
            return cached.node;
        }
        NodeModel node = nodeMapper.findById(nodeId);
        if (node == null) {
            throw new RuntimeException("节点不存在: nodeId=" + nodeId);
        }
        nodeCache.put(nodeId, new NodeCacheEntry(node, System.currentTimeMillis() + NODE_CACHE_TTL_MS));
        return node;
    }

    private String buildUrl(NodeModel node, String path) {
        return "http://" + node.getIp() + ":" + node.getPort() + "/api" + path;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String url, Map<String, Object> body, RestTemplate restTemplate) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(MAPPER.writeValueAsString(body), headers);
            Map<String, Object> resp = restTemplate.postForObject(url, entity, Map.class);
            return extractData(resp);
        } catch (Exception e) {
            log.error("Arthas Agent 请求失败: url={}, command={}, error={}", url, body.get("command"), e.getMessage());
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
