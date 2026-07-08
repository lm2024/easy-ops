package com.ops.server.selfheal.controller;

import com.ops.common.model.NotificationRecordModel;
import com.ops.common.response.Result;
import com.ops.server.selfheal.service.NotificationService;
import com.ops.server.util.SecurityContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 站内通知 REST 接口
 */
@RestController
@RequestMapping("/notifications")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private SecurityContext securityContext;

    /**
     * GET /api/notifications - 通知列表
     */
    @GetMapping
    public Result<?> list(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        Long userId = securityContext.getCurrentUserId();
        if (userId == null) {
            return Result.authError();
        }
        Map<String, Object> data = notificationService.listForUser(userId, page, pageSize);
        return Result.success(data);
    }

    /**
     * GET /api/notifications/unread-count - 未读数量
     */
    @GetMapping("/unread-count")
    public Result<?> unreadCount() {
        Long userId = securityContext.getCurrentUserId();
        if (userId == null) {
            return Result.authError();
        }
        Map<String, Object> data = new HashMap<>();
        data.put("count", notificationService.unreadCount(userId));
        return Result.success(data);
    }

    /**
     * GET /api/notifications/unacked-alerts - 未确认告警
     */
    @GetMapping("/unacked-alerts")
    public Result<?> unackedAlerts() {
        Long userId = securityContext.getCurrentUserId();
        if (userId == null) {
            return Result.authError();
        }
        List<NotificationRecordModel> alerts = notificationService.unackedAlerts(userId);
        return Result.success(alerts);
    }

    /**
     * POST /api/notifications/{id}/read - 标记已读
     */
    @PostMapping("/{id}/read")
    public Result<?> markRead(@PathVariable Long id) {
        Long userId = securityContext.getCurrentUserId();
        if (userId == null) {
            return Result.authError();
        }
        notificationService.markRead(id, userId);
        return Result.success();
    }

    /**
     * POST /api/notifications/{id}/ack - 确认关闭告警
     */
    @PostMapping("/{id}/ack")
    public Result<?> ack(@PathVariable Long id) {
        Long userId = securityContext.getCurrentUserId();
        if (userId == null) {
            return Result.authError();
        }
        notificationService.ack(id, userId);
        return Result.success();
    }
}
