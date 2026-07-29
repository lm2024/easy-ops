package com.ops.server.scheduler;

import com.ops.server.mapper.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;

/**
 * 统一数据清理调度器：每日凌晨自动清理所有过期的流水数据。
 * 
 * 支持通过 YML 配置的表（按 create_time 清理）：
 *   - operation_log              操作审计日志
 *   - file_access_log            文件访问审计
 *   - monitor_snapshot           监控快照
 *   - alarm_record               告警记录
 *   - self_heal_event            自愈事件
 *   - deploy_record              部署记录
 *   - config_distribute_record   配置分发记录
 *   - ai_diagnosis_record        AI 诊断记录
 *   - kb_recent_access           最近访问记录
 *   - agent_upgrade_record       Agent 升级记录
 *   - global_script_distribute_record  全局脚本分发记录
 * 
 * 特殊清理（非 create_time 驱动）：
 *   - notification_record        按 expire_time 清理已过期通知
 *   - user_notification_state    级联清理孤儿记录
 *   - kb_document_lock           清理过期文档锁
 *   - scheduler_lock             清理过期分布式锁
 * 
 * 配置项（application.yml）：easyops.data.cleanup
 *   - cron:        每日清理 cron 表达式，默认 "0 0 2 * * ?"
 *   - retain-days: 默认保留天数（最小 1 天），默认 3
 */
@Component
public class DataCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(DataCleanupScheduler.class);
    private static final String LOCK_NAME = "data_cleanup";

    @Value("${easyops.data.cleanup.retain-days:3}")
    private int retainDays;

    // ======================== Mapper 注入 ========================

    @Autowired
    private DistributedLock distributedLock;
    @Autowired
    private JdbcTemplate jdbcTemplate;
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
    @Autowired
    private AgentUpgradeRecordMapper agentUpgradeRecordMapper;
    @Autowired
    private GlobalScriptDistributeRecordMapper globalScriptDistributeRecordMapper;
    @Autowired
    private NotificationRecordMapper notificationRecordMapper;
    @Autowired
    private UserNotificationStateMapper userNotificationStateMapper;
    @Autowired
    private KbDocumentLockMapper kbDocumentLockMapper;

    // ======================== 清理任务表 ========================

    private final List<CleanupTask> tasks = new ArrayList<>();

    @PostConstruct
    public void init() {
        if (retainDays < 1) {
            log.warn("DataCleanupScheduler: retainDays={} is invalid, forcing to 1", retainDays);
            retainDays = 1;
        }
        buildTasks();
        log.info("DataCleanupScheduler initialized: retainDays={}, taskCount={}", retainDays, tasks.size());
    }

    /**
     * 构建清理任务列表。新增表只需在此方法加一行即可。
     * 每个任务由表名 + 一个 IntSupplier（返回删除条数）组成。
     */
    private void buildTasks() {
        // ---- create_time 驱动（使用统一的 cutoff） ----
        tasks.add(task("operation_log",              c -> operationLogMapper.deleteBefore(c)));
        tasks.add(task("file_access_log",            c -> fileAccessLogMapper.deleteBefore(c)));
        tasks.add(task("monitor_snapshot",           c -> monitorSnapshotMapper.deleteBefore(c)));
        tasks.add(task("alarm_record",               c -> alarmRecordMapper.deleteBefore(c)));
        tasks.add(task("self_heal_event",            c -> selfHealEventMapper.deleteBefore(c)));
        tasks.add(task("deploy_record",              c -> deployRecordMapper.deleteBefore(c)));
        tasks.add(task("config_distribute_record",   c -> configDistributeRecordMapper.deleteBefore(c)));
        tasks.add(task("ai_diagnosis_record",        c -> aiDiagnosisRecordMapper.deleteBefore(c)));
        tasks.add(task("kb_recent_access",           c -> kbRecentAccessMapper.deleteBefore(c)));
        tasks.add(task("agent_upgrade_record",       c -> agentUpgradeRecordMapper.deleteBefore(c)));
        tasks.add(task("global_script_distribute_record", c -> globalScriptDistributeRecordMapper.deleteBefore(c)));

        // ---- 特殊清理（非 create_time 驱动，cutoff 参数不使用） ----
        tasks.add(task("notification_record", c ->
                notificationRecordMapper.deleteExpired(System.currentTimeMillis())));
        tasks.add(task("user_notification_state", c ->
                userNotificationStateMapper.deleteOrphan()));
        tasks.add(task("kb_document_lock", c ->
                kbDocumentLockMapper.deleteExpired(System.currentTimeMillis())));
        tasks.add(task("scheduler_lock", c ->
                jdbcTemplate.update("DELETE FROM scheduler_lock WHERE expire_at < ?",
                        System.currentTimeMillis())));
    }

    /**
     * 创建 create_time 驱动的清理任务（lamdba 接收 cutoff 时间戳）
     */
    private CleanupTask task(String tableName, LongToIntFunction action) {
        return new CleanupTask(tableName, action);
    }

    /**
     * 创建非 create_time 驱动的清理任务（lamdba 不依赖 cutoff）
     */
    private CleanupTask task(String tableName, IntSupplier action) {
        return new CleanupTask(tableName, cutoff -> action.getAsInt());
    }

    // ======================== 调度入口 ========================

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

            for (CleanupTask task : tasks) {
                try {
                    int count = task.action.apply(cutoff);
                    if (count > 0) {
                        log.info("Cleaned {} {} records", count, task.tableName);
                    }
                } catch (Exception e) {
                    log.warn("Failed to cleanup {}", task.tableName, e);
                }
            }

            log.info("DataCleanupScheduler: cleanup completed");
        } catch (Exception e) {
            log.error("DataCleanupScheduler: cleanup failed", e);
        } finally {
            distributedLock.releaseLock(LOCK_NAME);
        }
    }

    // ======================== 内部类型 ========================

    /** 函数式接口：接收 cutoff 返回删除条数 */
    @FunctionalInterface
    private interface LongToIntFunction {
        int apply(long cutoff);
    }

    /** 单个清理任务 */
    private static class CleanupTask {
        final String tableName;
        final LongToIntFunction action;

        CleanupTask(String tableName, LongToIntFunction action) {
            this.tableName = tableName;
            this.action = action;
        }
    }
}
