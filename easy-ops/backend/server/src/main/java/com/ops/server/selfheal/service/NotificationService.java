package com.ops.server.selfheal.service;

import com.ops.common.model.NotificationRecordModel;
import com.ops.common.model.UserNotificationStateModel;
import com.ops.server.mapper.NotificationRecordMapper;
import com.ops.server.mapper.UserNotificationStateMapper;
import com.ops.server.selfheal.websocket.NotificationHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 站内通知服务：创建、已读、确认、列表与未读统计
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class NotificationService {

    private static final long EXPIRE_MS = 7L * 24 * 60 * 60 * 1000;

    @Autowired
    private NotificationRecordMapper notificationRecordMapper;

    @Autowired
    private UserNotificationStateMapper userNotificationStateMapper;

    @Autowired
    private NotificationHandler notificationHandler;

    /**
     * 创建广播通知（所有用户可见，不需要确认）
     * 用于监控告警等场景
     */
    public NotificationRecordModel createBroadcastNotification(
            String type, String level, String title, String content,
            Long projectId, Long nodeId, String sourceType) {
        NotificationRecordModel record = new NotificationRecordModel();
        record.setType(type);
        record.setLevel(level);
        record.setTitle(title);
        record.setContent(content);
        record.setProjectId(projectId);
        record.setNodeId(nodeId);
        record.setSourceType(sourceType);
        record.setRequireAck(0);
        record.setBroadcast(1);
        return create(record);
    }

    /**
     * 创建通知并可选 WebSocket 广播
     */
    public NotificationRecordModel create(NotificationRecordModel record) {
        long now = System.currentTimeMillis();
        if (record.getCreateTime() == null) {
            record.setCreateTime(now);
        }
        if (record.getExpireTime() == null) {
            record.setExpireTime(record.getCreateTime() + EXPIRE_MS);
        }
        if (record.getBroadcast() == null) {
            record.setBroadcast(1);
        }
        if (record.getRequireAck() == null) {
            record.setRequireAck(0);
        }
        notificationRecordMapper.insert(record);

        // 所有广播通知都通过 WebSocket 推送（不仅限于 requireAck=1）
        if (record.getBroadcast() != null && record.getBroadcast() == 1) {
            notificationHandler.broadcastNewNotification(record);
        }
        return record;
    }

    /**
     * 分页查询用户通知
     */
    public Map<String, Object> listForUser(Long userId, Integer page, Integer pageSize) {
        long now = System.currentTimeMillis();
        List<NotificationRecordModel> list = notificationRecordMapper.findByUserId(userId, now, page, pageSize);
        Long total = notificationRecordMapper.countByUserId(userId, now);
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", total);
        return data;
    }

    /**
     * 未读数量
     */
    public long unreadCount(Long userId) {
        return userNotificationStateMapper.countUnread(userId, System.currentTimeMillis());
    }

    /**
     * 未确认告警列表
     */
    public List<NotificationRecordModel> unackedAlerts(Long userId) {
        return notificationRecordMapper.findUnackedAlerts(userId, System.currentTimeMillis());
    }

    /**
     * 标记已读
     */
    public void markRead(Long notificationId, Long userId) {
        ensureState(notificationId, userId);
        userNotificationStateMapper.updateRead(notificationId, userId, 1);
    }

    /**
     * 确认关闭告警
     */
    public void ack(Long notificationId, Long userId) {
        ensureState(notificationId, userId);
        long now = System.currentTimeMillis();
        userNotificationStateMapper.updateRead(notificationId, userId, 1);
        userNotificationStateMapper.updateAck(notificationId, userId, 1, now);
    }

    /**
     * 全部标记已读
     */
    public int markAllRead(Long userId) {
        return userNotificationStateMapper.markAllRead(userId);
    }

    /**
     * 清空已读通知（将已读通知标记为过期）
     */
    public int clearRead(Long userId) {
        return userNotificationStateMapper.clearRead(userId, System.currentTimeMillis());
    }

    private void ensureState(Long notificationId, Long userId) {
        UserNotificationStateModel state = userNotificationStateMapper.findByNotificationAndUser(notificationId, userId);
        if (state == null) {
            UserNotificationStateModel newState = new UserNotificationStateModel();
            newState.setNotificationId(notificationId);
            newState.setUserId(userId);
            newState.setReadStatus(0);
            newState.setAckStatus(0);
            userNotificationStateMapper.insert(newState);
        }
    }
}
