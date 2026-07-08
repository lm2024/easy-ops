package com.ops.common;

import com.ops.common.response.Result;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ResultTest {

    @Test
    void success_withData() {
        Result<String> r = Result.success("hello");
        assertEquals(200, r.getCode());
        assertEquals("success", r.getMessage());
        assertEquals("hello", r.getData());
    }

    @Test
    void success_noData() {
        Result<Object> r = Result.success();
        assertEquals(200, r.getCode());
        assertEquals("success", r.getMessage());
        assertNull(r.getData());
    }

    @Test
    void success_withList() {
        List<String> list = Arrays.asList("a", "b");
        Result<List<String>> r = Result.success(list);
        assertEquals(200, r.getCode());
        assertEquals(2, r.getData().size());
    }

    @Test
    void error_withCodeAndMessage() {
        Result<Object> r = Result.error(500, "err");
        assertEquals(500, r.getCode());
        assertEquals("err", r.getMessage());
        assertNull(r.getData());
    }

    @Test
    void paramError() {
        Result<Object> r = Result.paramError("bad");
        assertEquals(400, r.getCode());
        assertEquals("bad", r.getMessage());
    }

    @Test
    void authError() {
        Result<Object> r = Result.authError();
        assertEquals(401, r.getCode());
        assertEquals("Unauthorized", r.getMessage());
    }

    @Test
    void serverError() {
        Result<Object> r = Result.serverError();
        assertEquals(500, r.getCode());
        assertEquals("Internal server error", r.getMessage());
    }
}
