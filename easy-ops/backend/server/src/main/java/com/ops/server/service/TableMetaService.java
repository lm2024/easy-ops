package com.ops.server.service;

import com.ops.server.config.CleanupProperties;
import com.ops.server.config.TableMetaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 表元数据服务：表分类 / 识别 / 清空策略的单一事实来源。
 *
 * 数据来源（优先级从高到低）：
 *   1. yml 配置 easyops.data.table-meta.tables（显式登记的表）
 *   2. 命名规则启发式兜底（未登记的表，按前缀/后缀规则自动分类）
 *
 * 每张表输出：tableName / label / categoryKey / categoryLabel / icon / type /
 *            source / retainDays / rowCount / clearable / recognized
 *
 * 关键设计：
 *   - rowCount 走缓存（表数据量大时 COUNT 昂贵，缓存 10s 过期）
 *   - clearable 由 type 推导：FLOW / AGENT_SYNC 可清空，BASE / CONFIG 禁止；
 *     且受硬编码保护黑名单兜底（即使 yml 配错也挡得住）
 *   - retainDays 联动 easyops.data.cleanup.table-retain-days 与 nginx minute-retain-days
 */
@Service
public class TableMetaService {

    private static final Logger log = LoggerFactory.getLogger(TableMetaService.class);

    /** 表类型常量 */
    public static final String TYPE_BASE = "BASE";
    public static final String TYPE_CONFIG = "CONFIG";
    public static final String TYPE_FLOW = "FLOW";
    public static final String TYPE_AGENT_SYNC = "AGENT_SYNC";

    /** 可清空类型集合 */
    private static final Set<String> CLEARABLE_TYPES = new HashSet<>(Arrays.asList(TYPE_FLOW, TYPE_AGENT_SYNC));

    /**
     * 硬编码保护黑名单（最后防线）：即使 yml 误配为 FLOW/AGENT_SYNC，这些表也禁止清空。
     * 全部为基础/配置表，删除即破坏平台运行。
     */
    private static final Set<String> PROTECTED_TABLES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "sys_user", "node_info", "project_info", "version_package",
            "tenant", "tenant_user", "user_project_relation",
            "sys_config", "alarm_config", "self_heal_policy",
            "scheduler_lock", "kb_document_lock",
            "nginx_source_whitelist", "nginx_traffic_alarm_rule", "nginx_access_source",
            "project_config_file", "project_log_profile", "project_health_probe",
            "project_script_file", "global_script_file", "kb_category", "kb_document"
    )));

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TableMetaProperties tableMetaProperties;

    @Autowired
    private CleanupProperties cleanupProperties;

    @Value("${easyops.nginx-traffic.minute-retain-days:7}")
    private int nginxMinuteRetainDays;

    /** rowCount 缓存：表名 -> (时间戳, 行数)。列表页展示行数，TTL 30s 避免频繁 COUNT 大表 */
    private final Map<String, long[]> rowCountCache = new ConcurrentHashMap<>();
    private static final long ROW_COUNT_TTL_MS = 30_000L;

    /** 表存在性缓存（防止反复查询 INFORMATION_SCHEMA） */
    private final Map<String, Boolean> existsCache = new ConcurrentHashMap<>();

    /**
     * 获取所有业务表的元数据（自动识别 + yml 配置合并）。
     *
     * @param withRowCount 是否统计行数（大表多时开销高，默认 false 用缓存/缺省值）
     */
    public List<Map<String, Object>> listTables(boolean withRowCount) {
        List<String> names = scanTableNames();
        List<Map<String, Object>> result = new ArrayList<>();
        for (String name : names) {
            result.add(buildMeta(name, withRowCount));
        }
        return result;
    }

    /** 获取单表元数据 */
    public Map<String, Object> getTableMeta(String tableName) {
        return buildMeta(tableName, true);
    }

    /**
     * 是否可清空：除硬编码保护清单（sys_user 等 22 张地基表）外，任意表都可清空。
     * type 仅作为"推荐清空"标记（FLOW/AGENT_SYNC 建议优先清），不限制权限。
     */
    public boolean isClearable(String tableName) {
        String tn = normalizeTableName(tableName);
        if (tn == null) return false;
        return !PROTECTED_TABLES.contains(tn);
    }

    /** 表是否"推荐清空"（流水表/Agent同步表，供前端默认勾选与提示） */
    public boolean isFlowType(String tableName) {
        String tn = normalizeTableName(tableName);
        if (tn == null) return false;
        TableMetaProperties.TableDef def = tableMetaProperties.getTables().get(tn);
        if (def != null) {
            return CLEARABLE_TYPES.contains(def.getType());
        }
        return isFlowByNaming(tn);
    }

    /** 获取所有可清空表名（用于一键清空） */
    public List<String> listClearableTables() {
        List<String> names = scanTableNames();
        List<String> clearable = new ArrayList<>();
        for (String name : names) {
            if (isClearable(name)) clearable.add(name);
        }
        return clearable;
    }

    /** 表名合法性 + 存在性校验（防止 SQL 注入与任意表访问） */
    public boolean isValidTable(String tableName) {
        String tn = normalizeTableName(tableName);
        if (tn == null) return false;
        Boolean cached = existsCache.get(tn);
        if (cached != null) return cached;
        try {
            // H2 INFORMATION_SCHEMA.TABLE_NAME 默认存大写，UPPER 后与参数（大写）比较
            Integer cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES " +
                    "WHERE UPPER(TABLE_NAME) = ? AND TABLE_SCHEMA != 'INFORMATION_SCHEMA'",
                    Integer.class, tn.toUpperCase());
            boolean exists = cnt != null && cnt > 0;
            existsCache.put(tn, exists);
            return exists;
        } catch (Exception e) {
            log.warn("校验表存在性失败: {}", tableName, e);
            return false;
        }
    }

    /** 获取表的保留天数（联动 cleanup.table-retain-days / nginx minute-retain-days），无则 null */
    public Integer getRetainDays(String tableName) {
        String tn = normalizeTableName(tableName);
        if (tn == null) return null;
        Integer days = cleanupProperties.getTableRetainDays().get(tn);
        if (days != null) return days;
        if (tn.startsWith("nginx_")) return nginxMinuteRetainDays;
        return null;
    }

    /** 统计行数（带缓存） */
    public long countRows(String tableName) {
        String tn = normalizeTableName(tableName);
        if (tn == null) return -1;
        long now = System.currentTimeMillis();
        long[] cached = rowCountCache.get(tn);
        if (cached != null && now - cached[0] < ROW_COUNT_TTL_MS) {
            return cached[1];
        }
        try {
            Long cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM \"" + tn.toUpperCase() + "\"", Long.class);
            long val = cnt != null ? cnt : 0L;
            rowCountCache.put(tn, new long[]{now, val});
            return val;
        } catch (Exception e) {
            log.debug("统计行数失败: 表={}, 原因={}", tn, e.getMessage());
            return -1;
        }
    }

    /** 清空单表（仅可清空表），返回删除行数。大表走分批删除避免长事务。 */
    public long clearTable(String tableName) {
        String tn = normalizeTableName(tableName);
        if (tn == null) throw new IllegalArgumentException("无效的表名: " + tableName);
        if (!isClearable(tn)) throw new IllegalArgumentException("该表禁止清空: " + tn);
        String upper = tn.toUpperCase(); // H2 双引号内大小写敏感，实际表名存储为大写

        // 超大表分批删除（如 nginx_minute_stat），避免单条 DELETE 长事务锁库
        if (tn.equals("nginx_minute_stat") || tn.equals("monitor_snapshot")) {
            final int batchSize = 10000;
            long total = 0;
            int deleted;
            do {
                deleted = jdbcTemplate.update(
                        "DELETE FROM \"" + upper + "\" WHERE ID IN " +
                        "(SELECT ID FROM \"" + upper + "\" LIMIT " + batchSize + ")");
                total += deleted;
            } while (deleted >= batchSize);
            rowCountCache.remove(tn);
            return total;
        }

        long before = countRows(tn);
        jdbcTemplate.update("DELETE FROM \"" + upper + "\"");
        rowCountCache.remove(tn);
        return before > 0 ? before : 0;
    }

    /** 一键清空所有可清空表，返回每张表删除行数 */
    public Map<String, Long> clearAllFlow() {
        Map<String, Long> results = new LinkedHashMap<>();
        for (String tn : listClearableTables()) {
            try {
                results.put(tn, clearTable(tn));
            } catch (Exception e) {
                log.warn("一键清空失败: 表={}, 原因={}", tn, e.getMessage());
                results.put(tn, -1L);
            }
        }
        return results;
    }

    // ======================== 内部实现 ========================

    private Map<String, Object> buildMeta(String tableName, boolean withRowCount) {
        String tn = normalizeTableName(tableName);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("tableName", tn);

        TableMetaProperties.TableDef def = tableMetaProperties.getTables().get(tn);
        String categoryKey;
        String type;
        String source = null;
        boolean recognized;
        String label;

        if (def != null) {
            // 已登记：使用 yml 配置
            categoryKey = def.getCategory();
            type = def.getType();
            source = def.getSource();
            recognized = true;
            label = def.getLabel() != null ? def.getLabel() : tn;
        } else {
            // 未登记：命名规则兜底
            label = tn;
            recognized = false;
            type = inferTypeByNaming(tn);
            categoryKey = inferCategoryByNaming(tn);
            if (tn.startsWith("nginx_") || "FLOW".equals(type)) {
                source = tn.startsWith("nginx_") ? "nginx" : null;
            }
        }

        TableMetaProperties.CategoryDef cat = categoryKey == null ? null
                : tableMetaProperties.getCategories().get(categoryKey);
        String categoryLabel = cat != null ? cat.getLabel() : (categoryKey == null ? "其他" : categoryKey);
        String icon = cat != null ? cat.getIcon() : "📄";

        meta.put("label", label);
        meta.put("category", categoryLabel);
        meta.put("categoryKey", categoryKey == null ? "other" : categoryKey);
        meta.put("icon", icon);
        meta.put("type", type);
        if (source != null) meta.put("source", source);
        Integer retainDays = getRetainDays(tn);
        if (retainDays != null) meta.put("retainDays", retainDays);
        meta.put("rowCount", withRowCount ? countRows(tn) : -1);
        meta.put("clearable", isClearable(tn));
        meta.put("flowType", isFlowType(tn)); // 是否推荐清空（流水表/Agent同步表），仅引导用
        meta.put("recognized", recognized);
        return meta;
    }

    /** 扫描业务表名（大写转小写统一） */
    private List<String> scanTableNames() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES " +
                "WHERE TABLE_SCHEMA != 'INFORMATION_SCHEMA' ORDER BY TABLE_NAME");
        List<String> names = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Object v = row.get("TABLE_NAME");
            if (v == null) v = row.get("tableName");
            if (v == null) continue;
            names.add(v.toString().toLowerCase());
        }
        return names;
    }

    /** 表名规范化：转小写 + 去非法字符，非法返回 null */
    private String normalizeTableName(String tableName) {
        if (tableName == null || tableName.trim().isEmpty()) return null;
        String tn = tableName.trim().toLowerCase().replaceAll("[^a-z0-9_]", "");
        if (tn.isEmpty() || !tn.matches("[a-z_][a-z0-9_]*")) return null;
        return tn;
    }

    /** 命名规则推断 type */
    private String inferTypeByNaming(String tn) {
        if (tn.startsWith("nginx_") || tn.startsWith("kb_")) {
            // nginx 统计/采样为流水；kb 大部分为业务数据
            if (tn.startsWith("nginx_")) {
                return isFlowByNaming(tn) ? TYPE_FLOW : TYPE_BASE;
            }
            return isFlowByNaming(tn) ? TYPE_FLOW : TYPE_BASE;
        }
        if (tn.endsWith("_snapshot") || tn.contains("_sync")) return TYPE_AGENT_SYNC;
        if (isFlowByNaming(tn)) return TYPE_FLOW;
        if (tn.endsWith("_config") || tn.endsWith("_lock") || tn.endsWith("_policy")
                || tn.endsWith("_probe") || tn.endsWith("_profile") || tn.endsWith("_rule")) {
            return TYPE_CONFIG;
        }
        return TYPE_BASE;
    }

    /** 命名规则推断 categoryKey */
    private String inferCategoryByNaming(String tn) {
        if (tn.startsWith("nginx_")) return "nginx";
        if (tn.startsWith("kb_")) return "kb";
        if (tn.startsWith("node_")) return "node";
        if (tn.startsWith("project_")) return "project";
        if (tn.startsWith("tenant")) return "tenant";
        if (tn.startsWith("global_script_") || tn.startsWith("script_")) return "script";
        if (tn.startsWith("self_heal_") || tn.contains("notification")) return "heal";
        if (tn.startsWith("alarm_")) return "alarm";
        if (tn.startsWith("operation_") || tn.contains("access_log")) return "audit";
        if (tn.contains("config")) return "config";
        if (tn.endsWith("_log") || tn.endsWith("_stat") || tn.endsWith("_record")
                || tn.endsWith("_event") || tn.endsWith("_sample") || tn.endsWith("_snapshot")) {
            return null; // 无法可靠归类 -> 其他
        }
        return null;
    }

    /** 是否流水表命名（_log/_record/_stat/_event/_sample 结尾，或 nginx 统计表） */
    private boolean isFlowByNaming(String tn) {
        if (tn.endsWith("_log") || tn.endsWith("_record") || tn.endsWith("_stat")
                || tn.endsWith("_event") || tn.endsWith("_sample")) {
            return true;
        }
        // nginx 白名单/告警规则/访问源是配置表，不是流水
        if (tn.startsWith("nginx_") && !tn.endsWith("_stat") && !tn.endsWith("_sample")) {
            return false;
        }
        return false;
    }
}
