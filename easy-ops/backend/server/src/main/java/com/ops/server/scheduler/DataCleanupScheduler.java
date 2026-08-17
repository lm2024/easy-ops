package com.ops.server.scheduler;

import com.ops.server.config.CleanupProperties;
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
 *   - nginx_minute_stat          按 bucket_time 清理（保留天数见 easyops.nginx-traffic.minute-retain-days）
 *   - nginx_ua_stat              按 bucket_time 清理（同上）
 *   - nginx_referer_stat         按 bucket_time 清理（同上）
 *   - nginx_request_sample       按 ts 清理（同上）
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

    @Value("${easyops.nginx-traffic.minute-retain-days:7}")
    private int nginxMinuteRetainDays;

    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    @Value("${easyops.data.cleanup.auto-restart:false}")
    private boolean autoRestart;

    @Value("${easyops.data.cleanup.restart-threshold-pct:80}")
    private int restartThresholdPct;

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
    private ScriptDistributeRecordMapper scriptDistributeRecordMapper;
    @Autowired
    private NginxMinuteStatMapper nginxMinuteStatMapper;
    @Autowired
    private NginxDimensionStatMapper nginxDimensionStatMapper;
    @Autowired
    private NginxIpStatMapper nginxIpStatMapper;
    @Autowired
    private NotificationRecordMapper notificationRecordMapper;
    @Autowired
    private UserNotificationStateMapper userNotificationStateMapper;
    @Autowired
    private KbDocumentLockMapper kbDocumentLockMapper;
    @Autowired
    private CleanupProperties cleanupProperties;

    // ======================== 清理任务表 ========================

    private final List<CleanupTask> tasks = new ArrayList<>();

    @PostConstruct
    public void init() {
        if (retainDays < 1) {
            log.warn("DataCleanupScheduler: retainDays={} is invalid, forcing to 1", retainDays);
            retainDays = 1;
        }
        buildTasks();
        log.info("定时清理初始化 保留{}天 表数={}", retainDays, tasks.size());
    }

    /**
     * 构建清理任务列表。新增表只需在此方法加一行即可。
     * 每个任务由表名 + 一个 IntSupplier（返回删除条数）组成。
     */
    private void buildTasks() {
        // ---- create_time 驱动（保留天数支持按表覆盖：easyops.data.cleanup.table-retain-days） ----
        addCreateTimeTask("operation_log",              c -> operationLogMapper.deleteBefore(c));
        addCreateTimeTask("file_access_log",            c -> fileAccessLogMapper.deleteBefore(c));
        addCreateTimeTask("monitor_snapshot",           c -> monitorSnapshotMapper.deleteBefore(c));
        addCreateTimeTask("alarm_record",               c -> alarmRecordMapper.deleteBefore(c));
        addCreateTimeTask("self_heal_event",            c -> selfHealEventMapper.deleteBefore(c));
        addCreateTimeTask("deploy_record",              c -> deployRecordMapper.deleteBefore(c));
        addCreateTimeTask("config_distribute_record",   c -> configDistributeRecordMapper.deleteBefore(c));
        addCreateTimeTask("ai_diagnosis_record",        c -> aiDiagnosisRecordMapper.deleteBefore(c));
        addCreateTimeTask("kb_recent_access",           c -> kbRecentAccessMapper.deleteBefore(c));
        addCreateTimeTask("agent_upgrade_record",       c -> agentUpgradeRecordMapper.deleteBefore(c));
        addCreateTimeTask("global_script_distribute_record", c -> globalScriptDistributeRecordMapper.deleteBefore(c));
        addCreateTimeTask("script_distribute_record",   c -> scriptDistributeRecordMapper.deleteBefore(c));

        // ---- 特殊清理（非 create_time 驱动，cutoff 参数不使用） ----
        // nginx_minute_stat 使用分批删除，避免大数据量锁表
        tasks.add(task("nginx_minute_stat", c -> {
            int days = Math.max(1, nginxMinuteRetainDays);
            long bucketCutoff = System.currentTimeMillis() - days * 24L * 3600L * 1000L;
            final int batchSize = 10000;
            int totalDeleted = 0;
            int deleted;
            do {
                deleted = nginxMinuteStatMapper.deleteBeforeBucketTimeBatch(bucketCutoff, batchSize);
                totalDeleted += deleted;
            } while (deleted >= batchSize);
            if (totalDeleted > 0) {
                log.info("清理 nginx_minute_stat 保留{}天 删除{}条 bucket_time<{}", days, totalDeleted, bucketCutoff);
            }
            return totalDeleted;
        }));
        tasks.add(task("nginx_ip_stat", c -> {
            int days = Math.max(1, nginxMinuteRetainDays);
            long bucketCutoff = System.currentTimeMillis() - days * 24L * 3600L * 1000L;
            int deleted = nginxIpStatMapper.deleteBefore(bucketCutoff);
            if (deleted > 0) {
                log.info("清理 nginx_ip_stat 保留{}天 删除{}条", days, deleted);
            }
            return deleted;
        }));
        tasks.add(task("nginx_ua_stat", c -> {
            int days = Math.max(1, nginxMinuteRetainDays);
            long bucketCutoff = System.currentTimeMillis() - days * 24L * 3600L * 1000L;
            int deleted = nginxDimensionStatMapper.deleteUaBefore(bucketCutoff);
            if (deleted > 0) {
                log.info("清理 nginx_ua_stat 保留{}天 删除{}条", days, deleted);
            }
            return deleted;
        }));
        tasks.add(task("nginx_referer_stat", c -> {
            int days = Math.max(1, nginxMinuteRetainDays);
            long bucketCutoff = System.currentTimeMillis() - days * 24L * 3600L * 1000L;
            int deleted = nginxDimensionStatMapper.deleteRefererBefore(bucketCutoff);
            if (deleted > 0) {
                log.info("清理 nginx_referer_stat 保留{}天 删除{}条", days, deleted);
            }
            return deleted;
        }));
        tasks.add(task("nginx_request_sample", c -> {
            int days = Math.max(1, nginxMinuteRetainDays);
            long cutoff = System.currentTimeMillis() - days * 24L * 3600L * 1000L;
            int deleted = nginxDimensionStatMapper.deleteSamplesBefore(cutoff);
            if (deleted > 0) {
                log.info("清理 nginx_request_sample 保留{}天 删除{}条", days, deleted);
            }
            return deleted;
        }));
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

    /**
     * 创建 create_time 驱动的清理任务。保留天数按表覆盖（easyops.data.cleanup.table-retain-days），
     * 未配置的表回退到全局 retain-days；每个任务独立计算 cutoff。
     */
    private void addCreateTimeTask(String tableName, LongToIntFunction deleter) {
        tasks.add(new CleanupTask(tableName, ignored -> {
            int days = retainDaysFor(tableName);
            long cutoff = System.currentTimeMillis() - days * 24L * 3600L * 1000L;
            return deleter.apply(cutoff);
        }));
    }

    /** 该表的保留天数：table-retain-days 优先，否则用全局 retain-days，最小 1 天 */
    private int retainDaysFor(String tableName) {
        Integer days = cleanupProperties.getTableRetainDays().get(tableName);
        return days != null ? Math.max(1, days) : Math.max(1, retainDays);
    }

    // ======================== 调度入口 ========================

    @Scheduled(cron = "${easyops.data.cleanup.cron:0 0 2 * * ?}")
    public void cleanupAll() {
        if (!distributedLock.tryLock(LOCK_NAME)) {
            return;
        }
        try {
            int safeRetainDays = Math.max(1, retainDays);
            long cutoff = System.currentTimeMillis() - safeRetainDays * 24L * 3600L * 1000L;
            log.info("定时清理开始 保留{}天", safeRetainDays);

            int totalDeleted = 0;
            StringBuilder detail = new StringBuilder();
            for (CleanupTask task : tasks) {
                try {
                    int count = task.action.apply(cutoff);
                    if (count > 0) {
                        totalDeleted += count;
                        if (detail.length() > 0) detail.append(", ");
                        detail.append(task.tableName).append("=").append(count);
                    }
                } catch (Exception e) {
                    log.warn("清理失败 表={} 原因={}", task.tableName, e.getMessage());
                }
            }

            if (totalDeleted > 0) {
                log.info("定时清理完成 共删除{}条 [{}]", totalDeleted, detail);
            } else {
                log.info("定时清理完成 无需清理");
            }
        } catch (Exception e) {
            log.error("定时清理异常", e);
        } finally {
            distributedLock.releaseLock(LOCK_NAME);
            if (autoRestart) {
                maybeAutoRestart();
            }
        }
    }

    /**
     * 磁盘水位高时触发 server 优雅重启。
     *
     * 原理：H2 的 MVStore 在【数据库关闭时】会自动压缩回收空洞空间
     * （实测：删除 90% 数据后保持连接打开文件不缩反涨，关闭连接自动压缩 64 倍）。
     * 定时清理的 DELETE 只标记空闲页，运行中文件只增不减，
     * 因此需要定期关闭数据库才能让文件真正变小 —— 这里用"自动重启 server"来实现。
     *
     * 注意：进程退出后必须由外部守护（systemd / docker --restart / 守护脚本）自动拉起，
     * 否则服务会停止。该功能默认关闭，由 easyops.data.cleanup.auto-restart 开启。
     */
    private void maybeAutoRestart() {
        try {
            java.nio.file.Path dbDir = resolveDbDir();
            java.nio.file.FileStore store = java.nio.file.Files.getFileStore(dbDir);
            long total = store.getTotalSpace();
            long usable = store.getUsableSpace();
            int pct = (int) ((total - usable) * 100 / total);
            log.info("磁盘水位检查（数据库所在盘: {}）: 使用率 {}%", dbDir, pct);
            if (pct >= restartThresholdPct) {
                log.warn("磁盘使用率 {}% >= 阈值 {}%，触发 server 自动重启以回收 H2 空洞空间（依赖外部守护自动拉起）",
                        pct, restartThresholdPct);
                new Thread(() -> {
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException ignored) {
                    }
                    System.exit(0);
                }, "h2-auto-restart").start();
            } else {
                log.info("磁盘使用率 {}% < 阈值 {}%，无需自动重启", pct, restartThresholdPct);
            }
        } catch (Exception e) {
            log.warn("磁盘水位检查失败，跳过自动重启: {}", e.getMessage());
        }
    }

    /**
     * 解析数据库文件（ops.mv.db）实际所在目录。
     * 来源是 spring.datasource.url（jdbc:h2:file:<路径>/ops;MODE=MySQL...），
     * 确保水位判断针对的是【db 真实所在磁盘】，而不是进程工作目录所在盘。
     */
    private java.nio.file.Path resolveDbDir() {
        String url = datasourceUrl;
        if (url == null || url.isEmpty()) {
            // 兜底：从已建连接拿真实 URL
            try (java.sql.Connection c = jdbcTemplate.getDataSource().getConnection()) {
                url = c.getMetaData().getURL();
            } catch (Exception e) {
                log.warn("无法获取数据源 URL: {}", e.getMessage());
                return java.nio.file.Paths.get(System.getProperty("user.dir"));
            }
        }
        int idx = url.indexOf("file:");
        if (idx < 0) {
            log.warn("数据源 URL 非 file 模式，回退到工作目录: {}", url);
            return java.nio.file.Paths.get(System.getProperty("user.dir"));
        }
        String p = url.substring(idx + 5);
        int semi = p.indexOf(';');
        if (semi >= 0) {
            p = p.substring(0, semi);
        }
        if (p.endsWith(".mv.db")) {
            p = p.substring(0, p.length() - 6);
        }
        java.nio.file.Path path = java.nio.file.Paths.get(p).toAbsolutePath().normalize();
        java.nio.file.Path dir = path.getParent();
        return dir != null ? dir : path;
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
