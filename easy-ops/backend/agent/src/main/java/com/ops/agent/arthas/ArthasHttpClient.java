package com.ops.agent.arthas;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Arthas HTTP API 客户端
 * 封装对 Arthas HTTP API (http://127.0.0.1:{port}/api) 的调用
 * 使用 hutool JSON 避免 Jackson 版本兼容问题
 */
@Component
public class ArthasHttpClient {
    private static final Logger log = LoggerFactory.getLogger(ArthasHttpClient.class);
    private static final int CONNECT_TIMEOUT = 5000;
    private static final int READ_TIMEOUT = 35000;

    /**
     * 同步执行命令
     */
    public ArthasResult exec(int port, String command, int timeoutMs) {
        Map<String, Object> req = new HashMap<>();
        req.put("action", "exec");
        req.put("command", command);
        req.put("execTimeout", timeoutMs);
        return doRequest(port, req);
    }

    /**
     * 创建会话
     */
    public String initSession(int port) {
        Map<String, Object> req = new HashMap<>();
        req.put("action", "init_session");
        ArthasResult result = doRequest(port, req);
        if (result.isSuccess() && result.getRawResponse() != null) {
            return result.getRawResponse().getStr("sessionId");
        }
        return null;
    }

    /**
     * 异步执行命令
     */
    public String asyncExec(int port, String sessionId, String command) {
        Map<String, Object> req = new HashMap<>();
        req.put("action", "async_exec");
        req.put("command", command);
        req.put("sessionId", sessionId);
        ArthasResult result = doRequest(port, req);
        if (result.isSuccess() && result.getRawResponse() != null) {
            JSONObject body = result.getRawResponse().getJSONObject("body");
            if (body != null) {
                return body.getStr("jobId");
            }
        }
        return null;
    }

    /**
     * 拉取异步结果
     */
    public List<Map<String, Object>> pullResults(int port, String sessionId, String consumerId) {
        Map<String, Object> req = new HashMap<>();
        req.put("action", "pull_results");
        req.put("sessionId", sessionId);
        req.put("consumerId", consumerId);
        ArthasResult result = doRequest(port, req);
        List<Map<String, Object>> results = new ArrayList<>();
        if (result.isSuccess() && result.getRawResponse() != null) {
            JSONObject body = result.getRawResponse().getJSONObject("body");
            if (body != null && body.containsKey("results")) {
                JSONArray arr = body.getJSONArray("results");
                for (int i = 0; i < arr.size(); i++) {
                    results.add(arr.getJSONObject(i).toBean(Map.class));
                }
            }
        }
        return results;
    }

    /**
     * 中断当前命令
     */
    public boolean interruptJob(int port, String sessionId) {
        Map<String, Object> req = new HashMap<>();
        req.put("action", "interrupt_job");
        req.put("sessionId", sessionId);
        ArthasResult result = doRequest(port, req);
        return result.isSuccess();
    }

    /**
     * 执行 HTTP 请求
     */
    private ArthasResult doRequest(int port, Map<String, Object> requestBody) {
        String urlStr = "http://127.0.0.1:" + port + "/api";
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setDoOutput(true);

            String jsonBody = JSONUtil.toJsonStr(requestBody);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(
                            responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream(),
                            StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }

            JSONObject jsonResponse = JSONUtil.parseObj(response.toString());
            ArthasResult result = new ArthasResult();
            result.setRawResponse(jsonResponse);
            String state = jsonResponse.getStr("state");
            result.setSuccess("SUCCEEDED".equals(state));
            result.setHttpCode(responseCode);
            if (!"SUCCEEDED".equals(state)) {
                log.warn("Arthas 命令返回非 SUCCEEDED 状态: state={}, response={}", state, response.toString());
            }

            // 提取命令结果（排除 type=status/input_status/message/welcome 的条目）
            if (jsonResponse.containsKey("body")) {
                JSONObject body = jsonResponse.getJSONObject("body");
                if (body != null && body.containsKey("results")) {
                    JSONArray allResults = body.getJSONArray("results");
                    List<Map<String, Object>> commandResults = new ArrayList<>();
                    for (int i = 0; i < allResults.size(); i++) {
                        JSONObject r = allResults.getJSONObject(i);
                        String type = r.getStr("type", "");
                        if (!"status".equals(type) && !"input_status".equals(type)
                                && !"message".equals(type) && !"welcome".equals(type)) {
                            commandResults.add(r.toBean(Map.class));
                        }
                    }
                    result.setResults(commandResults);
                }
            }
            return result;
        } catch (Exception e) {
            log.error("Arthas HTTP 请求失败: url={}, body={}, error={}", urlStr, requestBody, e.getMessage());
            ArthasResult result = new ArthasResult();
            result.setSuccess(false);
            result.setErrorMsg(e.getMessage());
            return result;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * Arthas 命令执行结果
     */
    public static class ArthasResult {
        private boolean success;
        private int httpCode;
        private List<Map<String, Object>> results;
        private JSONObject rawResponse;
        private String errorMsg;

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }

        public int getHttpCode() { return httpCode; }
        public void setHttpCode(int httpCode) { this.httpCode = httpCode; }

        public List<Map<String, Object>> getResults() { return results; }
        public void setResults(List<Map<String, Object>> results) { this.results = results; }

        public JSONObject getRawResponse() { return rawResponse; }
        public void setRawResponse(JSONObject rawResponse) { this.rawResponse = rawResponse; }

        public String getErrorMsg() { return errorMsg; }
        public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }
    }
}
