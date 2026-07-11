package com.ops.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordValidatorTest {

    @Test
    @DisplayName("强密码校验通过")
    void validate_strongPassword_ok() {
        assertNull(PasswordValidator.validate("Admin123!"));
    }

    @Test
    @DisplayName("弱密码被拒绝")
    void validate_weakPassword_fail() {
        assertNotNull(PasswordValidator.validate("admin123"));
    }
}
