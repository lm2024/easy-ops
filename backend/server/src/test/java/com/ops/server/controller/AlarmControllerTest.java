package com.ops.server.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ops.common.model.AlarmModel;
import com.ops.server.mapper.AlarmRecordMapper;
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

class AlarmControllerTest extends BaseControllerTest {

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AlarmRecordMapper alarmRecordMapper;

    @Test
    void listAlarms() throws Exception {
        AlarmModel alarm = new AlarmModel();
        alarm.setId(1L);
        alarm.setType("OFFLINE");
        alarm.setContent("node offline");
        when(alarmRecordMapper.findByFilters(null, null, 1, 20)).thenReturn(Arrays.asList(alarm));
        when(alarmRecordMapper.countByFilters(null, null)).thenReturn(1L);

        mockMvc.perform(get("/alarms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void listAlarms_empty() throws Exception {
        when(alarmRecordMapper.findByFilters(null, null, 1, 20)).thenReturn(Collections.emptyList());
        when(alarmRecordMapper.countByFilters(null, null)).thenReturn(0L);

        mockMvc.perform(get("/alarms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void getAlarmConfig() throws Exception {
        mockMvc.perform(get("/alarms/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void saveAlarmConfig() throws Exception {
        Map<String, Object> config = new HashMap<>();
        config.put("smtpHost", "smtp.example.com");

        mockMvc.perform(put("/alarms/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(config)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void sendAlarm() throws Exception {
        when(alarmRecordMapper.insert(any(AlarmModel.class))).thenReturn(1);

        Map<String, String> body = new HashMap<>();
        body.put("nodeId", "1");
        body.put("content", "test alarm");
        body.put("type", "OFFLINE");

        mockMvc.perform(post("/alarms/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
