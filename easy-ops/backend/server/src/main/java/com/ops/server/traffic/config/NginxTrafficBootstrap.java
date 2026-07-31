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
}
