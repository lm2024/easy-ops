package com.ops.server.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ops.common.model.ProjectModel;
import com.ops.server.mapper.ProjectMapper;
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

class ProjectControllerTest extends BaseControllerTest {

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProjectMapper projectMapper;

    private ProjectModel mockProject() {
        ProjectModel p = new ProjectModel();
        p.setId(1L);
        p.setName("test-project");
        p.setNodeIds("1,2");
        p.setStartScript("java -jar app.jar");
        p.setStopScript("kill -9");
        p.setJvmOpts("-Xmx512m");
        p.setStatus(1);
        return p;
    }

    @Test
    void listProjects() throws Exception {
        when(projectMapper.findByFilters(isNull(), isNull(), isNull(), eq(1), eq(20))).thenReturn(Arrays.asList(mockProject()));
        when(projectMapper.countByFilters(isNull(), isNull(), isNull())).thenReturn(1L);

        mockMvc.perform(get("/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void listProjects_empty() throws Exception {
        when(projectMapper.findByFilters(isNull(), isNull(), isNull(), eq(1), eq(20))).thenReturn(Collections.emptyList());
        when(projectMapper.countByFilters(isNull(), isNull(), isNull())).thenReturn(0L);

        mockMvc.perform(get("/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void getProject_found() throws Exception {
        when(projectMapper.findById(1L)).thenReturn(mockProject());

        mockMvc.perform(get("/projects/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void getProject_notFound() throws Exception {
        when(projectMapper.findById(999L)).thenReturn(null);

        mockMvc.perform(get("/projects/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1005));
    }

    @Test
    void createProject() throws Exception {
        when(projectMapper.findByName("new-project")).thenReturn(null);
        when(projectMapper.insert(any(ProjectModel.class))).thenReturn(1);

        ProjectModel p = new ProjectModel();
        p.setName("new-project");

        mockMvc.perform(post("/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(p)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void createProject_duplicateName() throws Exception {
        when(projectMapper.findByName("existing")).thenReturn(mockProject());

        ProjectModel p = new ProjectModel();
        p.setName("existing");

        mockMvc.perform(post("/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(p)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void updateProject() throws Exception {
        when(projectMapper.findById(1L)).thenReturn(mockProject());
        when(projectMapper.update(any(ProjectModel.class))).thenReturn(1);

        ProjectModel p = new ProjectModel();
        p.setName("updated");

        mockMvc.perform(put("/projects/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(p)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void deleteProject() throws Exception {
        when(projectMapper.deleteById(1L)).thenReturn(1);

        mockMvc.perform(delete("/projects/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
