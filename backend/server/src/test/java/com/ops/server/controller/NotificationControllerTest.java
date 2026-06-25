package com.ops.server.controller;

import com.ops.common.model.NotificationRecordModel;
import com.ops.server.selfheal.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class NotificationControllerTest extends BaseControllerTest {

    @MockBean
    private NotificationService notificationService;

    @BeforeEach
    void setupUser() {
        when(securityContext.getCurrentUserId()).thenReturn(1L);
    }

    @Test
    void listNotifications() throws Exception {
        Map<String, Object> data = new HashMap<>();
        NotificationRecordModel record = new NotificationRecordModel();
        record.setId(1L);
        record.setType("ALERT");
        data.put("list", Arrays.asList(record));
        data.put("total", 1L);
        when(notificationService.listForUser(1L, 1, 20)).thenReturn(data);

        mockMvc.perform(get("/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void unreadCount() throws Exception {
        when(notificationService.unreadCount(1L)).thenReturn(3L);

        mockMvc.perform(get("/notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.count").value(3));
    }

    @Test
    void unackedAlerts() throws Exception {
        NotificationRecordModel alert = new NotificationRecordModel();
        alert.setId(10L);
        alert.setType("ALERT");
        when(notificationService.unackedAlerts(1L)).thenReturn(Arrays.asList(alert));

        mockMvc.perform(get("/notifications/unacked-alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void markRead() throws Exception {
        mockMvc.perform(post("/notifications/1/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(notificationService).markRead(1L, 1L);
    }

    @Test
    void ack() throws Exception {
        mockMvc.perform(post("/notifications/1/ack"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(notificationService).ack(1L, 1L);
    }

    @Test
    void list_unauthorized() throws Exception {
        when(securityContext.getCurrentUserId()).thenReturn(null);

        mockMvc.perform(get("/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }
}
