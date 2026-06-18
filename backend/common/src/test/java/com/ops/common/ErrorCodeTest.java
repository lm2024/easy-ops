package com.ops.common;

import com.ops.common.constant.ErrorCode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ErrorCodeTest {

    @Test
    void constants() {
        assertEquals(200, ErrorCode.SUCCESS);
        assertEquals(400, ErrorCode.PARAM_ERROR);
        assertEquals(401, ErrorCode.UNAUTHORIZED);
        assertEquals(403, ErrorCode.FORBIDDEN);
        assertEquals(500, ErrorCode.SERVER_ERROR);
        assertEquals(1004, ErrorCode.VERSION_NOT_FOUND);
        assertEquals(1005, ErrorCode.PROJECT_NOT_FOUND);
        assertEquals(1006, ErrorCode.FILE_TYPE_INVALID);
        assertEquals(1007, ErrorCode.PATH_NOT_ALLOWED);
        assertEquals(1008, ErrorCode.PROCESS_NOT_RUNNING);
        assertEquals(1009, ErrorCode.ALARM_SEND_FAIL);
    }
}
