package com.ops.server.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ops.common.constant.ErrorCode;
import com.ops.common.exception.BusinessException;
import com.ops.common.model.NodeModel;
import com.ops.server.mapper.NodeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Agent HTTP 客户端：根据节点 IP/端口构建 URL 并发起请求
 */
@Component
public class AgentClient {

    private static final Logger log = LoggerFactory.getLogger(AgentClient.class);
    private static final ObjectMapper JSON_OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private NodeMapper nodeMapper;

    private final RestTemplate restTemplate;

    public AgentClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);  // 连接超时 5 秒
        factory.setReadTimeout(10000);    // 读取超时 10 秒
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * 按节点 ID 发起 GET 请求，返回 data 部分
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> get(Long nodeId, String path, Map<String, String> params) {
        NodeModel node = nodeMapper.findById(nodeId);
        if (node == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> data = extractDataMap(getForMap(node, path, params));
        return data != null ? data : Collections.emptyMap();
    }

    /**
     * GET 请求并返回完整响应 Map
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getForMap(NodeModel node, String path, Map<String, String> params) {
        String url = buildUrlWithQuery(node, path, params);
        try {
            return restTemplate.getForObject(url, Map.class);
        } catch (Exception e) {
            log.warn("Agent GET failed: {} - {}", url, e.getMessage());
            return null;
        }
    }

    /**
     * POST 请求并返回完整响应 Map
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> postForMap(NodeModel node, String path, Map<String, Object> body) {
        String url = buildUrl(node, path);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            return restTemplate.exchange(url, HttpMethod.POST, entity, Map.class).getBody();
        } catch (Exception e) {
            log.warn("Agent POST failed: {} - {}", url, e.getMessage());
            throw new RuntimeException("Agent POST failed: " + e.getMessage(), e);
        }
    }

    /**
     * GET 请求 Agent 接口，返回 data 部分
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> get(NodeModel node, String path) {
        return extractDataMap(getForMap(node, path, null));
    }

    /**
     * POST 请求 Agent 接口，body 为字符串 Map
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> post(NodeModel node, String path, Map<String, String> body) {
        Map<String, Object> objBody = new HashMap<>();
        if (body != null) {
            objBody.putAll(body);
        }
        Map<String, Object> response = postForMap(node, path, objBody);
        return extractDataMap(response);
    }

    /**
     * POST multipart 文件到 Agent（用于 Jar 升级等）。
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> postMultipart(NodeModel node, String path, File file, String sha256) {
        return postMultipart(node, path, file, sha256, null);
    }

    /**
     * POST multipart 文件到 Agent，支持附加参数。
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> postMultipart(NodeModel node, String path, File file, String sha256, String targetVersion) {
        String url = buildUrl(node, path);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new FileSystemResource(file));
            if (sha256 != null && !sha256.isEmpty()) {
                body.add("sha256", sha256);
            }
            if (targetVersion != null && !targetVersion.isEmpty()) {
                body.add("targetVersion", targetVersion);
            }
            HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
            return restTemplate.exchange(url, HttpMethod.POST, entity, Map.class).getBody();
        } catch (Exception e) {
            log.warn("Agent multipart POST failed: {} - {}", url, e.getMessage());
            throw new RuntimeException("Agent 文件上传失败: " + e.getMessage(), e);
        }
    }

    /**
     * 流式代理 Agent 文件内容到目标输出流（大文件下载专用）。
     * 使用 HttpURLConnection 且读超时为 0（无限），不受 RestTemplate 10s 读超时限制。
     * 客户端中途断开会抛 IOException，由调用方吞掉。
     */
    public void streamTo(NodeModel node, String path, Map<String, String> params, OutputStream target)
            throws IOException {
        String url = buildUrlWithQuery(node, path, params);
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(0);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "*/*");
        try {
            int code = conn.getResponseCode();
            if (code != 200) {
                throw new IOException("Agent 返回 HTTP " + code);
            }
            try (InputStream in = conn.getInputStream()) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) != -1) {
                    target.write(buf, 0, n);
                }
            }
        } finally {
            conn.disconnect();
        }
    }

    /**
     * 流式 POST multipart 文件到 Agent（文件管理上传，不落 Server 磁盘）。
     * 用 HttpURLConnection 手工拼 multipart，返回 Agent 的 Result data。
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> postMultipartStream(NodeModel node, String path,
                                                   String fieldName, String fileName,
                                                   InputStream in, long contentLength,
                                                   Map<String, String> params) throws IOException {
        String url = buildUrl(node, path);
        String boundary = "----EasyOpsBoundary" + Long.toHexString(System.nanoTime());
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(60000);
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        try {
            try (OutputStream os = conn.getOutputStream()) {
                if (params != null) {
                    for (Map.Entry<String, String> e : params.entrySet()) {
                        writeFormField(os, boundary, e.getKey(), e.getValue());
                    }
                }
                // 文件 part
                os.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
                os.write(("Content-Disposition: form-data; name=\"" + fieldName
                        + "\"; filename=\"" + fileName + "\"\r\n").getBytes(StandardCharsets.UTF_8));
                os.write(("Content-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) != -1) {
                    os.write(buf, 0, n);
                }
                os.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
            int code = conn.getResponseCode();
            InputStream respIn = code < 400 ? conn.getInputStream() : conn.getErrorStream();
            String body = respIn != null ? readAll(respIn) : "";
            Map<String, Object> resp = JSON_OBJECT_MAPPER.readValue(body, Map.class);
            if (code >= 400) {
                throw new IOException("Agent 返回 HTTP " + code + ": " + body);
            }
            return resp;
        } finally {
            conn.disconnect();
        }
    }

    private void writeFormField(OutputStream os, String boundary, String name, String value) throws IOException {
        os.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        os.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        os.write((value + "\r\n").getBytes(StandardCharsets.UTF_8));
    }

    private String readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            bos.write(buf, 0, n);
        }
        return bos.toString("UTF-8");
    }

    /**
     * GET 请求 Agent 版本信息。
     */
    public Map<String, Object> getAgentVersion(NodeModel node) {
        return extractDataMap(getForMap(node, "/system/version", null));
    }

    public Map<String, Object> getProcessStatus(NodeModel node, String deployDir, String jarName) {
        Map<String, String> params = new HashMap<>();
        params.put("deployDir", deployDir);
        params.put("jarName", jarName);
        return extractDataMap(getForMap(node, "/process/status", params));
    }

    /**
     * 重启应用进程
     */
    public Map<String, Object> restartProcess(NodeModel node, Long projectId, Map<String, String> body) {
        Map<String, Object> objBody = new HashMap<>();
        if (body != null) {
            objBody.putAll(body);
        }
        return extractDataMap(postForMap(node, "/process/" + projectId + "/restart", objBody));
    }

    /**
     * 读取节点日志尾部内容
     */
    public String readLog(Long nodeId, String logPath, int maxLines) {
        NodeModel node = nodeMapper.findById(nodeId);
        if (node == null) {
            return "";
        }
        Map<String, String> params = new HashMap<>();
        params.put("logPath", logPath);
        params.put("lines", String.valueOf(maxLines));
        return extractDataString(getForMap(node, "/file/log/tail", params));
    }

    /**
     * 获取 Agent 基础 URL（不含 /api 后缀后的 path）
     */
    public String getAgentBase(NodeModel node) {
        String ip = node.getIp() != null ? node.getIp() : "127.0.0.1";
        int port = node.getPort() != null ? node.getPort() : 2123;
        // IPv6 地址需加方括号，否则与端口拼接会解析失败
        if (ip.contains(":")) {
            return "http://[" + ip + "]:" + port + "/api";
        }
        return "http://" + ip + ":" + port + "/api";
    }

    /**
     * 构建 Agent 完整 URL
     */
    public String buildUrl(NodeModel node, String path) {
        return getAgentBase(node) + normalizePath(path);
    }

    /**
     * 校验 Agent 返回的 Result 包装（HTTP 200 但 code!=200 的情况）。
     */
    public void ensureAgentSuccess(Map<String, Object> response) {
        if (response == null) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "Agent 无响应");
        }
        Object codeObj = response.get("code");
        if (codeObj instanceof Number) {
            int code = ((Number) codeObj).intValue();
            if (code != 200) {
                Object message = response.get("message");
                throw new BusinessException(code, message != null ? message.toString() : "Agent 请求失败");
            }
        }
    }

    /**
     * 从响应中提取 data 字符串
     */
    public String extractDataString(Map<String, Object> response) {
        if (response == null) {
            return "";
        }
        Object rawData = response.get("data");
        if (rawData instanceof String) {
            return (String) rawData;
        }
        Map<String, Object> data = extractDataMap(response);
        if (data == null) {
            return "";
        }
        Object content = data.get("content");
        if (content != null) {
            return content.toString();
        }
        return data.toString();
    }

    /**
     * 从响应中提取 data Map
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> extractDataMap(Map<String, Object> response) {
        if (response == null) {
            return null;
        }
        Object data = response.get("data");
        if (data instanceof Map) {
            return (Map<String, Object>) data;
        }
        return response;
    }

    private String buildUrlWithQuery(NodeModel node, String path, Map<String, String> params) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(buildUrl(node, path));
        if (params != null) {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (entry.getValue() != null) {
                    builder.queryParam(entry.getKey(), entry.getValue());
                }
            }
        }
        return builder.build().encode().toUriString();
    }

    /**
     * DELETE 请求 Agent 接口
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> delete(NodeModel node, String path, Map<String, String> params) {
        String url = buildUrlWithQuery(node, path, params);
        try {
            return restTemplate.exchange(url, HttpMethod.DELETE, null, Map.class).getBody();
        } catch (Exception e) {
            log.warn("Agent DELETE failed: {} - {}", url, e.getMessage());
            return null;
        }
    }

    private String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    private String encode(String value) {
        if (value == null) {
            return "";
        }
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return value;
        }
    }
}
