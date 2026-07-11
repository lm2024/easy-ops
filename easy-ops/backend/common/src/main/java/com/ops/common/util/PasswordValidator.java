package com.ops.common.util;

/**
 * 密码强度校验（普通严格模式）
 * 要求：至少 8 位，含大小写字母、数字、特殊字符
 */
public final class PasswordValidator {

    private static final String SPECIAL = "!@#$%^&*()_+-=[]{}|;':\",./<>?`~";

    private PasswordValidator() {
    }

    /**
     * 校验密码强度，不通过时返回错误说明，通过返回 null
     */
    public static String validate(String password) {
        if (password == null || password.trim().isEmpty()) {
            return "密码不能为空";
        }
        if (password.length() < 8) {
            return "密码至少 8 位";
        }
        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;
        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) {
                hasUpper = true;
            } else if (Character.isLowerCase(c)) {
                hasLower = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            } else if (SPECIAL.indexOf(c) >= 0) {
                hasSpecial = true;
            }
        }
        if (!hasUpper) {
            return "密码需包含大写字母";
        }
        if (!hasLower) {
            return "密码需包含小写字母";
        }
        if (!hasDigit) {
            return "密码需包含数字";
        }
        if (!hasSpecial) {
            return "密码需包含特殊字符（如 !@#$%^&*）";
        }
        return null;
    }
}
