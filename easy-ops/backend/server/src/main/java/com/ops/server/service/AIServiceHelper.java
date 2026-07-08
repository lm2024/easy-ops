package com.ops.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ops.server.mapper.SysConfigMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 调用共享服务，供 AIAnalyzeController 与 AIDiagnosisService 复用
 */
@Service
public class AIServiceHelper {

    @Autowired
    private SysConfigMapper sysConfigMapper;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${ai.apiKey:#{null}}")
    private String envApiKey;

    /**
     * 调用 OpenAI 兼容接口
     */
    public String callAI(String question, String context) {
        String endpoint = sysConfigMapper.getValue("ai.endpoint");
        String model = sysConfigMapper.getValue("ai.model");
        String enabled = sysConfigMapper.getValue("ai.enabled");

        String apiKey = envApiKey;
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = sysConfigMapper.getValue("ai.apiKey");
        }

        if (!"true".equals(enabled) || endpoint == null || endpoint.isEmpty()) {
            return "AI 服务未配置或未启用。请在系统设置中配置内网 AI 接口地址。";
        }

        try {
            StringBuilder messages = new StringBuilder();
            messages.append("[{\"role\": \"system\", \"content\": \"你是一个专业的运维工程师，擅长分析日志、定位故障原因。" +
                    "请用中文简洁回答，指出问题根因、影响范围和修复建议。\"}");
            if (context != null && !context.isEmpty()) {
                messages.append(",{\"role\": \"user\", \"content\": \"以下是诊断上下文：\\n```\\n")
                        .append(escapeJson(context))
                        .append("\\n```\\n\\n")
                        .append(escapeJson(question))
                        .append("\"}");
            } else {
                messages.append(",{\"role\": \"user\", \"content\": \"")
                        .append(escapeJson(question))
                        .append("\"}");
            }
            messages.append("]");

            Map<String, Object> requestBody = new HashMap<String, Object>();
            requestBody.put("model", model != null && !model.isEmpty() ? model : "gpt-3.5-turbo");
            requestBody.put("messages", messages.toString());
            requestBody.put("temperature", 0.3);
            requestBody.put("max_tokens", 4096);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (apiKey != null && !apiKey.isEmpty()) {
                headers.set("Authorization", "Bearer " + apiKey);
            }

            HttpEntity<String> entity = new HttpEntity<String>(
                    new ObjectMapper().writeValueAsString(requestBody), headers);
            String response = restTemplate.postForObject(endpoint + "/chat/completions", entity, String.class);

            @SuppressWarnings("unchecked")
            Map<String, Object> respMap = new ObjectMapper().readValue(response, Map.class);
            if (respMap != null && respMap.containsKey("choices")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> choices = (List<Map<String, Object>>) respMap.get("choices");
                if (!choices.isEmpty()) {
                    Map<String, Object> choice = choices.get(0);
                    if (choice.containsKey("message")) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> msg = (Map<String, Object>) choice.get("message");
                        Object content = msg.get("content");
                        if (content != null) {
                            return content.toString();
                        }
                    }
                }
            }
            return "AI 返回了异常响应，请检查模型接口配置。";
        } catch (Exception e) {
            return "AI 调用失败: " + e.getMessage();
        }
    }

    /**
     * 估算 token 用量（字符数 / 2）
     */
    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return text.length() / 2;
    }

    private String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
