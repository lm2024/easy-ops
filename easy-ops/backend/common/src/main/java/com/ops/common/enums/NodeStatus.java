package com.ops.common.enums;

/**
 * 节点状态枚举
 */
public enum NodeStatus {
    OFFLINE(0, "离线"),
    ONLINE(1, "在线");

    private final int code;
    private final String desc;

    NodeStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() { return code; }
    public String getDesc() { return desc; }

    public static NodeStatus fromCode(int code) {
        for (NodeStatus s : values()) {
            if (s.code == code) return s;
        }
        return OFFLINE;
    }
}
