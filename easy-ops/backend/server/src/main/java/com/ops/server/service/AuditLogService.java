package com.ops.server.service;

import com.ops.common.model.OperationLogModel;
import com.ops.server.mapper.OperationLogMapper;
import com.ops.server.util.SecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;

/**
 * 操作审计日志服务 — 各模块统一调用记录操作
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    @Autowired
    private OperationLogMapper operationLogMapper;

    @Autowired
    private SecurityContext securityContext;

    @Autowired(required = false)
    private HttpServletRequest request;

    /**
     * 记录操作日志
     * @param module  模块: DEPLOY, PROJECT, VERSION, CONFIG, ALARM, NODE, AUTH
     * @param action  操作: CREATE, UPDATE, DELETE, LOGIN, DEPLOY, RESTART, READ, ACK, CLEAR 等
     * @param content 操作内容描述
     */
    public void log(String module, String action, String content) {
        try {
            OperationLogModel record = new OperationLogModel();
            record.setUserId(securityContext.getCurrentUserId());
            record.setModule(module);
            record.setAction(action);
            record.setContent(content);
            record.setIp(getClientIp());
            record.setCreateTime(System.currentTimeMillis());
            operationLogMapper.insert(record);
        } catch (Exception e) {
            // 审计日志不应影响业务流程
            log.warn("Failed to write audit log: {} - {}: {}", module, action, e.getMessage());
        }
    }

    private String getClientIp() {
        if (request == null) return "";
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty()) {
            return ip.split(",")[0].trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty()) {
            return ip;
        }
        return request.getRemoteAddr();
    }
}
