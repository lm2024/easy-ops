package com.ops.common.model;

import lombok.Data;

import java.io.Serializable;

/**
 * Nginx 流量告警规则（按日志源配置）
 */
@Data
public class NginxTrafficAlarmRuleModel implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long sourceId;
    /** 所属租户（来源=所属日志源的租户） */
    private Long tenantId;
    /** IP_FREQ / URI_FREQ / STATUS_4XX / STATUS_5XX / SLOW */
    private String ruleType;
    /** 1=启用 0=停用 */
    private Integer enabled;
    /** 统计窗口（分钟） */
    private Integer windowMinutes;
    /** 触发阈值（次数） */
    private Long threshold;
    /** CRITICAL / WARNING / INFO */
    private String level;
    /** 同类告警冷却（分钟） */
    private Integer cooldownMinutes;
    /** 1=需右上角确认关闭 */
    private Integer requireAck;
    private Long createTime;
    private Long updateTime;
}
