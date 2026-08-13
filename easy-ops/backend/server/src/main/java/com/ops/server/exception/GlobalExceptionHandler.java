package com.ops.server.exception;

import com.alibaba.fastjson2.JSON;
import com.ops.common.exception.BusinessException;
import com.ops.common.exception.SystemException;
import com.ops.common.response.Result;
import org.apache.ibatis.binding.BindingException;
import org.apache.ibatis.exceptions.PersistenceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
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

    /**
     * 资源归属校验异常（TenantResourceAccessService.requireXxx 抛出）→ 403
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public Result<?> handleIllegalArgument(IllegalArgumentException e, HttpServletResponse response) throws IOException {
        log.warn("Access denied: {}", e.getMessage());
        response.setStatus(HttpStatus.FORBIDDEN.value());
        return Result.error(com.ops.common.constant.ErrorCode.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleValidationException(MethodArgumentNotValidException e) throws IOException {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数校验失败");
        return Result.error(400, msg);
    }

    /**
     * MyBatis 参数绑定/SQL 异常 → 返回用户可读提示，而非技术栈
     */
    @ExceptionHandler({BindingException.class, PersistenceException.class})
    public Result<?> handleMyBatisException(Exception e, HttpServletResponse response) {
        log.error("数据库查询异常: {}", e.getMessage(), e);
        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        return Result.error(500, "数据查询失败，请稍后重试（如持续出现请联系管理员）");
    }

    /**
     * Spring 数据访问异常（连接失败、SQL 错误等）
     */
    @ExceptionHandler(DataAccessException.class)
    public Result<?> handleDataAccess(DataAccessException e, HttpServletResponse response) {
        log.error("数据访问异常: {}", e.getMessage(), e);
        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        return Result.error(500, "数据库访问异常，请稍后重试");
    }

    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e, HttpServletResponse response) throws IOException {
        String errorMsg;
        String className = e.getClass().getSimpleName();
        if (className.contains("Timeout")) {
            errorMsg = "请求超时，请稍后重试";
        } else if (className.contains("IOException") || className.contains("Connect")) {
            errorMsg = "连接失败，请检查网络或稍后重试";
        } else if (className.contains("NullPointerException")) {
            errorMsg = "系统内部异常，请联系管理员";
        } else {
            errorMsg = "系统内部异常，请联系管理员";
        }
        log.error("Unexpected exception [{}]: {}", className, e.getMessage(), e);
        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        return Result.error(500, errorMsg);
    }
}
