package com.ops.server.traffic.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 启动时检查 Nginx 流量监控表与日志源配置。
 */
@Component
public class NginxTrafficBootstrap {

    private static final Logger log = LoggerFactory.getLogger(NginxTrafficBootstrap.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${easyops.nginx-traffic.minute-retain-days:7}")
    private int minuteRetainDays;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        try {
            // 幂等建表：历史库（非全新 schema）也能自动补全白名单表，避免手动执行 DDL
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS nginx_source_whitelist (" +
                " id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                " source_id BIGINT NOT NULL," +
                " type VARCHAR(16) NOT NULL," +
                " match_value VARCHAR(500) NOT NULL," +
                " match_mode VARCHAR(16) NOT NULL," +
                " enabled TINYINT DEFAULT 1," +
                " remark VARCHAR(200)," +
                " create_time BIGINT," +
                " update_time BIGINT)");
            jdbcTemplate.execute(
                "CREATE INDEX IF NOT EXISTS idx_nginx_wl_source ON nginx_source_whitelist(source_id)");

            // 旧库补齐 nginx_minute_stat 新增列
            addColumnIfNotExists("nginx_minute_stat", "sum_upstream_connect_time_ms", "BIGINT DEFAULT 0");
            addColumnIfNotExists("nginx_minute_stat", "sum_upstream_header_time_ms", "BIGINT DEFAULT 0");
            addColumnIfNotExists("nginx_minute_stat", "sum_body_bytes", "BIGINT DEFAULT 0");
            addColumnIfNotExists("nginx_minute_stat", "upstream_5xx", "INT DEFAULT 0");
            addColumnIfNotExists("nginx_minute_stat", "cache_hit_count", "INT DEFAULT 0");
            addColumnIfNotExists("nginx_minute_stat", "cache_miss_count", "INT DEFAULT 0");
            addColumnIfNotExists("nginx_minute_stat", "https_count", "INT DEFAULT 0");

            // UA / Referer / 原始样本 三张新表
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS nginx_ua_stat (" +
                " id BIGINT PRIMARY KEY AUTO_INCREMENT, source_id BIGINT NOT NULL, bucket_time BIGINT NOT NULL," +
                " user_agent VARCHAR(500) NOT NULL, request_count INT DEFAULT 0, sum_request_time_ms BIGINT DEFAULT 0," +
                " max_request_time_ms BIGINT DEFAULT 0, slow_count INT DEFAULT 0, status_5xx INT DEFAULT 0, create_time BIGINT)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_nginx_ua ON nginx_ua_stat(source_id, bucket_time)");
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS nginx_referer_stat (" +
                " id BIGINT PRIMARY KEY AUTO_INCREMENT, source_id BIGINT NOT NULL, bucket_time BIGINT NOT NULL," +
                " referer VARCHAR(500) NOT NULL, request_count INT DEFAULT 0, sum_request_time_ms BIGINT DEFAULT 0," +
                " max_request_time_ms BIGINT DEFAULT 0, slow_count INT DEFAULT 0, status_5xx INT DEFAULT 0, create_time BIGINT)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_nginx_referer ON nginx_referer_stat(source_id, bucket_time)");
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS nginx_request_sample (" +
                " id BIGINT PRIMARY KEY AUTO_INCREMENT, source_id BIGINT NOT NULL, ts BIGINT NOT NULL," +
                " client_ip VARCHAR(64), uri VARCHAR(500), method VARCHAR(16), request_time_ms BIGINT DEFAULT 0," +
                " upstream_time_ms BIGINT DEFAULT 0, status INT DEFAULT 0, user_agent VARCHAR(500)," +
                " referer VARCHAR(500), create_time BIGINT)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_nginx_sample_src_ts ON nginx_request_sample(source_id, ts)");

            Integer sourceCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM nginx_access_source", Integer.class);
            Integer statCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM nginx_minute_stat", Integer.class);
            log.info("Nginx流量监控就绪: 日志源={} 分钟统计行数={} 历史保留{}天(超期由定时任务删除)",
                    sourceCount, statCount, minuteRetainDays);
        } catch (Exception e) {
            log.warn("Nginx流量监控表未就绪，请确认已用最新代码重启 Server: {}", e.getMessage());
        }
    }

    private void addColumnIfNotExists(String table, String column, String def) {
        try {
            jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN IF NOT EXISTS " + column + " " + def);
        } catch (Exception e) {
            log.warn("补充列失败 {}.{}: {}", table, column, e.getMessage());
        }
    }
}
