package com.ops.server.scheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DistributedLockTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private DistributedLock distributedLock;

    @BeforeEach
    void setUp() {
        distributedLock = new DistributedLock();
        distributedLock.setInstanceId("test-instance-01");
        ReflectionTestUtils.setField(distributedLock, "jdbcTemplate", jdbcTemplate);
    }

    @Test
    @DisplayName("tryLock - 插入成功返回true")
    void tryLock_success_returnsTrue() {
        lenient().when(jdbcTemplate.update(anyString(), any(), any(), any())).thenReturn(1);

        boolean result = distributedLock.tryLock("test_lock");

        assertTrue(result);
    }

    @Test
    @DisplayName("tryLock - 插入失败时尝试清理过期锁，成功清理后返回true")
    void tryLock_conflict_triesExpireCleanup() {
        // 第一次 INSERT 调用失败 (DuplicateKeyException)
        lenient().when(jdbcTemplate.update(anyString(), any(), any(), any()))
            .thenThrow(new org.springframework.dao.DuplicateKeyException("lock", new Throwable("Unique constraint")));
        // 第二次 UPDATE 成功 (cleanup)
        lenient().when(jdbcTemplate.update(anyString(), any(), any(), any(), any()))
            .thenReturn(1);

        boolean result = distributedLock.tryLock("conflict_lock");

        assertTrue(result);
    }

    @Test
    @DisplayName("tryLock - 两个都失败返回false")
    void tryLock_bothFail_returnsFalse() {
        lenient().when(jdbcTemplate.update(anyString(), any(), any(), any()))
            .thenThrow(new org.springframework.dao.DuplicateKeyException("lock", new Throwable("Unique constraint")));
        lenient().when(jdbcTemplate.update(anyString(), any(), any(), any(), any()))
            .thenThrow(new RuntimeException("another"));

        boolean result = distributedLock.tryLock("contention_lock");

        assertFalse(result);
    }

    @Test
    @DisplayName("releaseLock - 正常释放锁")
    void releaseLock_success() {
        lenient().when(jdbcTemplate.update(anyString(), any(), any())).thenReturn(1);

        distributedLock.releaseLock("test_lock");
    }

    @Test
    @DisplayName("实例ID - 自动生成")
    void setInstanceId_empty_automaticallyGenerated() {
        DistributedLock lock = new DistributedLock();
        lock.setInstanceId("");
        String instanceId = (String) ReflectionTestUtils.getField(lock, "instanceId");
        assertNotNull(instanceId);
        assertFalse(instanceId.isEmpty());
    }

    @Test
    @DisplayName("init - 打印日志初始化信息")
    void init_printsLog() {
        assertDoesNotThrow(() -> distributedLock.init());
    }
}
