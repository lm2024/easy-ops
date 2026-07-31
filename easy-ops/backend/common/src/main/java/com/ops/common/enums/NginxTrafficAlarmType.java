package com.ops.common.enums;

/**
 * Nginx 流量告警类型
 */
public final class NginxTrafficAlarmType {

    public static final String IP_FREQ = "IP_FREQ";
    public static final String URI_FREQ = "URI_FREQ";
    public static final String STATUS_4XX = "STATUS_4XX";
    public static final String STATUS_5XX = "STATUS_5XX";
    public static final String SLOW = "SLOW";

    private NginxTrafficAlarmType() {
    }
}
