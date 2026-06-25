package com.ops.server.controller;

import com.ops.common.model.AIDiagnosisRecordModel;
import com.ops.server.monitorapp.service.AIDiagnosisService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AIDiagnosisControllerTest extends BaseControllerTest {

    @MockBean
    private AIDiagnosisService diagnosisService;

    @Test
    void diagnose() throws Exception {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("id", 1L);
        result.put("severity", "INFO");
        result.put("diagnosis", "test diagnosis");
        when(diagnosisService.diagnose(eq(1L), isNull(), anyString(), any(), any()))
                .thenReturn(result);

        mockMvc.perform(post("/ai/diagnose")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":1,\"triggerType\":\"MANUAL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void getDiagnosis() throws Exception {
        AIDiagnosisRecordModel record = new AIDiagnosisRecordModel();
        record.setId(1L);
        record.setProjectId(1L);
        record.setDiagnosis("report");
        when(diagnosisService.getById(1L)).thenReturn(record);

        mockMvc.perform(get("/ai/diagnose/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
