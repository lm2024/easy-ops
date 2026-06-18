package com.ops.common.exception;

import com.ops.common.constant.ErrorCode;
import lombok.Getter;

/**
 * 业务异常
 */
@Getter
public class BusinessException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String message) {
        this(ErrorCode.SERVER_ERROR, message);
    }
}
