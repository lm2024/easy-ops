package com.ops.common.response;

import com.ops.common.constant.ErrorCode;
import lombok.Getter;
import java.io.Serializable;

/**
 * 统一响应类
 * 注意：静态工厂方法需要 setter，因此保留 set 方法
 */
@Getter
public class Result<T> implements Serializable {
    private static final long serialVersionUID = 1L;

    private int code;
    private String message;
    private T data;

    /**
     * 静态工厂方法需要 setter
     */
    public void setCode(int code) { this.code = code; }
    public void setMessage(String message) { this.message = message; }
    public void setData(T data) { this.data = data; }

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
