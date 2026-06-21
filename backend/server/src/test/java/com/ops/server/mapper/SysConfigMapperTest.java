package com.ops.server.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SysConfigMapperTest {

    @Autowired
    private SysConfigMapper sysConfigMapper;

    @Test
    @DisplayName("getValue - 查询不存在的配置返回null")
    void getValue_nonExisting_returnsNull() {
        assertNull(sysConfigMapper.getValue("nonexistent.key"));
    }

    @Test
    @DisplayName("getValue - 查询存在的配置")
    void getValue_existingReturnsValue() {
        sysConfigMapper.upsert("app.debug", "true", "Debug mode flag", System.currentTimeMillis());
        String value = sysConfigMapper.getValue("app.debug");
        assertNotNull(value);
    }

    @Test
    @DisplayName("upsert - 插入新配置")
    void upsert_insertsConfig() {
        sysConfigMapper.upsert("feature.new", "enabled", "New feature flag", System.currentTimeMillis());
        String value = sysConfigMapper.getValue("feature.new");
        assertEquals("enabled", value);
    }

    @Test
    @DisplayName("upsert - 更新已有配置")
    void upsert_updatesExisting() {
        sysConfigMapper.upsert("app.rate", "old-value", "Rate limit", System.currentTimeMillis());
        sysConfigMapper.upsert("app.rate", "updated", "Rate limit", System.currentTimeMillis());
        String value = sysConfigMapper.getValue("app.rate");
        assertEquals("updated", value);
    }
}
