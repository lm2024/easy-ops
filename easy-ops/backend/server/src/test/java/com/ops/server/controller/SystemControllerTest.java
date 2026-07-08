package com.ops.server.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ops.common.model.OperationLogModel;
import com.ops.common.model.UserModel;
import com.ops.server.mapper.OperationLogMapper;
import com.ops.server.mapper.UserMapper;
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

class SystemControllerTest extends BaseControllerTest {

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserMapper userMapper;

    @MockBean
    private OperationLogMapper operationLogMapper;

    @Test
    void login_success() throws Exception {
        UserModel user = new UserModel();
        user.setId(1L);
        user.setUsername("admin");
        user.setPassword("$2a$10$W7T3JOSYSRZ4olR9IKhrduXBKaHCy5rc071h8tzOblW/XjrklUX52");
        user.setRole("admin");
        user.setStatus(1);
        when(userMapper.findByUsername("admin")).thenReturn(user);

        Map<String, String> body = new HashMap<>();
        body.put("username", "admin");
        body.put("password", "admin123");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void login_userNotFound() throws Exception {
        when(userMapper.findByUsername("nobody")).thenReturn(null);

        Map<String, String> body = new HashMap<>();
        body.put("username", "nobody");
        body.put("password", "123");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void listUsers() throws Exception {
        UserModel u = new UserModel();
        u.setId(1L);
        u.setUsername("admin");
        when(userMapper.findAll(1, 20)).thenReturn(Arrays.asList(u));
        when(userMapper.countAll()).thenReturn(1L);

        mockMvc.perform(get("/auth/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void listUsers_empty() throws Exception {
        when(userMapper.findAll(1, 20)).thenReturn(Collections.emptyList());
        when(userMapper.countAll()).thenReturn(0L);

        mockMvc.perform(get("/auth/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void createUser() throws Exception {
        when(userMapper.findByUsername("newuser")).thenReturn(null);
        when(userMapper.insert(any(UserModel.class))).thenReturn(1);

        UserModel user = new UserModel();
        user.setUsername("newuser");
        user.setPassword("pass123");
        user.setRole("operator");

        mockMvc.perform(post("/auth/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void updateUser() throws Exception {
        UserModel existing = new UserModel();
        existing.setId(1L);
        existing.setUsername("admin");
        existing.setPassword("$2a$10$xxxx");
        when(userMapper.findById(1L)).thenReturn(existing);
        when(userMapper.update(any(UserModel.class))).thenReturn(1);

        UserModel user = new UserModel();
        user.setUsername("updated");

        mockMvc.perform(put("/auth/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void deleteUser() throws Exception {
        when(userMapper.deleteById(1L)).thenReturn(1);

        mockMvc.perform(delete("/auth/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void getOperations() throws Exception {
        OperationLogModel log = new OperationLogModel();
        log.setId(1L);
        log.setModule("node");
        when(operationLogMapper.findByModule("node", 1, 20)).thenReturn(Arrays.asList(log));
        when(operationLogMapper.countByModule("node")).thenReturn(1L);

        mockMvc.perform(get("/auth/operations").param("module", "node"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
