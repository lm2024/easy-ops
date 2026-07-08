package com.ops.common.exception;

import lombok.Getter;

/**
 * 系统异常（参数校验异常等）
 */
@Getter
public class SystemException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final String message;

    public SystemException(String message) {
        super(message);
        this.message = message;
    }
}
