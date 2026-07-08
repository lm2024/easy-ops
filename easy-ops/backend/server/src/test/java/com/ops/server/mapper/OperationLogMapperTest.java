package com.ops.server.mapper;

import com.ops.common.model.OperationLogModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class OperationLogMapperTest {

    @Autowired
    private OperationLogMapper operationLogMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanup() {
        jdbcTemplate.execute("DELETE FROM operation_log");
    }

    @Test
    @DisplayName("findById - 返回操作日志")
    void findById_returnsLog() {
        OperationLogModel log = createLog("deploy", "Deployed app-1", "admin");
        operationLogMapper.insert(log);

        OperationLogModel found = operationLogMapper.findById(log.getId());
        assertNotNull(found);
        assertEquals("deploy", found.getModule());
        assertEquals("Deployed app-1", found.getAction());
    }

    @Test
    @DisplayName("findByModule - 按模块查询")
    void findByModule_returnsLogsByModule() {
        operationLogMapper.insert(createLog("deploy", "Deployed app-1", "admin"));
        operationLogMapper.insert(createLog("deploy", "Deployed app-2", "admin"));
        operationLogMapper.insert(createLog("node", "Node added", "admin"));

        List<OperationLogModel> deployLogs = operationLogMapper.findByModule("deploy", 1, 10);
        assertEquals(2, deployLogs.size());

        List<OperationLogModel> nodeLogs = operationLogMapper.findByModule("node", 1, 10);
        assertEquals(1, nodeLogs.size());
    }

    @Test
    @DisplayName("countByModule - 返回模块计数")
    void countByModule_returnsCount() {
        assertEquals(0L, operationLogMapper.countByModule("deploy"));
        operationLogMapper.insert(createLog("deploy", "A", "admin"));
        operationLogMapper.insert(createLog("deploy", "B", "admin"));
        operationLogMapper.insert(createLog("node", "C", "admin"));
        assertEquals(2L, operationLogMapper.countByModule("deploy"));
        assertEquals(1L, operationLogMapper.countByModule("node"));
    }

    @Test
    @DisplayName("insert - 自增ID")
    void insert_generatesId() {
        OperationLogModel log = createLog("system", "System start", "system");
        int rows = operationLogMapper.insert(log);
        assertEquals(1, rows);
        assertNotNull(log.getId());
    }

    private OperationLogModel createLog(String module, String action, String user) {
        OperationLogModel log = new OperationLogModel();
        log.setUserId(1L);
        log.setModule(module);
        log.setAction(action);
        log.setContent(action);
        log.setIp("127.0.0.1");
        log.setCreateTime(System.currentTimeMillis());
        return log;
    }
}
