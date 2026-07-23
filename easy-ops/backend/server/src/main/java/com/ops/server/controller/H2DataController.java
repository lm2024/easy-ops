package com.ops.server.controller;

import com.ops.common.response.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.jdbc.support.rowset.SqlRowSetMetaData;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.util.*;

/**
 * H2 数据库表结构维护控制器
 * 提供表列表、表结构、数据 CRUD、导入导出功能
 */
@RestController
@RequestMapping("/db")
public class H2DataController {

    private static final Logger log = LoggerFactory.getLogger(H2DataController.class);

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;

    @Autowired
    public void setJdbc(DataSource ds) {
        this.jdbc = new JdbcTemplate(ds);
    }

    // ======================== 表列表 ========================

    /**
     * GET /api/db/tables - 列出所有业务表
     */
    @GetMapping("/tables")
    public Result<?> listTables() {
        List<Map<String, Object>> tables = jdbc.queryForList(
                "SELECT TABLE_NAME, REMARKS " +
                "FROM INFORMATION_SCHEMA.TABLES " +
                "WHERE TABLE_SCHEMA != 'INFORMATION_SCHEMA' " +
                "ORDER BY TABLE_NAME");
        tables = toCamelCaseKeys(tables);
        // H2 不同版本返回列名不同: TABLE_NAME→tableName, TABLENAME→tablename
        // 统一标准化为 tableName
        for (Map<String, Object> row : tables) {
            if (row.containsKey("tablename") && !row.containsKey("tableName")) {
                row.put("tableName", row.remove("tablename"));
            }
        }
        log.info("[H2Data] 表列表: 共 {} 张表", tables.size());
        if (!tables.isEmpty()) {
            log.info("[H2Data] 表列表: 首张表字段={}", tables.get(0).keySet());
            log.info("[H2Data] 表列表: 首张表数据={}", tables.get(0));
        }
        return Result.success(tables);
    }

    // ======================== 表结构 ========================

    /**
     * GET /api/db/table/{tableName} - 表结构详情
     */
    @GetMapping("/table/{tableName}")
    public Result<?> tableStructure(@PathVariable String tableName) {
        log.info("[H2Data] 查询表结构: 表名={}", tableName);
        try {
            String tn = tableName.toUpperCase();
            Map<String, Object> result = new LinkedHashMap<>();
            List<Map<String, Object>> columns;

            // 1. 列信息 - 兼容 H2 1.x 和 2.x
            // H2 不同版本列名不同: TYPE_NAME / DATA_TYPE / type_name
            try {
                columns = jdbc.queryForList(
                        "SELECT COLUMN_NAME AS column_name, TYPE_NAME AS type_name, " +
                        "CHARACTER_MAXIMUM_LENGTH AS char_max_len, IS_NULLABLE AS is_nullable, " +
                        "COLUMN_DEFAULT AS column_default " +
                        "FROM INFORMATION_SCHEMA.COLUMNS " +
                        "WHERE TABLE_NAME = ? " +
                        "ORDER BY ORDINAL_POSITION", tn);
            } catch (Exception e1) {
                columns = jdbc.queryForList(
                        "SELECT COLUMN_NAME, DATA_TYPE AS type_name, " +
                        "CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE, " +
                        "COLUMN_DEFAULT " +
                        "FROM INFORMATION_SCHEMA.COLUMNS " +
                        "WHERE TABLE_NAME = ? " +
                        "ORDER BY ORDINAL_POSITION", tn);
            }
            // 尝试获取自增信息
            try {
                List<Map<String, Object>> autoIncCols = jdbc.queryForList(
                    "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS " +
                    "WHERE TABLE_NAME = ? AND IS_IDENTITY = 'YES'", tn);
                Set<String> autoIncSet = new HashSet<>();
                for (Map<String, Object> ac : autoIncCols) {
                    autoIncSet.add(getH2Value(ac, "COLUMN_NAME").toUpperCase());
                }
                for (Map<String, Object> col : columns) {
                    if (autoIncSet.contains(getH2Value(col, "COLUMN_NAME").toUpperCase())) {
                        col.put("IS_IDENTITY", "YES");
                    }
                }
            } catch (Exception e2) {
                log.debug("[H2Data] IS_IDENTITY 不支持, 跳过自增检测");
            }
            result.put("columns", toCamelCaseKeys(columns));

            // 2. 主键 - 兼容 H2 1.x 和 2.x
            List<String> pkColumns = findPrimaryKeyColumns(tn);
            result.put("primaryKey", pkColumns);

            // 3. DDL
            try {
                result.put("ddl", generateDDL(tn, columns, pkColumns));
            } catch (Exception e) {
                log.error("[H2Data] DDL 生成失败: 表={}", tn, e);
                result.put("ddl", "-- DDL 生成失败: " + e.getMessage());
            }

            // 4. 记录数
            try {
                Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM \"" + escapeTableName(tn) + "\"", Integer.class);
                result.put("rowCount", count != null ? count : 0);
            } catch (Exception e) {
                log.debug("[H2Data] 统计行数失败: 表={}, 原因={}", tn, e.getMessage());
                result.put("rowCount", -1);
            }

            return Result.success(result);
        } catch (Exception e) {
            log.error("[H2Data] 查询表结构异常: 表名={}", tableName, e);
            return Result.error(500, "查询表结构失败: " + e.getMessage());
        }
    }

    // ======================== 数据查询 ========================

    /**
     * GET /api/db/table/{tableName}/data - 分页查询数据
     */
    @GetMapping("/table/{tableName}/data")
    public Result<?> queryData(@PathVariable String tableName,
                                @RequestParam(defaultValue = "1") int page,
                                @RequestParam(defaultValue = "50") int pageSize,
                                @RequestParam(required = false) String search) {
        String tn = tableName.toUpperCase();
        log.info("[H2Data] 查询数据: 原始表名={}, 转换后={}, 页码={}, 每页={}, 搜索词={}", tableName, tn, page, pageSize, search);
        int offset = (page - 1) * pageSize;

        // 获取列信息
        List<Map<String, Object>> columns = jdbc.queryForList(
                "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS " +
                "WHERE TABLE_NAME = ? " +
                "ORDER BY ORDINAL_POSITION", tn);
        log.info("[H2Data] 查询数据: 表 {} 共 {} 列", tn, columns.size());
        if (!columns.isEmpty()) {
            log.info("[H2Data] 查询数据: 列名列表={}", columns.stream()
                .map(c -> getH2Value(c, "COLUMN_NAME")).collect(java.util.stream.Collectors.toList()));
        }

        // 构建查询 SQL
        StringBuilder countSql = new StringBuilder("SELECT COUNT(*) FROM \"").append(tn).append("\"");
        StringBuilder dataSql = new StringBuilder("SELECT * FROM \"").append(tn).append("\"");

        // 如果有关键词搜索，对所有文本列做 LIKE
        if (search != null && !search.trim().isEmpty()) {
            List<String> textCols = new ArrayList<>();
            for (Map<String, Object> col : columns) {
                textCols.add("\"" + getH2Value(col, "COLUMN_NAME") + "\"");
            }
            String likeCond = "";
            for (int i = 0; i < textCols.size(); i++) {
                if (i > 0) likeCond += " OR ";
                likeCond += "CAST(" + textCols.get(i) + " AS VARCHAR) LIKE '%" + search.replace("'", "''") + "%'";
            }
            if (!textCols.isEmpty()) {
                String where = " WHERE " + likeCond;
                countSql.append(where);
                dataSql.append(where);
            }
        }

        dataSql.append(" LIMIT ").append(pageSize).append(" OFFSET ").append(offset);

        log.info("[H2Data] 查询数据: countSql={}", countSql);
        log.info("[H2Data] 查询数据: dataSql={}", dataSql);

        Integer total = jdbc.queryForObject(countSql.toString(), Integer.class);
        List<Map<String, Object>> rows = jdbc.queryForList(dataSql.toString());

        log.info("[H2Data] 查询数据: 总数={}, 返回行数={}", total, rows.size());
        if (!rows.isEmpty()) {
            log.info("[H2Data] 查询数据: 首行字段={}, 首行数据={}", rows.get(0).keySet(), rows.get(0));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        // 转换列信息为前端需要的 {name: xxx} 格式
        List<Map<String, Object>> colNameList = new ArrayList<>();
        for (Map<String, Object> col : columns) {
            Map<String, Object> nm = new LinkedHashMap<>();
            nm.put("name", getH2Value(col, "COLUMN_NAME"));
            colNameList.add(nm);
        }
        result.put("columns", colNameList);
        List<Map<String, Object>> camelRows = toCamelCaseKeys(rows);
        result.put("rows", camelRows);
        result.put("total", total != null ? total : 0);
        result.put("page", page);
        result.put("pageSize", pageSize);

        if (!camelRows.isEmpty()) {
            log.info("[H2Data] 查询数据: 驼峰转换后首行字段={}", camelRows.get(0).keySet());
        }
        log.info("[H2Data] 查询数据: 最终结果 - {} 列, {} 行", colNameList.size(), camelRows.size());

        return Result.success(result);
    }

    // ======================== 单行 CRUD ========================

    /**
     * POST /api/db/table/{tableName}/data - 新增行
     */
    @PostMapping("/table/{tableName}/data")
    public Result<?> insertRow(@PathVariable String tableName, @RequestBody Map<String, Object> row) {
        String tn = tableName.toUpperCase();
        log.info("[H2Data] 新增行: 表={}, 原始数据={}", tn, row);
        List<String> cols = new ArrayList<>();
        List<Object> vals = new ArrayList<>();
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (e.getValue() != null) {
                // 驼峰转大写下划线：smtpPort → SMTP_PORT
                cols.add("\"" + camelToUpperSnake(e.getKey()) + "\"");
                vals.add(e.getValue());
            }
        }
        if (cols.isEmpty()) {
            log.warn("[H2Data] 新增行: 没有有效字段, 表={}", tn);
            return Result.paramError("没有有效字段");
        }
        String placeholders = String.join(",", Collections.nCopies(cols.size(), "?"));
        String sql = "INSERT INTO \"" + tn + "\" (" + String.join(",", cols) + ") VALUES (" + placeholders + ")";
        log.info("[H2Data] 新增行: sql={}, 参数={}", sql, vals);
        jdbc.update(sql, vals.toArray());
        log.info("[H2Data] 新增行成功: 表={}", tn);
        return Result.success("插入成功");
    }

    /**
     * PUT /api/db/table/{tableName}/data/{id} - 更新行
     */
    @PutMapping("/table/{tableName}/data/{id}")
    public Result<?> updateRow(@PathVariable String tableName, @PathVariable String id,
                               @RequestBody Map<String, Object> row) {
        String tn = tableName.toUpperCase();
        log.info("[H2Data] 更新行: 表={}, id={}, 数据={}", tn, id, row);
        // 查找主键
        List<String> pk = findPrimaryKey(tn);
        log.info("[H2Data] 更新行: 主键列={}", pk);

        List<Object> vals = new ArrayList<>();
        List<String> setClauses = new ArrayList<>();
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (e.getValue() != null) {
                // 驼峰转大写下划线：smtpPort → SMTP_PORT
                setClauses.add("\"" + camelToUpperSnake(e.getKey()) + "\" = ?");
                vals.add(e.getValue());
            }
        }
        if (setClauses.isEmpty()) {
            log.warn("[H2Data] 更新行: 没有有效字段, 表={}", tn);
            return Result.paramError("没有有效字段");
        }

        // 构建 WHERE 条件
        String where;
        if (!pk.isEmpty()) {
            where = buildPkWhere(pk, id);
        } else {
            where = "\"" + findFirstColumn(tn) + "\" = ?";
        }
        vals.add(id);

        String sql = "UPDATE \"" + tn + "\" SET " + String.join(", ", setClauses) + " WHERE " + where;
        log.info("[H2Data] 更新行: sql={}, 参数={}", sql, vals);
        jdbc.update(sql, vals.toArray());
        log.info("[H2Data] 更新行成功: 表={}, id={}", tn, id);
        return Result.success("更新成功");
    }

    /**
     * DELETE /api/db/table/{tableName}/data/{id} - 删除行
     */
    @DeleteMapping("/table/{tableName}/data/{id}")
    public Result<?> deleteRow(@PathVariable String tableName, @PathVariable String id) {
        String tn = tableName.toUpperCase();
        log.info("[H2Data] 删除行: 表={}, id={}", tn, id);
        List<String> pk = findPrimaryKey(tn);
        log.info("[H2Data] 删除行: 主键列={}", pk);
        String where;
        if (!pk.isEmpty()) {
            where = buildPkWhere(pk, id);
        } else {
            where = "\"" + findFirstColumn(tn) + "\" = ?";
        }
        String sql = "DELETE FROM \"" + tn + "\" WHERE " + where;
        log.info("[H2Data] 删除行: sql={}, 参数={}", sql, id);
        jdbc.update(sql, id);
        log.info("[H2Data] 删除行成功: 表={}, id={}", tn, id);
        return Result.success("删除成功");
    }

    // ======================== 导出 ========================

    /**
     * GET /api/db/table/{tableName}/export - 导出表数据为 JSON
     */
    @GetMapping("/table/{tableName}/export")
    public Result<?> exportData(@PathVariable String tableName) {
        String tn = tableName.toUpperCase();
        log.info("[H2Data] 导出数据: 表={}", tn);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM \"" + tn + "\"");
        log.info("[H2Data] 导出数据: 共 {} 行", rows.size());
        List<Map<String, Object>> columns;
        try {
            columns = jdbc.queryForList(
                    "SELECT COLUMN_NAME AS column_name, TYPE_NAME AS type_name " +
                    "FROM INFORMATION_SCHEMA.COLUMNS " +
                    "WHERE TABLE_NAME = ? " +
                    "ORDER BY ORDINAL_POSITION", tn);
        } catch (Exception e) {
            columns = jdbc.queryForList(
                    "SELECT COLUMN_NAME, DATA_TYPE AS type_name " +
                    "FROM INFORMATION_SCHEMA.COLUMNS " +
                    "WHERE TABLE_NAME = ? " +
                    "ORDER BY ORDINAL_POSITION", tn);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tableName", tableName);
        // 导出列信息使用 {name, type} 格式
        List<Map<String, Object>> exportCols = new ArrayList<>();
        for (Map<String, Object> col : columns) {
            Map<String, Object> nm = new LinkedHashMap<>();
            nm.put("name", getH2Value(col, "COLUMN_NAME"));
            String colType2 = getH2Value(col, "type_name");
            if (colType2.isEmpty()) colType2 = getH2Value(col, "TYPE_NAME");
            if (colType2.isEmpty()) colType2 = getH2Value(col, "DATA_TYPE");
            nm.put("type", colType2);
            exportCols.add(nm);
        }
        result.put("columns", exportCols);
        result.put("rows", toCamelCaseKeys(rows));
        result.put("exportedAt", new Date().toString());
        result.put("rowCount", rows.size());

        return Result.success(result);
    }

    // ======================== 导入 ========================

    /**
     * POST /api/db/table/{tableName}/import - 导入数据
     * mode: truncate (清空后导入) / append (增量导入)
     */
    @SuppressWarnings("unchecked")
    @PostMapping("/table/{tableName}/import")
    public Result<?> importData(@PathVariable String tableName,
                                @RequestParam(defaultValue = "append") String mode,
                                @RequestBody Map<String, Object> body) {
        String tn = tableName.toUpperCase();
        log.info("[H2Data] 导入数据: 表={}, 模式={}", tn, mode);
        Object rowsObj = body.get("rows");
        if (rowsObj == null || !(rowsObj instanceof List)) {
            return Result.paramError("导入数据格式错误，需要 rows 数组");
        }
        List<Map<String, Object>> rows = (List<Map<String, Object>>) rowsObj;
        log.info("[H2Data] 导入数据: 待导入 {} 行", rows.size());
        if (rows.isEmpty()) {
            return Result.success("没有数据需要导入");
        }

        // 清空模式
        if ("truncate".equalsIgnoreCase(mode)) {
            jdbc.execute("DELETE FROM \"" + tn + "\"");
        }

        // 获取表的所有列
        List<Map<String, Object>> columns = jdbc.queryForList(
                "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS " +
                "WHERE TABLE_NAME = ? " +
                "ORDER BY ORDINAL_POSITION", tn);
        Set<String> allCols = new LinkedHashSet<>();
        for (Map<String, Object> col : columns) {
            allCols.add(getH2Value(col, "COLUMN_NAME"));
        }

        int inserted = 0;
        for (Map<String, Object> row : rows) {
            List<String> insertCols = new ArrayList<>();
            List<Object> insertVals = new ArrayList<>();
            for (Map.Entry<String, Object> e : row.entrySet()) {
                // 驼峰转大写下划线：projectId → PROJECT_ID
                String colNameUpper = camelToUpperSnake(e.getKey());
                if (allCols.contains(colNameUpper)) {
                    // 列名用大写：H2 默认大写，双引号引用时大小写敏感
                    insertCols.add("\"" + colNameUpper + "\"");
                    insertVals.add(e.getValue());
                }
            }
            if (insertCols.isEmpty()) continue;
            String placeholders = String.join(",", Collections.nCopies(insertCols.size(), "?"));
            String sql = "INSERT INTO \"" + tn + "\" (" + String.join(",", insertCols) + ") VALUES (" + placeholders + ")";
            try {
                jdbc.update(sql, insertVals.toArray());
                inserted++;
            } catch (Exception e) {
                log.warn("[H2Data] 导入跳过一行: 原因={}", e.getMessage());
            }
        }

        // 获取当前总行数
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM \"" + tn + "\"", Integer.class);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", mode);
        result.put("inserted", inserted);
        result.put("totalRows", total != null ? total : 0);
        result.put("message", "导入完成: " + inserted + "/" + rows.size() + " 行");
        log.info("[H2Data] 导入数据完成: 模式={}, 成功={}/{} 行, 表内总行数={}", mode, inserted, rows.size(), total);

        return Result.success(result);
    }

    // ======================== 全量导出 ========================

    /**
     * GET /api/db/export-all - 全量导出所有业务表
     */
    @GetMapping("/export-all")
    public Result<?> exportAll() {
        log.info("[H2Data] 全量导出: 开始");
        // 获取所有业务表
        List<Map<String, Object>> tableList = jdbc.queryForList(
                "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES " +
                "WHERE TABLE_SCHEMA != 'INFORMATION_SCHEMA' " +
                "ORDER BY TABLE_NAME");

        Map<String, Object> allData = new LinkedHashMap<>();
        allData.put("type", "full");
        allData.put("exportedAt", new Date().toString());

        Map<String, Object> tablesMap = new LinkedHashMap<>();
        int totalRows = 0;
        for (Map<String, Object> tbl : tableList) {
            String tn = getH2Value(tbl, "TABLE_NAME");
            try {
                List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM \"" + tn + "\"");
                List<Map<String, Object>> columns = jdbc.queryForList(
                        "SELECT COLUMN_NAME, DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS " +
                        "WHERE TABLE_NAME = ? ORDER BY ORDINAL_POSITION", tn);
                List<Map<String, Object>> exportCols = new ArrayList<>();
                for (Map<String, Object> col : columns) {
                    Map<String, Object> nm = new LinkedHashMap<>();
                    nm.put("name", getH2Value(col, "COLUMN_NAME"));
                    nm.put("type", getH2Value(col, "DATA_TYPE"));
                    exportCols.add(nm);
                }
                Map<String, Object> tableData = new LinkedHashMap<>();
                tableData.put("columns", exportCols);
                tableData.put("rows", toCamelCaseKeys(rows));
                tableData.put("rowCount", rows.size());
                tablesMap.put(tn, tableData);
                totalRows += rows.size();
                log.info("[H2Data] 全量导出: 表={} 共 {} 行", tn, rows.size());
            } catch (Exception e) {
                log.warn("[H2Data] 全量导出: 跳过表 {} 原因={}", tn, e.getMessage());
            }
        }
        allData.put("tableCount", tablesMap.size());
        allData.put("totalRows", totalRows);
        allData.put("tables", tablesMap);
        log.info("[H2Data] 全量导出完成: {} 张表, {} 行数据", tablesMap.size(), totalRows);

        return Result.success(allData);
    }

    // ======================== 全量导入 ========================

    /**
     * POST /api/db/import-all - 全量导入所有表
     * mode: truncate (清空后导入, 默认) / append (增量导入)
     */
    @SuppressWarnings("unchecked")
    @PostMapping("/import-all")
    public Result<?> importAll(@RequestParam(defaultValue = "truncate") String mode,
                               @RequestBody Map<String, Object> body) {
        log.info("[H2Data] 全量导入: 模式={}", mode);
        Object tablesObj = body.get("tables");
        if (tablesObj == null || !(tablesObj instanceof Map)) {
            return Result.paramError("导入格式错误，需要 tables 对象");
        }
        Map<String, Object> tablesMap = (Map<String, Object>) tablesObj;
        log.info("[H2Data] 全量导入: 待导入 {} 张表", tablesMap.size());

        Map<String, Object> results = new LinkedHashMap<>();
        int totalInserted = 0;
        int totalTables = 0;

        for (Map.Entry<String, Object> entry : tablesMap.entrySet()) {
            String tn = entry.getKey().toUpperCase();
            Map<String, Object> tableData = (Map<String, Object>) entry.getValue();
            List<Map<String, Object>> rows = (List<Map<String, Object>>) tableData.get("rows");
            if (rows == null || rows.isEmpty()) {
                results.put(tn, "跳过(无数据)");
                continue;
            }

            try {
                // 清空模式
                if ("truncate".equalsIgnoreCase(mode)) {
                    jdbc.execute("DELETE FROM \"" + tn + "\"");
                }

                // 获取表的所有列
                List<Map<String, Object>> columns = jdbc.queryForList(
                        "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS " +
                        "WHERE TABLE_NAME = ? ORDER BY ORDINAL_POSITION", tn);
                Set<String> allCols = new LinkedHashSet<>();
                for (Map<String, Object> col : columns) {
                    allCols.add(getH2Value(col, "COLUMN_NAME"));
                }

                int inserted = 0;
                for (Map<String, Object> row : rows) {
                    List<String> insertCols = new ArrayList<>();
                    List<Object> insertVals = new ArrayList<>();
                    for (Map.Entry<String, Object> e : row.entrySet()) {
                        String colNameUpper = camelToUpperSnake(e.getKey());
                        if (allCols.contains(colNameUpper)) {
                            insertCols.add("\"" + colNameUpper + "\"");
                            insertVals.add(e.getValue());
                        }
                    }
                    if (insertCols.isEmpty()) continue;
                    String placeholders = String.join(",", Collections.nCopies(insertCols.size(), "?"));
                    String sql = "INSERT INTO \"" + tn + "\" (" + String.join(",", insertCols) + ") VALUES (" + placeholders + ")";
                    try {
                        jdbc.update(sql, insertVals.toArray());
                        inserted++;
                    } catch (Exception e) {
                        log.warn("[H2Data] 全量导入: 表 {} 跳过一行: {}", tn, e.getMessage());
                    }
                }
                totalInserted += inserted;
                totalTables++;
                results.put(tn, "成功 " + inserted + "/" + rows.size() + " 行");
                log.info("[H2Data] 全量导入: 表={} 成功={}/{} 行", tn, inserted, rows.size());
            } catch (Exception e) {
                results.put(tn, "失败: " + e.getMessage());
                log.error("[H2Data] 全量导入: 表 {} 失败", tn, e);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", mode);
        result.put("tableCount", totalTables);
        result.put("totalInserted", totalInserted);
        result.put("details", results);
        result.put("message", "全量导入完成: " + totalTables + " 张表, " + totalInserted + " 行数据");
        log.info("[H2Data] 全量导入完成: {} 张表, {} 行", totalTables, totalInserted);

        return Result.success(result);
    }

    // ======================== 数据清理 API ========================

    /**
     * POST /api/db/cleanup - 清理所有流水表过期数据
     * 
     * @param retainDays 保留天数（可选，默认7天）
     */
    @PostMapping("/cleanup")
    public Result<?> cleanupAll(@RequestParam(required = false, defaultValue = "7") Integer retainDays) {
        long cutoff = System.currentTimeMillis() - retainDays * 24L * 3600L * 1000L;
        Map<String, Integer> results = new LinkedHashMap<>();
        String[] tables = {
            "operation_log", "file_access_log", "monitor_snapshot",
            "alarm_record", "self_heal_event", "deploy_record",
            "config_distribute_record", "ai_diagnosis_record",
            "notification_record", "kb_recent_access"
        };
        for (String table : tables) {
            try {
                int deleted = jdbc.update("DELETE FROM " + escapeTableName(table) +
                    " WHERE create_time < ?", cutoff);
                if (deleted > 0) {
                    results.put(table, deleted);
                    log.info("Manual cleanup: deleted {} records from {}", deleted, table);
                }
            } catch (Exception e) {
                // notification_record 用 expire_time，单独处理
                if ("notification_record".equals(table)) {
                    try {
                        int deleted = jdbc.update(
                            "DELETE FROM notification_record WHERE expire_time < ?",
                            System.currentTimeMillis());
                        if (deleted > 0) {
                            results.put(table, deleted);
                        }
                    } catch (Exception ex) {
                        log.warn("Failed to cleanup {}: {}", table, ex.getMessage());
                    }
                } else {
                    log.warn("Failed to cleanup {}: {}", table, e.getMessage());
                }
            }
        }
        return Result.success(results);
    }

    /**
     * POST /api/db/cleanup/{tableName} - 清理指定流水表
     * 
     * @param tableName  表名
     * @param retainDays 保留天数（可选，默认7天）
     */
    @PostMapping("/cleanup/{tableName}")
    public Result<?> cleanupTable(@PathVariable String tableName,
                                   @RequestParam(required = false, defaultValue = "7") Integer retainDays) {
        String safeTable = escapeTableName(tableName);
        if (safeTable.isEmpty()) {
            return Result.paramError("无效的表名");
        }

        try {
            int deleted;
            if ("notification_record".equalsIgnoreCase(safeTable)) {
                deleted = jdbc.update(
                    "DELETE FROM " + safeTable + " WHERE expire_time < ?",
                    System.currentTimeMillis());
            } else {
                long cutoff = System.currentTimeMillis() - retainDays * 24L * 3600L * 1000L;
                deleted = jdbc.update(
                    "DELETE FROM " + safeTable + " WHERE create_time < ?", cutoff);
            }
            log.info("Manual cleanup of {}: deleted {} records", safeTable, deleted);
            Map<String, Object> result = new HashMap<>();
            result.put("table", safeTable);
            result.put("deleted", deleted);
            result.put("retainDays", retainDays);
            return Result.success(result);
        } catch (Exception e) {
            log.warn("Failed to cleanup {}: {}", safeTable, e.getMessage());
            return Result.error(com.ops.common.constant.ErrorCode.SERVER_ERROR, "清理失败: " + e.getMessage());
        }
    }

    // ======================== 工具方法 ========================

    private String generateDDL(String tableName, List<Map<String, Object>> columns, List<String> pkColumns) {
        StringBuilder ddl = new StringBuilder();
        ddl.append("CREATE TABLE \"").append(tableName).append("\" (\n");
        for (int i = 0; i < columns.size(); i++) {
            Map<String, Object> col = columns.get(i);
            String colName = getH2Value(col, "COLUMN_NAME");
            String colType = getH2Value(col, "type_name");
            if (colType.isEmpty()) colType = getH2Value(col, "TYPE_NAME");
            if (colType.isEmpty()) colType = getH2Value(col, "DATA_TYPE");
            ddl.append("  \"").append(colName).append("\" ")
               .append(colType);
            String maxLen = getH2Value(col, "CHARACTER_MAXIMUM_LENGTH");
            if (!maxLen.isEmpty() && !"0".equals(maxLen)) {
                ddl.append("(").append(maxLen).append(")");
            }
            String nullable = getH2Value(col, "IS_NULLABLE");
            if (nullable.isEmpty() || "YES".equalsIgnoreCase(nullable)) {
                ddl.append(" NULL");
            } else {
                ddl.append(" NOT NULL");
            }
            String def = getH2Value(col, "COLUMN_DEFAULT");
            if (!def.isEmpty() && !"NULL".equals(def.toUpperCase())) {
                ddl.append(" DEFAULT ").append(def);
            }
            String autoInc = getH2Value(col, "IS_IDENTITY");
            if (autoInc.isEmpty()) {
                autoInc = getH2Value(col, "AUTO_INCREMENT");
            }
            if ("YES".equalsIgnoreCase(autoInc)) {
                ddl.append(" AUTO_INCREMENT");
            }
            if (i < columns.size() - 1) ddl.append(",");
            ddl.append("\n");
        }
        if (!pkColumns.isEmpty()) {
            ddl.append("  PRIMARY KEY (");
            for (int i = 0; i < pkColumns.size(); i++) {
                if (i > 0) ddl.append(", ");
                ddl.append("\"").append(pkColumns.get(i).trim()).append("\"");
            }
            ddl.append(")\n");
        }
        ddl.append(")");
        return ddl.toString();
    }

    /**
     * 查找主键列 - 兼容 H2 1.x 和 2.x
     * H2 2.x: INFORMATION_SCHEMA.INDEXES 没有 COLUMN_LIST 列，也没有 CONSTRAINTS 表
     * 使用 KEY_COLUMN_USAGE + TABLE_CONSTRAINTS（SQL 标准）查询
     */
    private List<String> findPrimaryKeyColumns(String tableName) {
        // 方式1: H2 2.x - 通过 KEY_COLUMN_USAGE 查询（SQL 标准方式）
        try {
            String sql = "SELECT kcu.COLUMN_NAME FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu " +
                "JOIN INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc " +
                "ON kcu.CONSTRAINT_NAME = tc.CONSTRAINT_NAME " +
                "AND kcu.TABLE_SCHEMA = tc.TABLE_SCHEMA " +
                "WHERE tc.TABLE_NAME = ? AND tc.CONSTRAINT_TYPE = 'PRIMARY KEY' " +
                "ORDER BY kcu.ORDINAL_POSITION";
            List<Map<String, Object>> rows = jdbc.queryForList(sql, tableName);
            List<String> pkCols = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                String colName = getH2Value(row, "COLUMN_NAME");
                if (!colName.isEmpty()) {
                    pkCols.add(colName);
                }
            }
            if (!pkCols.isEmpty()) {
                log.debug("[H2Data] 主键检测成功(KEY_COLUMN_USAGE): 表={}, 主键列={}", tableName, pkCols);
                return pkCols;
            }
        } catch (Exception e) {
            log.debug("[H2Data] KEY_COLUMN_USAGE 查询失败: 表={}, 原因={}", tableName, e.getMessage());
        }

        // 方式2: H2 1.x - INDEXES 表有 COLUMN_LIST
        try {
            List<Map<String, Object>> indexes = jdbc.queryForList(
                    "SELECT COLUMN_LIST FROM INFORMATION_SCHEMA.INDEXES " +
                    "WHERE TABLE_NAME = ? AND PRIMARY_KEY = TRUE", tableName);
            for (Map<String, Object> idx : indexes) {
                String colList = getH2Value(idx, "COLUMN_LIST");
                if (!colList.isEmpty()) {
                    return Arrays.asList(colList.split(","));
                }
            }
        } catch (Exception e) {
            log.debug("[H2Data] INDEXES.COLUMN_LIST 查询失败: 表={}, 原因={}", tableName, e.getMessage());
        }

        // 方式3: H2 1.x 备选 - CONSTRAINTS 表
        try {
            List<Map<String, Object>> constraints = jdbc.queryForList(
                    "SELECT COLUMN_LIST FROM INFORMATION_SCHEMA.CONSTRAINTS " +
                    "WHERE TABLE_NAME = ? AND CONSTRAINT_TYPE = 'PRIMARY_KEY'", tableName);
            for (Map<String, Object> c : constraints) {
                String colList = getH2Value(c, "COLUMN_LIST");
                if (!colList.isEmpty()) {
                    return Arrays.asList(colList.split(","));
                }
            }
        } catch (Exception e) {
            log.debug("[H2Data] CONSTRAINTS.COLUMN_LIST 查询失败: 表={}, 原因={}", tableName, e.getMessage());
        }

        log.warn("[H2Data] 所有主键检测方式均失败: 表={}", tableName);
        return Collections.emptyList();
    }

    private List<String> findPrimaryKey(String tableName) {
        return findPrimaryKeyColumns(tableName.toUpperCase());
    }

    private String findFirstColumn(String tableName) {
        try {
            List<Map<String, Object>> cols = jdbc.queryForList(
                    "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS " +
                    "WHERE TABLE_NAME = ? " +
                    "ORDER BY ORDINAL_POSITION", tableName);
            if (!cols.isEmpty()) {
                return getH2Value(cols.get(0), "COLUMN_NAME");
            }
        } catch (Exception ignored) {}
        return "ID";
    }

    private String buildPkWhere(List<String> pkColumns, String idValue) {
        // 简单处理：如果只有一个主键，直接等于
        if (pkColumns.size() == 1) {
            return "\"" + pkColumns.get(0).trim() + "\" = ?";
        }
        // 复合主键：使用 id 参数作为单一值匹配第一个主键
        return "\"" + pkColumns.get(0).trim() + "\" = ?";
    }

    /**
     * 将 Map 的所有 key 转为 camelCase（兼容 H2 不同版本的列名大小写差异）
     * TABLE_NAME → tableName,  TABLENAME → tablename,  tableName → tableName
     */
    private Map<String, Object> toCamelCaseKeys(Map<String, Object> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : map.entrySet()) {
            result.put(toCamelCase(e.getKey()), e.getValue());
        }
        return result;
    }

    /**
     * 将 List<Map> 的所有 key 转为 camelCase
     */
    private List<Map<String, Object>> toCamelCaseKeys(List<Map<String, Object>> list) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> map : list) {
            result.add(toCamelCaseKeys(map));
        }
        return result;
    }

    /**
     * 将下划线或全大写列名转为 camelCase
     */
    /**
     * 从 H2 INFORMATION_SCHEMA 查询结果中取值，兼容不同 H2 版本的列名大小写差异
     * 尝试依次匹配：原样 key → 全小写 → 驼峰 → 忽略大小写遍历
     */
    private String getH2Value(Map<String, Object> map, String key) {
        if (map == null) return "";
        // 尝试传入的 key
        Object v = map.get(key);
        if (v != null) return v.toString();
        // 尝试全小写
        v = map.get(key.toLowerCase());
        if (v != null) return v.toString();
        // 尝试驼峰
        v = map.get(toCamelCase(key));
        if (v != null) return v.toString();
        // 遍历匹配（不区分大小写）
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (e.getKey().equalsIgnoreCase(key)) {
                return e.getValue() != null ? e.getValue().toString() : "";
            }
        }
        return "";
    }

    private String toCamelCase(String key) {
        if (key == null || key.isEmpty()) return key;
        String lower = key.toLowerCase();
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = false;
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (c == '_') {
                nextUpper = true;
            } else if (nextUpper) {
                sb.append(Character.toUpperCase(c));
                nextUpper = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private String escapeTableName(String tn) {
        return tn.replaceAll("[^a-zA-Z0-9_]", "");
    }

    /**
     * 驼峰转大写下划线：smtpPort → SMTP_PORT, updateTime → UPDATE_TIME
     */
    private String camelToUpperSnake(String camel) {
        if (camel == null || camel.isEmpty()) return camel;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < camel.length(); i++) {
            char c = camel.charAt(i);
            if (i > 0 && Character.isUpperCase(c)) {
                sb.append('_');
            }
            sb.append(Character.toUpperCase(c));
        }
        return sb.toString();
    }
}
