package com.ops.server.controller;

import com.ops.server.interceptor.AuthInterceptor;
import com.ops.server.util.SecurityContext;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public abstract class BaseControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    @MockBean
    protected AuthInterceptor authInterceptor;

    @MockBean
    protected SecurityContext securityContext;

    @BeforeEach
    void setupAuth() throws Exception {
        when(authInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        // Default: all users can access all projects (admin-like behavior for tests)
        when(securityContext.hasProjectPermission(any())).thenReturn(true);
        when(securityContext.hasProjectPermission((Long) any())).thenReturn(true);
        when(securityContext.getAccessibleProjectIds()).thenReturn(Collections.emptyList());
        // Mockito 默认对 Long 返回 0L（非 null），会导致租户守卫误判。显式置 null = 平台视图（super_admin）
        when(securityContext.getCurrentTenantId()).thenReturn(null);
    }
}
