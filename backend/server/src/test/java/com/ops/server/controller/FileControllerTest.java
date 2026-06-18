package com.ops.server.controller;

import com.ops.server.mapper.FileAccessLogMapper;
import com.ops.server.mapper.NodeMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FileControllerTest extends BaseControllerTest {

    @MockBean
    private NodeMapper nodeMapper;

    @MockBean
    private FileAccessLogMapper fileAccessLogMapper;

    @Test
    void viewLog() throws Exception {
        com.ops.common.model.NodeModel node = new com.ops.common.model.NodeModel();
        node.setId(1L);
        node.setStatus(1);
        when(nodeMapper.findById(1L)).thenReturn(node);

        mockMvc.perform(get("/files/log")
                        .param("nodeId", "1")
                        .param("path", "/var/log/app.log"))
                .andExpect(status().isOk());
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

        mockMvc.perform(get("/files/config")
                        .param("nodeId", "1")
                        .param("path", "/opt/app/application.yml"))
                .andExpect(status().isOk());
    }

    @Test
    void saveConfig_nonYml() throws Exception {
        com.ops.common.model.NodeModel node = new com.ops.common.model.NodeModel();
        node.setId(1L);
        node.setStatus(1);
        when(nodeMapper.findById(1L)).thenReturn(node);

        mockMvc.perform(post("/files/config")
                        .param("nodeId", "1")
                        .param("path", "/opt/app/app.properties")
                        .contentType("application/json")
                        .content("key=value"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1007));
    }
}
