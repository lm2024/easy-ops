package com.ops.server.exception;

import com.alibaba.fastjson2.JSON;
import com.ops.common.exception.BusinessException;
import com.ops.common.exception.SystemException;
import com.ops.common.response.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e, HttpServletResponse response) throws IOException {
        log.warn("Business exception: {}", e.getMessage());
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(SystemException.class)
    public Result<?> handleSystemException(SystemException e, HttpServletResponse response) throws IOException {
        log.error("System exception", e);
        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        return Result.error(500, "系统内部错误");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleValidationException(MethodArgumentNotValidException e) throws IOException {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数校验失败");
        return Result.error(400, msg);
    }

    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e, HttpServletResponse response) throws IOException {
        log.error("Unexpected exception", e);
        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        return Result.error(500, "系统内部错误");
    }
}
