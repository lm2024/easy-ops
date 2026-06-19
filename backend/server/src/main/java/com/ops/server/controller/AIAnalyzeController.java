package com.ops.server.controller;

import com.ops.common.model.NodeModel;
import com.ops.common.response.Result;
import com.ops.server.mapper.NodeMapper;
import com.ops.server.mapper.SysConfigMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * AI 智能分析接口
 * 对接公司内网 OpenAI 兼容的大模型，提供日志分析、异常诊断能力
 */
@RestController
@RequestMapping("/ai")
public class AIAnalyzeController {

    @Autowired
    private NodeMapper nodeMapper;

    @Autowired
    private SysConfigMapper sysConfigMapper;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * GET /api/ai/config - 获取 AI 配置
     */
    @GetMapping("/config")
    public Result<?> getConfig() {
        Map<String, String> config = new HashMap<>();
        config.put("endpoint", sysConfigMapper.getValue("ai.endpoint"));
        config.put("model", sysConfigMapper.getValue("ai.model"));
        config.put("apiKey", maskApiKey(sysConfigMapper.getValue("ai.apiKey")));
        config.put("enabled", sysConfigMapper.getValue("ai.enabled"));
        return Result.success(config);
    }

    /**
     * POST /api/ai/config - 保存 AI 配置
     */
    @PostMapping("/config")
    public Result<?> saveConfig(@RequestBody Map<String, String> body) {
        if (body.containsKey("endpoint"))
            sysConfigMapper.upsert("ai.endpoint", body.get("endpoint"), "AI API Endpoint");
        if (body.containsKey("model"))
            sysConfigMapper.upsert("ai.model", body.get("model"), "AI Model Name");
        if (body.containsKey("apiKey"))
            sysConfigMapper.upsert("ai.apiKey", body.get("apiKey"), "AI API Key");
        if (body.containsKey("enabled"))
            sysConfigMapper.upsert("ai.enabled", body.get("enabled"), "AI Enabled");
        return Result.success();
    }

    /**
     * POST /api/ai/analyze-log - 使用 AI 分析日志
     * @param body nodeId, logPath, lines (最近行数), question (可选)
     */
    @PostMapping("/analyze-log")
    public Result<?> analyzeLog(@RequestBody Map<String, Object> body) {
        String nodeId = body.get("nodeId") != null ? body.get("nodeId").toString() : null;
        String logPath = body.get("logPath") != null ? body.get("logPath").toString() : null;
        int lines = body.containsKey("lines") ? Integer.parseInt(body.get("lines").toString()) : 200;
        String question = body.get("question") != null ? body.get("question").toString() : "分析以上日志，找出异常原因并给出修复建议";

        if (nodeId == null || logPath == null) {
            return Result.paramError("nodeId 和 logPath 不能为空");
        }

        // 1. 从节点读取日志
        NodeModel node = nodeMapper.findById(Long.parseLong(nodeId));
        if (node == null) return Result.error(1002, "节点不存在");

        String agentIp = node.getIp() != null ? node.getIp() : "127.0.0.1";
        int agentPort = node.getPort() != null ? node.getPort() : 2123;
        String agentUrl = "http://" + agentIp + ":" + agentPort + "/api/file/log?logPath=" + logPath + "&offset=0&lines=" + lines;

        String logContent;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> agentResp = restTemplate.getForObject(agentUrl, Map.class);
            if (agentResp != null && agentResp.get("data") != null) {
                logContent = agentResp.get("data").toString();
            } else {
                logContent = "无法读取日志文件: " + logPath;
            }
        } catch (Exception e) {
            logContent = "读取日志失败: " + e.getMessage();
        }

        // 2. 调用 AI 分析
        String analysis = callAI(question, logContent);

        Map<String, Object> result = new HashMap<>();
        result.put("logSnippet", logContent.length() > 3000 ? logContent.substring(0, 3000) + "\n...(截断)" : logContent);
        result.put("analysis", analysis);
        return Result.success(result);
    }

    /**
     * POST /api/ai/chat - 通用 AI 对话
     */
    @PostMapping("/chat")
    public Result<?> chat(@RequestBody Map<String, String> body) {
        String prompt = body.get("prompt");
        if (prompt == null || prompt.trim().isEmpty()) {
            return Result.paramError("prompt 不能为空");
        }
        String reply = callAI(prompt, null);
        Map<String, Object> result = new HashMap<>();
        result.put("reply", reply);
        return Result.success(result);
    }

    private String callAI(String question, String context) {
        String endpoint = sysConfigMapper.getValue("ai.endpoint");
        String model = sysConfigMapper.getValue("ai.model");
        String apiKey = sysConfigMapper.getValue("ai.apiKey");
        String enabled = sysConfigMapper.getValue("ai.enabled");

        if (!"true".equals(enabled) || endpoint == null || endpoint.isEmpty()) {
            return "⚠️ AI 服务未配置或未启用。请在「系统设置 > AI 配置」中填写内网 AI 接口地址。\n" +
                   "建议部署: vLLM / Ollama / Xinference 等兼容 OpenAI API 的推理服务。";
        }

        try {
            // 构造 OpenAI 兼容请求
            StringBuilder messages = new StringBuilder();
            messages.append("[{\"role\": \"system\", \"content\": \"你是一个专业的运维工程师，擅长分析日志、定位故障原因。" +
                    "请用中文简洁回答，指出问题根因、影响范围和修复建议。\"}");
            if (context != null && !context.isEmpty()) {
                messages.append(",{\"role\": \"user\", \"content\": \"以下是系统日志：\\n```\\n")
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

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model != null && !model.isEmpty() ? model : "gpt-3.5-turbo");
            requestBody.put("messages", messages.toString());
            requestBody.put("temperature", 0.3);
            requestBody.put("max_tokens", 4096);

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            if (apiKey != null && !apiKey.isEmpty()) {
                headers.set("Authorization", "Bearer " + apiKey);
            }

            org.springframework.http.HttpEntity<String> entity =
                    new org.springframework.http.HttpEntity<>(
                            new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(requestBody),
                            headers);

            String response = restTemplate.postForObject(endpoint + "/chat/completions", entity, String.class);

            // 解析响应
            @SuppressWarnings("unchecked")
            Map<String, Object> respMap = new com.fasterxml.jackson.databind.ObjectMapper().readValue(response, Map.class);
            if (respMap != null && respMap.containsKey("choices")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> choices = (List<Map<String, Object>>) respMap.get("choices");
                if (!choices.isEmpty()) {
                    Map<String, Object> choice = choices.get(0);
                    if (choice.containsKey("message")) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> msg = (Map<String, Object>) choice.get("message");
                        Object content = msg.get("content");
                        if (content != null) return content.toString();
                    }
                }
            }
            return "⚠️ AI 返回了异常响应，请检查模型接口配置。响应: " + (response != null ? response.substring(0, Math.min(200, response.length())) : "null");
        } catch (Exception e) {
            return "❌ AI 调用失败: " + e.getMessage() + "\n请检查: 1) AI 服务是否正常运行 2) 接口地址和端口是否正确 3) API Key 是否有效";
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String maskApiKey(String key) {
        if (key == null || key.length() < 8) return key;
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }
}
