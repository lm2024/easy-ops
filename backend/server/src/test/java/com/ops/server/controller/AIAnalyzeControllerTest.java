package com.ops.server.controller;

import com.ops.common.model.NodeModel;
import com.ops.common.response.Result;
import com.ops.server.mapper.NodeMapper;
import com.ops.server.mapper.SysConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AIAnalyzeControllerTest {

    @Mock
    private NodeMapper nodeMapper;

    @Mock
    private SysConfigMapper sysConfigMapper;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private AIAnalyzeController controller;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "envApiKey", "env-test-key");
    }

    @Test
    @DisplayName("POST /ai/config - 保存配置不包含apiKey时成功")
    void saveConfig_noApiKey() {
        doNothing().when(sysConfigMapper).upsert(eq("ai.endpoint"), anyString(), anyString(), anyLong());
        doNothing().when(sysConfigMapper).upsert(eq("ai.model"), anyString(), anyString(), anyLong());

        Map<String, String> body = new HashMap<>();
        body.put("endpoint", "http://ai.internal:8080");
        body.put("model", "gpt-3.5");

        Object result = controller.saveConfig(body);
        assertNotNull(result);
    }

    @Test
    @DisplayName("POST /ai/config - 包含apiKey返回安全提示")
    void saveConfig_withApiKey_returnsSecurityError() {
        Map<String, String> body = new HashMap<>();
        body.put("apiKey", "sk-12345");

        Object result = controller.saveConfig(body);
        assertEquals(400, ((Result<?>) result).getCode());
    }

    @Test
    @DisplayName("analyzeLog - 缺少nodeId返回参数错误")
    void analyzeLog_missingNodeId() {
        Map<String, Object> body = new HashMap<>();
        body.put("logPath", "/var/log/app.log");

        Object result = controller.analyzeLog(body);
        assertEquals(400, ((Result<?>) result).getCode());
    }

    @Test
    @DisplayName("analyzeLog - 节点不存在返回错误")
    void analyzeLog_nodeNotFound() {
        Map<String, Object> body = new HashMap<>();
        body.put("nodeId", "999");
        body.put("logPath", "/var/log/app.log");

        when(nodeMapper.findById(999L)).thenReturn(null);

        Object result = controller.analyzeLog(body);
        assertEquals(1002, ((Result<?>) result).getCode());
    }

    @Test
    @DisplayName("analyzeLog - 正常分析流程")
    void analyzeLog_normalFlow() {
        Map<String, Object> body = new HashMap<>();
        body.put("nodeId", "1");
        body.put("logPath", "/var/log/app.log");
        body.put("lines", 100);

        NodeModel node = new NodeModel();
        node.setId(1L);
        node.setIp("127.0.0.1");
        node.setPort(2123);
        lenient().when(nodeMapper.findById(1L)).thenReturn(node);
        lenient().when(sysConfigMapper.getValue("ai.endpoint")).thenReturn("http://ai.internal:8080");
        lenient().when(sysConfigMapper.getValue("ai.model")).thenReturn("gpt-3.5");
        lenient().when(sysConfigMapper.getValue("ai.enabled")).thenReturn("true");
        lenient().when(sysConfigMapper.getValue("ai.apiKey")).thenReturn("fallback-key");
        lenient().when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(
            java.util.Collections.singletonMap("data", "log content here")
        );
        lenient().when(restTemplate.postForObject(anyString(), any(), eq(String.class))).thenReturn(
            "{\"choices\":[{\"message\":{\"content\":\"AI分析结果\"}}]}"
        );

        Object result = controller.analyzeLog(body);
        assertNotNull(result);
    }

    @Test
    @DisplayName("chat - 缺少prompt返回参数错误")
    void chat_missingPrompt() {
        Map<String, String> body = new HashMap<>();

        Object result = controller.chat(body);
        assertEquals(400, ((Result<?>) result).getCode());
    }

    @Test
    @DisplayName("chat - 正常请求")
    void chat_withPrompt() {
        Map<String, String> body = new HashMap<>();
        body.put("prompt", "how to fix OOM?");

        when(sysConfigMapper.getValue("ai.endpoint")).thenReturn("http://ai.internal:8080");
        when(sysConfigMapper.getValue("ai.model")).thenReturn("gpt-3.5");
        when(sysConfigMapper.getValue("ai.enabled")).thenReturn("true");
        when(restTemplate.postForObject(anyString(), any(), eq(String.class))).thenReturn(
            "{\"choices\":[{\"message\":{\"content\":\"回复内容\"}}]}"
        );

        Object result = controller.chat(body);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getConfig - 获取AI配置")
    void getConfig() {
        when(sysConfigMapper.getValue("ai.endpoint")).thenReturn("http://ai.internal:8080");
        when(sysConfigMapper.getValue("ai.model")).thenReturn("gpt-3.5");
        when(sysConfigMapper.getValue("ai.enabled")).thenReturn("true");

        Object result = controller.getConfig();
        assertNotNull(result);
    }
}
