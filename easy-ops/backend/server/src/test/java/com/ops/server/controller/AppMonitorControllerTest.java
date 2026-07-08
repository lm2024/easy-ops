package com.ops.server.controller;

import com.ops.common.model.MonitorSnapshotModel;
import com.ops.common.model.ProjectModel;
import com.ops.server.mapper.MonitorSnapshotMapper;
import com.ops.server.mapper.ProjectMapper;
import com.ops.server.monitorapp.service.HealthProbeService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AppMonitorControllerTest extends BaseControllerTest {

    @MockBean
    private ProjectMapper projectMapper;
    @MockBean
    private MonitorSnapshotMapper snapshotMapper;
    @MockBean
    private HealthProbeService healthProbeService;

    @Test
    void overview() throws Exception {
        ProjectModel project = new ProjectModel();
        project.setId(1L);
        project.setName("test-app");
        when(projectMapper.findById(1L)).thenReturn(project);
        when(snapshotMapper.findLatestByProject(1L)).thenReturn(Collections.<MonitorSnapshotModel>emptyList());

        mockMvc.perform(get("/monitor/app/overview").param("projectId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void healthProbeGet() throws Exception {
        when(healthProbeService.getByProjectId(1L)).thenReturn(null);
        mockMvc.perform(get("/monitor/health-probe").param("projectId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
