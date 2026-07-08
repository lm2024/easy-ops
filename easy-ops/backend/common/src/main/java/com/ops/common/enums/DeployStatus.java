package com.ops.common.enums;

/**
 * 部署状态枚举
 */
public enum DeployStatus {
    PROCESSING(0, "进行中"),
    SUCCESS(1, "成功"),
    FAILED(2, "失败"),
    ROLLBACK(3, "回滚"),
    ROLLBACK_DONE(4, "回滚完成"),
    SCHEDULED(5, "待部署");

    private final int code;
    private final String desc;

    DeployStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() { return code; }
    public String getDesc() { return desc; }
}
