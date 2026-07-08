package com.ops.server.controller;

import com.ops.server.mapper.MonitorSnapshotMapper;
import com.ops.server.mapper.NodeMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MonitorControllerTest extends BaseControllerTest {

    @MockBean
    private MonitorSnapshotMapper snapshotMapper;
    @MockBean
    private NodeMapper nodeMapper;

    @Test
    void getProcessMonitor() throws Exception {
        when(snapshotMapper.findLatest(1L, 1L)).thenReturn(null);
        mockMvc.perform(get("/monitor/process")
                        .param("projectId", "1")
                        .param("nodeId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
