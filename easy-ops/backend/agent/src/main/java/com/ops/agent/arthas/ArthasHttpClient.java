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

    /**
     * HTTP 读超时在命令超时之上追加的缓冲时间。
     * Arthas 侧到点后会终止命令并回包，这段缓冲用于容纳序列化与网络传输，
     * 避免"命令其实成功了但 HTTP 层先超时"造成的假失败。
     */
    private static final int READ_TIMEOUT_BUFFER_MS = 5000;

    /** 单次请求读超时下限，防止调用方传入过小的超时导致正常命令被误判失败 */
    private static final int MIN_READ_TIMEOUT_MS = 10000;

    /** 命令超时上限：超过此值视为异常调用，避免 HTTP 连接被无限期占用 */
    public static final int MAX_EXEC_TIMEOUT_MS = 300000;

    /**
     * 同步执行命令。
     * HTTP 读超时按命令超时动态计算，确保长命令（profiler 采样等）不会被提前切断。
     */
    public ArthasResult exec(int port, String command, int timeoutMs) {
        Map<String, Object> req = new HashMap<>();
        req.put("action", "exec");
        req.put("command", command);
        int safeTimeout = normalizeTimeout(timeoutMs);
        req.put("execTimeout", safeTimeout);
        return doRequest(port, req, safeTimeout + READ_TIMEOUT_BUFFER_MS);
    }

    /**
     * 将命令超时规范到合理区间，避免非法值导致 HTTP 层行为异常
     */
    private static int normalizeTimeout(int timeoutMs) {
        if (timeoutMs <= 0) {
            return 30000;
        }
        return Math.min(timeoutMs, MAX_EXEC_TIMEOUT_MS);
    }

    /**
     * 创建会话
     */
    public String initSession(int port) {
        Map<String, Object> req = new HashMap<>();
        req.put("action", "init_session");
        ArthasResult result = doRequest(port, req, MIN_READ_TIMEOUT_MS);
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
        ArthasResult result = doRequest(port, req, MIN_READ_TIMEOUT_MS);
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
        ArthasResult result = doRequest(port, req, MIN_READ_TIMEOUT_MS);
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
        ArthasResult result = doRequest(port, req, MIN_READ_TIMEOUT_MS);
        return result.isSuccess();
    }

    /**
     * 执行 HTTP 请求
     */
    private ArthasResult doRequest(int port, Map<String, Object> requestBody, int readTimeoutMs) {
        String urlStr = "http://127.0.0.1:" + port + "/api";
        HttpURLConnection conn = null;
        boolean completed = false;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(Math.max(readTimeoutMs, MIN_READ_TIMEOUT_MS));
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
            // 流已完整读完，标记可复用连接
            completed = true;

            JSONObject jsonResponse = JSONUtil.parseObj(response.toString());
            ArthasResult result = new ArthasResult();
            result.setRawResponse(jsonResponse);
            String state = jsonResponse.getStr("state");
            result.setSuccess("SUCCEEDED".equals(state));
            result.setHttpCode(responseCode);
            if (!"SUCCEEDED".equals(state)) {
                log.warn("Arthas 命令返回非 SUCCEEDED 状态: state={}, command={}",
                        state, requestBody.get("command"));
            }

            // 提取命令结果
            if (jsonResponse.containsKey("body")) {
                JSONObject body = jsonResponse.getJSONObject("body");
                if (body != null && body.containsKey("results")) {
                    result.setResults(extractResults(body.getJSONArray("results")));
                }
            }
            return result;
        } catch (java.net.SocketTimeoutException e) {
            log.warn("Arthas 请求超时: url={}, command={}, readTimeout={}ms",
                    urlStr, requestBody.get("command"), readTimeoutMs);
            ArthasResult result = new ArthasResult();
            result.setSuccess(false);
            result.setErrorMsg("命令执行超时（" + readTimeoutMs / 1000 + "秒）：" + requestBody.get("command"));
            return result;
        } catch (Exception e) {
            log.error("Arthas HTTP 请求失败: url={}, command={}, error={}",
                    urlStr, requestBody.get("command"), e.getMessage());
            ArthasResult result = new ArthasResult();
            result.setSuccess(false);
            result.setErrorMsg(e.getMessage());
            return result;
        } finally {
            // 只有异常时才断开：正常读完流后不断开，让 JDK 的 keep-alive 缓存复用 TCP 连接，
            // 避免每条命令都重新三次握手（一键体检等场景要连发多条命令）。
            if (conn != null && !completed) {
                conn.disconnect();
            }
        }
    }

    /**
     * 从 Arthas 返回的 results 数组中提取有实际内容的结果。
     *
     * <p>Arthas 4.x 每条命令会在末尾附带一个 statusCode=0 的 status 哨兵，表示命令执行完毕，
     * 这类哨兵需要过滤。但有两种情况必须保留，否则功能会静默失效：
     * <ul>
     *   <li>statusCode != 0 的 status：它携带真实信息，例如 "thread -b" 无死锁时
     *       返回的就是 {type:status, statusCode:1, message:"No most blocking thread found!"}，
     *       这是该命令的唯一输出，过滤掉会导致死锁检测永远没有反馈。</li>
     *   <li>独立的 message 条目：承载错误或提示信息。</li>
     * </ul>
     */
    private List<Map<String, Object>> extractResults(JSONArray allResults) {
        List<Map<String, Object>> commandResults = new ArrayList<>();
        if (allResults == null) {
            return commandResults;
        }
        for (int i = 0; i < allResults.size(); i++) {
            JSONObject r = allResults.getJSONObject(i);
            if (r == null) {
                continue;
            }
            String type = r.getStr("type", "");
            if ("status".equals(type)) {
                // 仅丢弃"命令正常结束"的哨兵，保留携带信息的 status
                int statusCode = r.getInt("statusCode", 0);
                if (statusCode == 0) {
                    continue;
                }
            } else if ("input_status".equals(type) || "welcome".equals(type)) {
                continue;
            }
            commandResults.add(r.toBean(Map.class));
        }
        return commandResults;
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
