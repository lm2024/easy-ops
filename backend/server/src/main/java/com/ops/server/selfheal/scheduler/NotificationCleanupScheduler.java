package com.ops.server.selfheal.scheduler;

import com.ops.server.mapper.NotificationRecordMapper;
import com.ops.server.scheduler.DistributedLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 通知过期清理调度器：每日删除超过 expire_time 的记录
 */
@Component
public class NotificationCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationCleanupScheduler.class);
    private static final String LOCK_NAME = "notification_cleanup";

    @Autowired
    private DistributedLock distributedLock;

    @Autowired
    private NotificationRecordMapper notificationRecordMapper;

    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupExpired() {
        if (!distributedLock.tryLock(LOCK_NAME)) {
            log.debug("NotificationCleanupScheduler: lock not acquired, skipping");
            return;
        }
        try {
            long now = System.currentTimeMillis();
            int deleted = notificationRecordMapper.deleteExpired(now);
            log.info("Notification cleanup: deleted {} expired records", deleted);
        } finally {
            distributedLock.releaseLock(LOCK_NAME);
        }
    }
}
