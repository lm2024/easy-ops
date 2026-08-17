package com.ops.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 数据清理配置属性（对应 application.yml 的 easyops.data.cleanup.*）
 * 仅用于读取「按表覆盖保留天数」的 table-retain-days 配置。
 *
 * 说明：YAML 中的 map 在 Spring Environment 里会被展开成扁平 key，
 * 无法用 @Value("${...table-retain-days}") 直接读取，必须通过
 * @ConfigurationProperties 绑定。
 */
@Component
@ConfigurationProperties(prefix = "easyops.data.cleanup")
public class CleanupProperties {

    /** 按表覆盖的保留天数，key 为表名（小写，如 operation_log），value 为天数 */
    private Map<String, Integer> tableRetainDays = new HashMap<>();

    public Map<String, Integer> getTableRetainDays() {
        return tableRetainDays;
    }

    public void setTableRetainDays(Map<String, Integer> tableRetainDays) {
        this.tableRetainDays = tableRetainDays;
    }
}
