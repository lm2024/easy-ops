package com.ops.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LogLevelUtilTest {

    @Test
    void matches_allLevel() {
        String line = "2026-07-10 14:10:47.378  INFO 1 --- [main] c.App : hello";
        assertTrue(LogLevelUtil.matches(line, "ALL"));
        assertTrue(LogLevelUtil.matches(line, null));
    }

    @Test
    void matches_specificLevel() {
        String info = "2026-07-10 14:10:47.378  INFO 1 --- [main] c.App : hello";
        String error = "2026-07-10 14:10:47.378 ERROR 1 --- [main] c.App : boom";
        assertTrue(LogLevelUtil.matches(info, "INFO"));
        assertFalse(LogLevelUtil.matches(info, "ERROR"));
        assertTrue(LogLevelUtil.matches(error, "ERROR"));
    }

    @Test
    void extractLevel() {
        assertEquals("WARN", LogLevelUtil.extractLevel("2026-07-10 10:00:00.000  WARN 1 --- [t] x : y"));
    }
}
