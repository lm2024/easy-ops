package com.ops.server.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class FileAccessLogMapperTest {

    @Autowired
    private FileAccessLogMapper fileAccessLogMapper;

    @Test
    @DisplayName("insert - 插入文件访问日志")
    void insert_createsLogEntry() {
        Map<String, Object> log = new HashMap<>();
        log.put("userId", 1L);
        log.put("nodeId", 2L);
        log.put("fileType", "config");
        log.put("filePath", "/etc/app/config.yml");
        log.put("action", "read");
        log.put("contentSummary", "Config file read");
        log.put("ip", "127.0.0.1");
        log.put("createTime", System.currentTimeMillis());

        int rows = fileAccessLogMapper.insert(log);
        assertEquals(1, rows);
    }
}
