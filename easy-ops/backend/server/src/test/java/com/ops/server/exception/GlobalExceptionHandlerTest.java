package com.ops.server.exception;

import com.ops.common.exception.BusinessException;
import com.ops.common.exception.SystemException;
import com.ops.common.response.Result;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleBusinessException() throws Exception {
        BusinessException ex = new BusinessException(400, "bad request");
        MockHttpServletResponse response = new MockHttpServletResponse();
        Result<?> result = handler.handleBusinessException(ex, response);
        assertEquals(400, result.getCode());
        assertEquals("bad request", result.getMessage());
        assertEquals(400, response.getStatus());
    }

    @Test
    void handleSystemException() throws Exception {
        SystemException ex = new SystemException("system error");
        MockHttpServletResponse response = new MockHttpServletResponse();
        Result<?> result = handler.handleSystemException(ex, response);
        assertEquals(500, result.getCode());
        assertEquals("系统内部错误", result.getMessage());
        assertEquals(500, response.getStatus());
    }

    @Test
    void handleException() throws Exception {
        Exception ex = new RuntimeException("unexpected");
        MockHttpServletResponse response = new MockHttpServletResponse();
        Result<?> result = handler.handleException(ex, response);
        assertEquals(500, result.getCode());
        assertEquals("系统内部错误: RuntimeException", result.getMessage());
        assertEquals(500, response.getStatus());
    }
}
