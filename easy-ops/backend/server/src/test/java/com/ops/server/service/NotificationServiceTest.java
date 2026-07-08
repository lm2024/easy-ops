package com.ops.server.service;

import com.ops.common.model.NotificationRecordModel;
import com.ops.common.model.UserNotificationStateModel;
import com.ops.server.mapper.NotificationRecordMapper;
import com.ops.server.mapper.UserNotificationStateMapper;
import com.ops.server.selfheal.service.NotificationService;
import com.ops.server.selfheal.websocket.NotificationHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRecordMapper notificationRecordMapper;

    @Mock
    private UserNotificationStateMapper userNotificationStateMapper;

    @Mock
    private NotificationHandler notificationHandler;

    @InjectMocks
    private NotificationService notificationService;

    private NotificationRecordModel sampleAlert;

    @BeforeEach
    void setUp() {
        sampleAlert = new NotificationRecordModel();
        sampleAlert.setId(100L);
        sampleAlert.setType("ALERT");
        sampleAlert.setLevel("CRITICAL");
        sampleAlert.setTitle("测试告警");
        sampleAlert.setContent("自愈失败");
        sampleAlert.setRequireAck(1);
        sampleAlert.setBroadcast(1);
    }

    @Test
    @DisplayName("create - ALERT 类型广播 WebSocket")
    void create_broadcastsAlert() {
        when(notificationRecordMapper.insert(any(NotificationRecordModel.class))).thenAnswer(invocation -> {
            NotificationRecordModel r = invocation.getArgument(0);
            r.setId(100L);
            return 1;
        });

        NotificationRecordModel result = notificationService.create(sampleAlert);

        assertNotNull(result.getId());
        assertNotNull(result.getExpireTime());
        verify(notificationHandler).broadcastAlert(any(NotificationRecordModel.class));
    }

    @Test
    @DisplayName("listForUser - 返回分页数据")
    void listForUser_returnsPagedData() {
        when(notificationRecordMapper.findByUserId(eq(1L), anyLong(), eq(1), eq(20)))
                .thenReturn(Arrays.asList(sampleAlert));
        when(notificationRecordMapper.countByUserId(eq(1L), anyLong())).thenReturn(1L);

        Map<String, Object> data = notificationService.listForUser(1L, 1, 20);

        assertEquals(1L, data.get("total"));
        assertEquals(1, ((List<?>) data.get("list")).size());
    }

    @Test
    @DisplayName("unreadCount - 返回未读数")
    void unreadCount_returnsCount() {
        when(userNotificationStateMapper.countUnread(eq(1L), anyLong())).thenReturn(5L);
        assertEquals(5L, notificationService.unreadCount(1L));
    }

    @Test
    @DisplayName("unackedAlerts - 返回未确认告警")
    void unackedAlerts_returnsList() {
        when(notificationRecordMapper.findUnackedAlerts(eq(1L), anyLong()))
                .thenReturn(Collections.singletonList(sampleAlert));
        List<NotificationRecordModel> alerts = notificationService.unackedAlerts(1L);
        assertEquals(1, alerts.size());
    }

    @Test
    @DisplayName("markRead - 创建状态并标记已读")
    void markRead_createsStateIfMissing() {
        when(userNotificationStateMapper.findByNotificationAndUser(100L, 1L)).thenReturn(null);
        when(userNotificationStateMapper.insert(any(UserNotificationStateModel.class))).thenReturn(1);

        notificationService.markRead(100L, 1L);

        verify(userNotificationStateMapper).insert(any(UserNotificationStateModel.class));
        verify(userNotificationStateMapper).updateRead(100L, 1L, 1);
    }

    @Test
    @DisplayName("ack - 确认告警")
    void ack_updatesAckStatus() {
        UserNotificationStateModel state = new UserNotificationStateModel();
        state.setNotificationId(100L);
        state.setUserId(1L);
        when(userNotificationStateMapper.findByNotificationAndUser(100L, 1L)).thenReturn(state);

        notificationService.ack(100L, 1L);

        verify(userNotificationStateMapper).updateAck(eq(100L), eq(1L), eq(1), anyLong());
    }
}
