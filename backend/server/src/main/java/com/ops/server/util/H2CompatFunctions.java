package com.ops.server.util;

/**
 * H2 数据库兼容函数（MySQL 函数在 H2 中的 Java 实现）
 */
public class H2CompatFunctions {

    /**
     * MySQL FIND_IN_SET 函数的 H2 实现
     * FIND_IN_SET(str, strlist) — 在逗号分隔的字符串列表中查找 str 的位置（1-based），未找到返回 0
     */
    public static int findInSet(String str, String strlist) {
        if (str == null || strlist == null) return 0;
        String s = str.trim();
        String[] parts = strlist.split(",");
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].trim().equals(s)) {
                return i + 1;
            }
        }
        return 0;
    }
}
