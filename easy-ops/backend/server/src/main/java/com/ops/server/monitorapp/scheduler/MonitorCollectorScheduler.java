package com.ops.server.monitorapp.scheduler;

import com.ops.server.monitorapp.service.MonitorCollectConfigService;
import com.ops.server.monitorapp.service.MonitorCollectorService;
import com.ops.server.scheduler.DistributedLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 按配置频率自动采集应用监控快照。
 */
@Component
public class MonitorCollectorScheduler implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(MonitorCollectorScheduler.class);
    private static final String LOCK_NAME = "monitor_collector";

    @Autowired
    private MonitorCollectorService collectorService;
    @Autowired
    private MonitorCollectConfigService configService;
    @Autowired
    private DistributedLock distributedLock;

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addTriggerTask(this::collectSafely, triggerContext -> {
            long intervalMs = configService.getIntervalSec() * 1000L;
            return new PeriodicTrigger(intervalMs, TimeUnit.MILLISECONDS).nextExecutionTime(triggerContext);
        });
    }

    private void collectSafely() {
        if (!distributedLock.tryLock(LOCK_NAME)) {
            return;
        }
        try {
            // 使用异步采集，不阻塞调度线程
            String taskId = collectorService.collectAllAsync();
            log.info("监控采集已启动 taskId={}", taskId);
        } catch (Exception e) {
            log.error("监控采集失败", e);
        } finally {
            distributedLock.releaseLock(LOCK_NAME);
        }
    }
}
