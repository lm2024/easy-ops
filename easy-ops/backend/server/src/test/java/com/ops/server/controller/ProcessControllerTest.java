package com.ops.server.controller;

import com.ops.common.model.ProjectModel;
import com.ops.server.mapper.ProjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProcessControllerTest extends BaseControllerTest {

    @MockBean
    private ProjectMapper projectMapper;

    private ProjectModel mockProject() {
        ProjectModel p = new ProjectModel();
        p.setId(1L);
        p.setName("test");
        p.setStartScript("java -jar app.jar");
        p.setStopScript("kill -9");
        p.setRestartScript("restart.sh");
        return p;
    }

    @Test
    void start_success() throws Exception {
        when(projectMapper.findById(1L)).thenReturn(mockProject());

        mockMvc.perform(post("/process/1/1/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void start_projectNotFound() throws Exception {
        when(projectMapper.findById(999L)).thenReturn(null);

        mockMvc.perform(post("/process/999/1/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1005));
    }

    @Test
    void stop_success() throws Exception {
        when(projectMapper.findById(1L)).thenReturn(mockProject());

        mockMvc.perform(post("/process/1/1/stop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void restart_success() throws Exception {
        when(projectMapper.findById(1L)).thenReturn(mockProject());

        mockMvc.perform(post("/process/1/1/restart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
