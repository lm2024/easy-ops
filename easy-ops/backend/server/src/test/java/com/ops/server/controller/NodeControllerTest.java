package com.ops.server.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ops.common.model.NodeModel;
import com.ops.server.mapper.NodeMapper;
import com.ops.server.mapper.OperationLogMapper;
import com.ops.server.service.AlarmService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class NodeControllerTest extends BaseControllerTest {

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private NodeMapper nodeMapper;

    @MockBean
    private OperationLogMapper operationLogMapper;

    @MockBean
    private AlarmService alarmService;

    private NodeModel mockNode() {
        NodeModel node = new NodeModel();
        node.setId(1L);
        node.setName("node-1");
        node.setIp("192.168.1.1");
        node.setPort(2123);
        node.setToken("abc123");
        node.setStatus(1);
        node.setOsInfo("Linux");
        node.setJavaVersion("17");
        node.setLastHeartbeat(System.currentTimeMillis());
        return node;
    }

    @Test
    void listNodes() throws Exception {
        when(nodeMapper.findByStatus(null, 1, 20, null)).thenReturn(Arrays.asList(mockNode()));
        when(nodeMapper.countByStatus(null, null)).thenReturn(1L);

        mockMvc.perform(get("/nodes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void listNodes_empty() throws Exception {
        when(nodeMapper.findByStatus(null, 1, 20, null)).thenReturn(Collections.emptyList());
        when(nodeMapper.countByStatus(null, null)).thenReturn(0L);

        mockMvc.perform(get("/nodes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void getNode_found() throws Exception {
        when(nodeMapper.findById(1L)).thenReturn(mockNode());

        mockMvc.perform(get("/nodes/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void getNode_notFound() throws Exception {
        when(nodeMapper.findById(999L)).thenReturn(null);

        mockMvc.perform(get("/nodes/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1002));
    }

    @Test
    void addNode() throws Exception {
        when(nodeMapper.findByName("new-node")).thenReturn(null);
        when(nodeMapper.insert(any(NodeModel.class))).thenReturn(1);

        NodeModel node = new NodeModel();
        node.setName("new-node");
        node.setIp("10.0.0.1");
        node.setPort(2123);

        mockMvc.perform(post("/nodes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(node)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void addNode_duplicateName() throws Exception {
        when(nodeMapper.findByName("existing")).thenReturn(mockNode());

        NodeModel node = new NodeModel();
        node.setName("existing");

        mockMvc.perform(post("/nodes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(node)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void updateNode() throws Exception {
        when(nodeMapper.findById(1L)).thenReturn(mockNode());
        when(nodeMapper.update(any(NodeModel.class))).thenReturn(1);

        NodeModel node = new NodeModel();
        node.setName("updated");

        mockMvc.perform(put("/nodes/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(node)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void deleteNode_success() throws Exception {
        when(nodeMapper.countByNodeId(1L)).thenReturn(0);
        when(nodeMapper.deleteById(1L)).thenReturn(1);

        mockMvc.perform(delete("/nodes/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void deleteNode_hasProjects() throws Exception {
        when(nodeMapper.countByNodeId(1L)).thenReturn(2);

        mockMvc.perform(delete("/nodes/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1003));
    }
}
