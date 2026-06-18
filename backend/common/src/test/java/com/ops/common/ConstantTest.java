package com.ops.common;

import com.ops.common.constant.SystemConstant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConstantTest {

    @Test
    void systemConstants() {
        assertEquals(30, SystemConstant.HEARTBEAT_INTERVAL);
        assertEquals(90, SystemConstant.OFFLINE_THRESHOLD);
        assertEquals(32, SystemConstant.TOKEN_LENGTH);
        assertEquals("X-Token", SystemConstant.TOKEN_HEADER);
        assertEquals("Authorization", SystemConstant.AUTH_HEADER);
        assertNotNull(SystemConstant.JWT_SECRET);
        assertEquals(86400000L, SystemConstant.JWT_EXPIRE_MS);
        assertEquals("8081", SystemConstant.DEFAULT_SERVER_PORT);
        assertEquals("2123", SystemConstant.DEFAULT_AGENT_PORT);
    }
}
