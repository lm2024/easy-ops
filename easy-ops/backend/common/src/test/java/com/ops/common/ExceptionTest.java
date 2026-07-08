package com.ops.common;

import com.ops.common.constant.ErrorCode;
import com.ops.common.exception.BusinessException;
import com.ops.common.exception.SystemException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExceptionTest {

    @Test
    void businessException_withCodeAndMessage() {
        BusinessException e = new BusinessException(400, "bad request");
        assertEquals(400, e.getCode());
        assertEquals("bad request", e.getMessage());
    }

    @Test
    void businessException_withMessageOnly() {
        BusinessException e = new BusinessException("server error");
        assertEquals(ErrorCode.SERVER_ERROR, e.getCode());
        assertEquals("server error", e.getMessage());
    }

    @Test
    void businessException_isRuntimeException() {
        assertInstanceOf(RuntimeException.class, new BusinessException(500, "err"));
    }

    @Test
    void systemException() {
        SystemException e = new SystemException("param invalid");
        assertEquals("param invalid", e.getMessage());
    }

    @Test
    void systemException_isRuntimeException() {
        assertInstanceOf(RuntimeException.class, new SystemException("err"));
    }
}
