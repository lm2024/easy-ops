package com.ops.server.configmgmt.controller;

import com.ops.common.model.ProjectConfigFileModel;
import com.ops.server.configmgmt.service.ConfigMgmtService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ConfigMgmtControllerTest extends com.ops.server.controller.BaseControllerTest {

    @MockBean
    private ConfigMgmtService configMgmtService;

    @Test
    void listFiles() throws Exception {
        ProjectConfigFileModel file = new ProjectConfigFileModel();
        file.setId(1L);
        file.setProjectId(1L);
        file.setFileName("application.yml");
        when(configMgmtService.listFiles(1L)).thenReturn(Collections.singletonList(file));

        mockMvc.perform(get("/config/files").param("projectId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].fileName").value("application.yml"));
    }

    @Test
    void getSnapshot() throws Exception {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("allSame", true);
        when(configMgmtService.getSnapshot(1L, 2L)).thenReturn(snapshot);

        mockMvc.perform(get("/config/snapshot")
                        .param("projectId", "1")
                        .param("configFileId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.allSame").value(true));
    }

    @Test
    void compare() throws Exception {
        Map<String, Object> result = new HashMap<>();
        result.put("baseNodeId", 10);
        when(configMgmtService.compare(eq(1L), eq(2L), eq(10L), anyList())).thenReturn(result);

        mockMvc.perform(post("/config/compare")
                        .contentType("application/json")
                        .content("{\"projectId\":1,\"configFileId\":2,\"baseNodeId\":10,\"targetNodeIds\":[11,12]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void refresh() throws Exception {
        Map<String, Object> result = new HashMap<>();
        result.put("refreshed", 3);
        when(configMgmtService.refreshSnapshots(1L, 2L)).thenReturn(result);

        mockMvc.perform(post("/config/refresh")
                        .contentType("application/json")
                        .content("{\"projectId\":1,\"configFileId\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refreshed").value(3));
    }
}
