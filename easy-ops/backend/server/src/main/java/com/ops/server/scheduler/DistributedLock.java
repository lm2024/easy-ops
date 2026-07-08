package com.ops.server.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.net.InetAddress;
import java.util.UUID;

/**
 * SEC-001: 分布式调度锁
 * 基于数据库的轻量级分布式锁，确保多实例部署时定时任务只在一个实例上执行。
 * 使用 scheduler_lock 表，通过 INSERT 的 UNIQUE 约束实现互斥。
 */
@Component
public class DistributedLock {

    private static final Logger log = LoggerFactory.getLogger(DistributedLock.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String instanceId;

    @Value("${server.instance-id:}")
    public void setInstanceId(String id) {
        if (id == null || id.isEmpty()) {
            this.instanceId = UUID.randomUUID().toString().substring(0, 8);
        } else {
            this.instanceId = id;
        }
    }

    private static final int LOCK_TIMEOUT_MS = 5 * 60 * 1000; // 5 分钟

    @PostConstruct
    public void init() {
        log.info("DistributedLock initialized, instanceId={}", instanceId);
    }

    /**
     * 尝试获取锁。返回 true 表示获取成功，false 表示锁已被其他实例持有且未过期。
     */
    public boolean tryLock(String lockName) {
        long now = System.currentTimeMillis();
        long expireAt = now + LOCK_TIMEOUT_MS;

        try {
            // 尝试插入锁记录（INSERT 失败表示已被其他实例持有）
            jdbcTemplate.update(
                    "INSERT INTO scheduler_lock (lock_name, instance_id, locked_at, expire_at) VALUES (?, ?, ?, ?)",
                    lockName, instanceId, now, expireAt);
            log.debug("Lock acquired: {} by {}", lockName, instanceId);
            return true;
        } catch (Exception e) {
            // 插入失败，尝试清理过期锁（CASE WHEN expire_at < NOW 允许覆盖）
            try {
                int cleaned = jdbcTemplate.update(
                        "UPDATE scheduler_lock SET instance_id = ?, locked_at = ?, expire_at = ? " +
                        "WHERE lock_name = ? AND expire_at < ?",
                        instanceId, now, expireAt, lockName, now);
                if (cleaned > 0) {
                    log.debug("Lock refreshed: {} by {} (was expired)", lockName, instanceId);
                    return true;
                }
            } catch (Exception ex) {
                // 另一个实例刚获取锁，放弃
            }
            log.debug("Lock not acquired: {} (held by another instance)", lockName);
            return false;
        }
    }

    /**
     * 释放锁（仅允许锁持有者释放）
     */
    public void releaseLock(String lockName) {
        jdbcTemplate.update(
                "DELETE FROM scheduler_lock WHERE lock_name = ? AND instance_id = ?",
                lockName, instanceId);
        log.debug("Lock released: {}", lockName);
    }
}
