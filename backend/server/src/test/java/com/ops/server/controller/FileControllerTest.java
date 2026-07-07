package com.ops.server.controller;

import com.ops.server.client.AgentClient;
import com.ops.server.mapper.FileAccessLogMapper;
import com.ops.server.mapper.NodeMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FileControllerTest extends BaseControllerTest {

    @MockBean
    private NodeMapper nodeMapper;

    @MockBean
    private FileAccessLogMapper fileAccessLogMapper;

    @MockBean
    private AgentClient agentClient;

    @Test
    void viewLog() throws Exception {
        com.ops.common.model.NodeModel node = new com.ops.common.model.NodeModel();
        node.setId(1L);
        node.setStatus(1);
        when(nodeMapper.findById(1L)).thenReturn(node);
        Map<String, Object> resp = new HashMap<>();
        resp.put("code", 200);
        resp.put("data", "log content");
        when(agentClient.getForMap(eq(node), eq("/file/log"), anyMap())).thenReturn(resp);
        when(agentClient.extractDataString(resp)).thenReturn("log content");

        mockMvc.perform(get("/files/log")
                        .param("nodeId", "1")
                        .param("logPath", "/var/log/app.log"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void viewLog_nodeNotFound() throws Exception {
        when(nodeMapper.findById(999L)).thenReturn(null);

        mockMvc.perform(get("/files/log")
                        .param("nodeId", "999")
                        .param("path", "/var/log/app.log"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1002));
    }

    @Test
    void viewConfig() throws Exception {
        com.ops.common.model.NodeModel node = new com.ops.common.model.NodeModel();
        node.setId(1L);
        node.setStatus(1);
        when(nodeMapper.findById(1L)).thenReturn(node);
        Map<String, Object> resp = new HashMap<>();
        resp.put("code", 200);
        resp.put("data", "config content");
        when(agentClient.getForMap(eq(node), eq("/file/config"), anyMap())).thenReturn(resp);
        when(agentClient.extractDataString(resp)).thenReturn("config content");

        mockMvc.perform(get("/files/config")
                        .param("nodeId", "1")
                        .param("configPath", "/opt/app/application.yml"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void saveConfig_nonYml() throws Exception {
        com.ops.common.model.NodeModel node = new com.ops.common.model.NodeModel();
        node.setId(1L);
        node.setStatus(1);
        when(nodeMapper.findById(1L)).thenReturn(node);

        Map<String, String> body = new java.util.HashMap<>();
        body.put("nodeId", "1");
        body.put("configPath", "/opt/app/app.properties");
        body.put("content", "key=value");

        mockMvc.perform(post("/files/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nodeId\":\"1\",\"configPath\":\"/opt/app/app.properties\",\"content\":\"key=value\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1007));
    }
}
