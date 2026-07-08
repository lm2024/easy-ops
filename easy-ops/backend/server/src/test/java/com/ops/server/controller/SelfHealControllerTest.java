package com.ops.server.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ops.common.model.SelfHealEventModel;
import com.ops.common.model.SelfHealPolicyModel;
import com.ops.server.mapper.SelfHealEventMapper;
import com.ops.server.selfheal.service.SelfHealPolicyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SelfHealControllerTest extends BaseControllerTest {

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SelfHealPolicyService policyService;

    @MockBean
    private SelfHealEventMapper eventMapper;

    @Test
    void listPolicies() throws Exception {
        SelfHealPolicyModel policy = new SelfHealPolicyModel();
        policy.setId(1L);
        policy.setProjectId(1L);
        policy.setEnabled(1);
        when(policyService.listAll()).thenReturn(Arrays.asList(policy));

        mockMvc.perform(get("/self-heal/policies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void getPolicy() throws Exception {
        SelfHealPolicyModel policy = new SelfHealPolicyModel();
        policy.setProjectId(1L);
        when(policyService.getByProjectId(1L)).thenReturn(policy);

        mockMvc.perform(get("/self-heal/policies/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void savePolicy() throws Exception {
        SelfHealPolicyModel policy = new SelfHealPolicyModel();
        policy.setProjectId(1L);
        policy.setEnabled(1);
        policy.setMaxRetries(3);
        when(policyService.save(any(SelfHealPolicyModel.class))).thenReturn(policy);

        mockMvc.perform(post("/self-heal/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(policy)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void resetCircuitBreaker() throws Exception {
        SelfHealPolicyModel policy = new SelfHealPolicyModel();
        policy.setProjectId(1L);
        policy.setCircuitBreaker(0);
        when(policyService.resetCircuitBreaker(1L)).thenReturn(policy);

        mockMvc.perform(post("/self-heal/policies/1/circuit-break"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void listEvents() throws Exception {
        SelfHealEventModel event = new SelfHealEventModel();
        event.setId(1L);
        event.setEventType("RETRY");
        when(eventMapper.findByFilters(1L, 1, 20)).thenReturn(Arrays.asList(event));
        when(eventMapper.countByFilters(1L)).thenReturn(1L);

        mockMvc.perform(get("/self-heal/events").param("projectId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void listEvents_empty() throws Exception {
        when(eventMapper.findByFilters(null, 1, 20)).thenReturn(Collections.emptyList());
        when(eventMapper.countByFilters(null)).thenReturn(0L);

        mockMvc.perform(get("/self-heal/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
