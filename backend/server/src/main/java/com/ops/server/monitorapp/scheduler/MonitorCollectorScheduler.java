package com.ops.server.monitorapp.scheduler;

import com.ops.server.monitorapp.service.MonitorCollectorService;
import com.ops.server.scheduler.DistributedLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时采集应用监控快照
 */
@Component
public class MonitorCollectorScheduler {

    private static final Logger log = LoggerFactory.getLogger(MonitorCollectorScheduler.class);
    private static final String LOCK_NAME = "monitor_collector";

    @Autowired
    private MonitorCollectorService collectorService;
    @Autowired
    private DistributedLock distributedLock;

    @Scheduled(fixedRate = 60000)
    public void collect() {
        if (!distributedLock.tryLock(LOCK_NAME)) {
            return;
        }
        try {
            collectorService.collectAll();
        } catch (Exception e) {
            log.error("Monitor collection failed: {}", e.getMessage());
        } finally {
            distributedLock.releaseLock(LOCK_NAME);
        }
    }

    @Scheduled(cron = "0 0 3 * * ?")
    public void purgeOldData() {
        if (!distributedLock.tryLock(LOCK_NAME + "_purge")) {
            return;
        }
        try {
            int deleted = collectorService.purgeOldSnapshots(30);
            log.info("Purged {} old monitor snapshots", deleted);
        } finally {
            distributedLock.releaseLock(LOCK_NAME + "_purge");
        }
    }
}
