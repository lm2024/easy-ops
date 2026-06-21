package com.ops.server.scheduler;

import com.ops.common.model.AlarmModel;
import com.ops.common.model.NodeModel;
import com.ops.server.mapper.AlarmRecordMapper;
import com.ops.server.mapper.NodeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HeartbeatCheckerTest {

    @Mock
    private NodeMapper nodeMapper;

    @Mock
    private AlarmRecordMapper alarmRecordMapper;

    @Mock
    private DistributedLock distributedLock;

    @InjectMocks
    private HeartbeatChecker heartbeatChecker;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(heartbeatChecker, "heartSecond", 30);
        ReflectionTestUtils.setField(heartbeatChecker, "offlineSecond", 90);
    }

    @Test
    @DisplayName("checkOffline - 获取锁成功时执行检测")
    void checkOffline_lockAcquired_executesDetection() {
        when(distributedLock.tryLock("heartbeat_checker")).thenReturn(true);
        when(nodeMapper.getOfflineCandidates(anyLong())).thenReturn(Collections.emptyList());

        heartbeatChecker.checkOffline();

        verify(distributedLock).tryLock("heartbeat_checker");
        verify(distributedLock).releaseLock("heartbeat_checker");
        verify(nodeMapper).getOfflineCandidates(anyLong());
    }

    @Test
    @DisplayName("checkOffline - 锁失败时跳过")
    void checkOffline_lockFailed_skips() {
        when(distributedLock.tryLock("heartbeat_checker")).thenReturn(false);

        heartbeatChecker.checkOffline();

        verify(distributedLock).tryLock("heartbeat_checker");
        // 锁获取失败时不调用 releaseLock
        // verify(distributedLock).releaseLock("heartbeat_checker");
        verify(nodeMapper, never()).getOfflineCandidates(anyLong());
    }

    @Test
    @DisplayName("checkOffline - 离线节点触发告警")
    void checkOffline_offlineNodesTriggerAlarms() {
        when(distributedLock.tryLock("heartbeat_checker")).thenReturn(true);

        NodeModel offlineNode = new NodeModel();
        offlineNode.setId(1L);
        offlineNode.setName("offline-node");
        offlineNode.setIp("10.0.0.1");
        when(nodeMapper.getOfflineCandidates(anyLong())).thenReturn(Arrays.asList(offlineNode));

        heartbeatChecker.checkOffline();

        verify(nodeMapper).updateStatusOffline(1L);
        verify(alarmRecordMapper).insert(argThat(alarm ->
            "OFFLINE".equals(alarm.getType()) &&
            alarm.getSendResult() == 0 &&
            alarm.getNodeId() != null && alarm.getNodeId() == 1L
        ));
    }

    @Test
    @DisplayName("checkOffline - 无离线节点不触发")
    void checkOffline_noOfflineNodes() {
        when(distributedLock.tryLock("heartbeat_checker")).thenReturn(true);
        when(nodeMapper.getOfflineCandidates(anyLong())).thenReturn(Collections.emptyList());

        heartbeatChecker.checkOffline();

        verify(nodeMapper).getOfflineCandidates(anyLong());
        verify(alarmRecordMapper, never()).insert(any());
    }

    @Test
    @DisplayName("checkOffline - null id的节点被跳过")
    void checkOffline_nullId_skipped() {
        when(distributedLock.tryLock("heartbeat_checker")).thenReturn(true);

        NodeModel badNode = new NodeModel();
        badNode.setId(null);
        badNode.setName("bad-node");
        when(nodeMapper.getOfflineCandidates(anyLong())).thenReturn(Arrays.asList(badNode));

        assertDoesNotThrow(() -> heartbeatChecker.checkOffline());

        verify(nodeMapper).getOfflineCandidates(anyLong());
        verify(nodeMapper, never()).updateStatusOffline(anyLong());
        verify(alarmRecordMapper, never()).insert(any());
    }
}
