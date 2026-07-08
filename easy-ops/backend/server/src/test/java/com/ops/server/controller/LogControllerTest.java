package com.ops.server.controller;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LogControllerTest extends BaseControllerTest {

    @Test
    void getLogFile() throws Exception {
        mockMvc.perform(get("/logs/file")
                        .param("nodeId", "1")
                        .param("path", "/var/log/app.log"))
                .andExpect(status().isOk());
    }

    @Test
    void getConsole() throws Exception {
        mockMvc.perform(get("/logs/console")
                        .param("projectId", "1")
                        .param("nodeId", "1"))
                .andExpect(status().isOk());
    }
}
