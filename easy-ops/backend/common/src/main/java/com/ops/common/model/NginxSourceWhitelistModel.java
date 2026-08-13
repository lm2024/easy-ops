package com.ops.common.model;

import lombok.Data;

import java.io.Serializable;

/**
 * Nginx 日志源白名单：被排除的维度值不参与任何统计/告警。
 * type 决定匹配维度，matchMode 决定匹配方式，便于后续扩展状态码/地区/UA 等白名单。
 */
@Data
public class NginxSourceWhitelistModel implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    /** 关联日志源 */
    private Long sourceId;
    /** 所属租户（来源=所属日志源的租户） */
    private Long tenantId;
    /** 维度类型：IP / URI / URI_PREFIX / METHOD */
    private String type;
    /** 匹配值（METHOD 时为 GET/POST...；URI_PREFIX 时为前缀） */
    private String matchValue;
    /** 匹配方式：EXACT / PREFIX / CONTAINS */
    private String matchMode;
    /** 1=启用 0=停用 */
    private Integer enabled;
    /** 备注 */
    private String remark;
    private Long createTime;
    private Long updateTime;
}
