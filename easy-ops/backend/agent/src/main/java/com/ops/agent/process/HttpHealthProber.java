package com.ops.agent.process;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * HTTP 健康探针：基于 JDK 8 HttpURLConnection，支持 GET/POST/HEAD。
 */
public class HttpHealthProber {

    private static final int SNIPPET_MAX = 200;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 执行 HTTP 健康探针。
     *
     * @param method         HTTP 方法（GET/POST/HEAD）
     * @param url            目标 URL
     * @param expectedStatus 期望 HTTP 状态码，0 表示不校验
     * @param timeoutMs      超时毫秒
     * @param body           POST 请求体，可为 null
     * @param headers        JSON 格式请求头，可为 null
     * @return status(UP/DOWN)、httpCode、responseMs、bodySnippet
     */
    public Map<String, Object> probe(String method, String url, int expectedStatus,
                                     int timeoutMs, String body, String headers) {
        Map<String, Object> result = new HashMap<String, Object>();
        long start = System.currentTimeMillis();
        HttpURLConnection conn = null;
        try {
            if (url == null || url.trim().isEmpty()) {
                return down(result, 0, 0L, "url 不能为空");
            }
            String httpMethod = normalizeMethod(method);
            conn = (HttpURLConnection) new URL(url.trim()).openConnection();
            conn.setRequestMethod(httpMethod);
            conn.setConnectTimeout(Math.max(timeoutMs, 1000));
            conn.setReadTimeout(Math.max(timeoutMs, 1000));
            applyHeaders(conn, headers);

            if ("POST".equals(httpMethod) && body != null) {
                conn.setDoOutput(true);
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                conn.setRequestProperty("Content-Length", String.valueOf(bytes.length));
                OutputStream out = conn.getOutputStream();
                out.write(bytes);
                out.flush();
                out.close();
            }

            int httpCode = conn.getResponseCode();
            long responseMs = System.currentTimeMillis() - start;
            String snippet = readBodySnippet(conn, httpMethod);

            boolean statusOk = expectedStatus <= 0 || httpCode == expectedStatus;
            result.put("httpCode", httpCode);
            result.put("responseMs", responseMs);
            result.put("bodySnippet", snippet);
            result.put("status", statusOk ? "UP" : "DOWN");
            if (!statusOk) {
                result.put("detail", "期望状态码 " + expectedStatus + "，实际 " + httpCode);
            }
            return result;
        } catch (Exception e) {
            long responseMs = System.currentTimeMillis() - start;
            return down(result, conn != null ? safeCode(conn) : 0, responseMs, e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private Map<String, Object> down(Map<String, Object> result, int httpCode,
                                     long responseMs, String detail) {
        result.put("status", "DOWN");
        result.put("httpCode", httpCode);
        result.put("responseMs", responseMs);
        result.put("bodySnippet", "");
        result.put("detail", detail);
        return result;
    }

    private String normalizeMethod(String method) {
        if (method == null || method.trim().isEmpty()) {
            return "GET";
        }
        String upper = method.trim().toUpperCase();
        if ("GET".equals(upper) || "POST".equals(upper) || "HEAD".equals(upper)) {
            return upper;
        }
        return "GET";
    }

    private void applyHeaders(HttpURLConnection conn, String headers) {
        if (headers == null || headers.trim().isEmpty()) {
            return;
        }
        try {
            Map<String, String> map = OBJECT_MAPPER.readValue(headers,
                    new TypeReference<Map<String, String>>() {});
            if (map != null) {
                for (Map.Entry<String, String> entry : map.entrySet()) {
                    conn.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }
        } catch (Exception ignored) {
            // 非 JSON 时忽略非法 headers
        }
    }

    private String readBodySnippet(HttpURLConnection conn, String method) {
        if ("HEAD".equals(method)) {
            return "";
        }
        InputStream stream;
        try {
            stream = conn.getInputStream();
        } catch (Exception e) {
            try {
                stream = conn.getErrorStream();
            } catch (Exception ex) {
                return "";
            }
        }
        if (stream == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null && sb.length() < SNIPPET_MAX) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(line);
            }
        } catch (Exception ignored) {
            return "";
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {
                    // ignore
                }
            }
        }
        String text = sb.toString();
        if (text.length() > SNIPPET_MAX) {
            return text.substring(0, SNIPPET_MAX);
        }
        return text;
    }

    private int safeCode(HttpURLConnection conn) {
        try {
            return conn.getResponseCode();
        } catch (Exception e) {
            return 0;
        }
    }
}
