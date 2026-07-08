package com.ops.server.selfheal.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 邮件通知桩实现：仅记录日志，后续可对接 SMTP
 */
@Service
public class EmailNotifyService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotifyService.class);

    /**
     * 发送自愈失败邮件（桩）
     */
    public void sendSelfHealFailEmail(String title, String content, Long projectId, Long nodeId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", title);
        payload.put("content", content);
        payload.put("projectId", projectId);
        payload.put("nodeId", nodeId);
        payload.put("type", "SELF_HEAL_FAIL");
        log.info("[EmailNotify STUB] would send email: {}", payload);
    }
}
