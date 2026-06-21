package com.ops.server.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ops.common.enums.DeployStatus;
import com.ops.common.model.DeployModel;
import com.ops.common.model.NodeModel;
import com.ops.common.model.ProjectModel;
import com.ops.common.model.VersionModel;
import com.ops.server.mapper.*;
import com.ops.server.service.AlarmService;
import com.ops.server.websocket.DeployHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DeployControllerTest extends BaseControllerTest {

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DeployRecordMapper deployRecordMapper;

    @MockBean
    private ProjectMapper projectMapper;

    @MockBean
    private NodeMapper nodeMapper;

    @MockBean
    private VersionPackageMapper versionPackageMapper;

    @MockBean
    private AlarmService alarmService;

    @MockBean
    private DeployHandler deployHandler;

    @Test
    void publish_success() throws Exception {
        ProjectModel project = new ProjectModel();
        project.setId(1L);
        project.setName("test");
        project.setNodeIds("1");
        when(projectMapper.findById(1L)).thenReturn(project);

        VersionModel version = new VersionModel();
        version.setId(1L);
        version.setJarName("app.jar");
        version.setFilePath("./data/versions/1/v1/app.jar");
        when(versionPackageMapper.findById(1L)).thenReturn(version);

        when(deployRecordMapper.insert(any(DeployModel.class))).thenReturn(1);

        Map<String, Long> body = new HashMap<>();
        body.put("projectId", 1L);
        body.put("versionId", 1L);

        mockMvc.perform(post("/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void publish_projectNotFound() throws Exception {
        when(projectMapper.findById(999L)).thenReturn(null);

        Map<String, Long> body = new HashMap<>();
        body.put("projectId", 999L);
        body.put("versionId", 1L);

        mockMvc.perform(post("/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1005));
    }

    @Test
    void publish_versionNotFound() throws Exception {
        ProjectModel project = new ProjectModel();
        project.setId(1L);
        project.setNodeIds("1");
        when(projectMapper.findById(1L)).thenReturn(project);
        when(versionPackageMapper.findById(999L)).thenReturn(null);

        Map<String, Long> body = new HashMap<>();
        body.put("projectId", 1L);
        body.put("versionId", 999L);

        mockMvc.perform(post("/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1004));
    }

    @Test
    void listRecords() throws Exception {
        DeployModel deploy = new DeployModel();
        deploy.setId(1L);
        deploy.setProjectId(1L);
        deploy.setStatus(DeployStatus.SUCCESS.getCode());
        when(deployRecordMapper.findByProjectId(1L, 1, 20)).thenReturn(Arrays.asList(deploy));
        when(deployRecordMapper.countByProjectId(1L)).thenReturn(1L);

        mockMvc.perform(get("/deploy").param("projectId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void listRecords_empty() throws Exception {
        when(deployRecordMapper.findByProjectId(1L, 1, 20)).thenReturn(Collections.emptyList());
        when(deployRecordMapper.countByProjectId(1L)).thenReturn(0L);

        mockMvc.perform(get("/deploy").param("projectId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void rollback_success() throws Exception {
        DeployModel original = new DeployModel();
        original.setId(1L);
        original.setProjectId(1L);
        original.setVersionId(1L);
        original.setNodeId(1L);
        when(deployRecordMapper.findById(1L)).thenReturn(original);

        VersionModel version = new VersionModel();
        version.setId(1L);
        version.setJarName("app.jar");
        when(versionPackageMapper.findById(1L)).thenReturn(version);

        ProjectModel project = new ProjectModel();
        project.setId(1L);
        when(projectMapper.findById(1L)).thenReturn(project);

        NodeModel node = new NodeModel();
        node.setId(1L);
        node.setIp("127.0.0.1");
        node.setPort(2123);
        when(nodeMapper.findById(1L)).thenReturn(node);

        when(deployRecordMapper.insert(any(DeployModel.class))).thenReturn(1);

        mockMvc.perform(post("/deploy/1/rollback"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void rollback_notFound() throws Exception {
        when(deployRecordMapper.findById(999L)).thenReturn(null);

        mockMvc.perform(post("/deploy/999/rollback"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }
}
