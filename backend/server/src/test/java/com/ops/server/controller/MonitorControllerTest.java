package com.ops.server.controller;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MonitorControllerTest extends BaseControllerTest {

    @Test
    void getProcessMonitor() throws Exception {
        mockMvc.perform(get("/monitor/process")
                        .param("projectId", "1")
                        .param("nodeId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
