package com.ops.server.traffic.scheduler;

import com.ops.server.scheduler.DistributedLock;
import com.ops.server.traffic.service.NginxTrafficAlarmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每分钟评估 Nginx 流量告警规则。
 */
@Component
public class NginxTrafficAlarmScheduler {

    private static final Logger log = LoggerFactory.getLogger(NginxTrafficAlarmScheduler.class);
    private static final String LOCK_NAME = "nginx_traffic_alarm";

    @Autowired
    private NginxTrafficAlarmService alarmService;
    @Autowired
    private DistributedLock distributedLock;

    @Scheduled(initialDelay = 45000, fixedDelay = 60000)
    public void evaluate() {
        if (!distributedLock.tryLock(LOCK_NAME)) {
            return;
        }
        try {
            alarmService.evaluateAll();
        } catch (Exception e) {
            log.warn("Nginx流量告警评估失败", e);
        } finally {
            distributedLock.releaseLock(LOCK_NAME);
        }
    }
}
