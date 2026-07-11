package com.ops.server.logmgmt.controller;

import com.ops.common.model.ProjectLogProfileModel;
import com.ops.server.logmgmt.service.LogMgmtService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LogMgmtControllerTest extends com.ops.server.controller.BaseControllerTest {

    @MockBean
    private LogMgmtService logMgmtService;

    @Test
    void getProfile() throws Exception {
        ProjectLogProfileModel profile = new ProjectLogProfileModel();
        profile.setProjectId(1L);
        profile.setLogDir("logs");
        profile.setMainLogFile("app.log");
        when(logMgmtService.getProfile(1L)).thenReturn(profile);

        mockMvc.perform(get("/logs/profile").param("projectId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.mainLogFile").value("app.log"));
    }

    @Test
    void listFiles() throws Exception {
        java.util.List<Map<String, Object>> files = Collections.emptyList();
        when(logMgmtService.listLogFiles(1L, 10L)).thenReturn(files);

        mockMvc.perform(get("/logs/files")
                        .param("projectId", "1")
                        .param("nodeId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void viewLog() throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("content", "log line");
        when(logMgmtService.viewLog(1L, 10L, null, 0, 200, null, "tail")).thenReturn(data);

        mockMvc.perform(get("/logs/view")
                        .param("projectId", "1")
                        .param("nodeId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("log line"));
    }

    @Test
    void search() throws Exception {
        Map<String, Object> result = new HashMap<>();
        result.put("totalHits", 1);
        when(logMgmtService.search(eq(1L), eq("ERROR"), anyString(), any(), anyInt(), anyInt(), any(), any()))
                .thenReturn(result);

        mockMvc.perform(post("/logs/search")
                        .contentType("application/json")
                        .content("{\"projectId\":1,\"keyword\":\"ERROR\",\"scope\":\"AGGREGATE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalHits").value(1));
    }
}
