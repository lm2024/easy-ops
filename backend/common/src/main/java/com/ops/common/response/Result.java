package com.ops.common.response;

import com.ops.common.constant.ErrorCode;
import lombok.Data;
import java.io.Serializable;

/**
 * 统一响应类
 */
@Data
public class Result<T> implements Serializable {
    private static final long serialVersionUID = 1L;

    private int code;
    private String message;
    private T data;

    public static <T> Result<T> success(T data) {
        Result<T> r = new Result<>();
        r.setCode(ErrorCode.SUCCESS);
        r.setMessage("success");
        r.setData(data);
        return r;
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> error(int code, String message) {
        Result<T> r = new Result<>();
        r.setCode(code);
        r.setMessage(message);
        return r;
    }

    public static <T> Result<T> paramError(String message) {
        return error(ErrorCode.PARAM_ERROR, message);
    }

    public static <T> Result<T> authError() {
        return error(ErrorCode.UNAUTHORIZED, "Unauthorized");
    }

    public static <T> Result<T> serverError() {
        return error(ErrorCode.SERVER_ERROR, "Internal server error");
    }
}
