package com.ops.server.scheduler;

import com.ops.server.mapper.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * 数据清理调度器：每日凌晨自动清理过期的流水数据。
 * 
 * 清理范围（默认保留 7 天）：
 * - operation_log       操作审计日志
 * - file_access_log     文件访问审计
 * - monitor_snapshot    监控快照
 * - alarm_record        告警记录
 * - self_heal_event     自愈事件
 * - deploy_record       部署记录
 * - config_distribute_record 配置分发记录
 * - ai_diagnosis_record AI 诊断记录
 * - kb_recent_access    最近访问记录
 * 
 * notification_record 由 NotificationCleanupScheduler 独立管理。
 * 
 * 清理频率：每天凌晨 2:00 执行一次（可配置 cron 表达式）
 * 保留天数：通过 easyops.data.cleanup.retain-days 配置，默认 7 天（最小 1 天）
 */
@Component
public class DataCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(DataCleanupScheduler.class);
    private static final String LOCK_NAME = "data_cleanup";

    @Value("${easyops.data.cleanup.retain-days:7}")
    private int retainDays;

    @Autowired
    private DistributedLock distributedLock;

    @Autowired
    private OperationLogMapper operationLogMapper;

    @Autowired
    private FileAccessLogMapper fileAccessLogMapper;

    @Autowired
    private MonitorSnapshotMapper monitorSnapshotMapper;

    @Autowired
    private AlarmRecordMapper alarmRecordMapper;

    @Autowired
    private SelfHealEventMapper selfHealEventMapper;

    @Autowired
    private DeployRecordMapper deployRecordMapper;

    @Autowired
    private ConfigDistributeRecordMapper configDistributeRecordMapper;

    @Autowired
    private AIDiagnosisRecordMapper aiDiagnosisRecordMapper;

    @Autowired
    private KbRecentAccessMapper kbRecentAccessMapper;

    @PostConstruct
    public void init() {
        // 确保保留天数最小为 1，防止误删全部数据
        if (retainDays < 1) {
            log.warn("DataCleanupScheduler: retainDays={} is invalid, forcing to 1", retainDays);
            retainDays = 1;
        }
        log.info("DataCleanupScheduler initialized: retainDays={}", retainDays);
    }

    @Scheduled(cron = "${easyops.data.cleanup.cron:0 0 2 * * ?}")
    public void cleanupAll() {
        if (!distributedLock.tryLock(LOCK_NAME)) {
            log.debug("DataCleanupScheduler: lock not acquired, skipping");
            return;
        }
        try {
            int safeRetainDays = Math.max(1, retainDays);
            long cutoff = System.currentTimeMillis() - safeRetainDays * 24L * 3600L * 1000L;
            log.info("DataCleanupScheduler: starting cleanup, retainDays={}", safeRetainDays);

            cleanupOperationLog(cutoff);
            cleanupFileAccessLog(cutoff);
            cleanupMonitorSnapshot(cutoff);
            cleanupAlarmRecord(cutoff);
            cleanupSelfHealEvent(cutoff);
            cleanupDeployRecord(cutoff);
            cleanupConfigDistributeRecord(cutoff);
            cleanupAIDiagnosisRecord(cutoff);
            cleanupKbRecentAccess(cutoff);

            log.info("DataCleanupScheduler: cleanup completed");
        } catch (Exception e) {
            log.error("DataCleanupScheduler: cleanup failed", e);
        } finally {
            distributedLock.releaseLock(LOCK_NAME);
        }
    }

    private void cleanupOperationLog(long cutoff) {
        try {
            int count = operationLogMapper.deleteBefore(cutoff);
            if (count > 0) log.info("Cleaned {} operation_log records", count);
        } catch (Exception e) {
            log.warn("Failed to cleanup operation_log", e);
        }
    }

    private void cleanupFileAccessLog(long cutoff) {
        try {
            int count = fileAccessLogMapper.deleteBefore(cutoff);
            if (count > 0) log.info("Cleaned {} file_access_log records", count);
        } catch (Exception e) {
            log.warn("Failed to cleanup file_access_log", e);
        }
    }

    private void cleanupMonitorSnapshot(long cutoff) {
        try {
            int count = monitorSnapshotMapper.deleteBefore(cutoff);
            if (count > 0) log.info("Cleaned {} monitor_snapshot records", count);
        } catch (Exception e) {
            log.warn("Failed to cleanup monitor_snapshot", e);
        }
    }

    private void cleanupAlarmRecord(long cutoff) {
        try {
            int count = alarmRecordMapper.deleteBefore(cutoff);
            if (count > 0) log.info("Cleaned {} alarm_record records", count);
        } catch (Exception e) {
            log.warn("Failed to cleanup alarm_record", e);
        }
    }

    private void cleanupSelfHealEvent(long cutoff) {
        try {
            int count = selfHealEventMapper.deleteBefore(cutoff);
            if (count > 0) log.info("Cleaned {} self_heal_event records", count);
        } catch (Exception e) {
            log.warn("Failed to cleanup self_heal_event", e);
        }
    }

    private void cleanupDeployRecord(long cutoff) {
        try {
            int count = deployRecordMapper.deleteBefore(cutoff);
            if (count > 0) log.info("Cleaned {} deploy_record records", count);
        } catch (Exception e) {
            log.warn("Failed to cleanup deploy_record", e);
        }
    }

    private void cleanupConfigDistributeRecord(long cutoff) {
        try {
            int count = configDistributeRecordMapper.deleteBefore(cutoff);
            if (count > 0) log.info("Cleaned {} config_distribute_record records", count);
        } catch (Exception e) {
            log.warn("Failed to cleanup config_distribute_record", e);
        }
    }

    private void cleanupAIDiagnosisRecord(long cutoff) {
        try {
            int count = aiDiagnosisRecordMapper.deleteBefore(cutoff);
            if (count > 0) log.info("Cleaned {} ai_diagnosis_record records", count);
        } catch (Exception e) {
            log.warn("Failed to cleanup ai_diagnosis_record", e);
        }
    }

    private void cleanupKbRecentAccess(long cutoff) {
        try {
            int count = kbRecentAccessMapper.deleteBefore(cutoff);
            if (count > 0) log.info("Cleaned {} kb_recent_access records", count);
        } catch (Exception e) {
            log.warn("Failed to cleanup kb_recent_access", e);
        }
    }
}
