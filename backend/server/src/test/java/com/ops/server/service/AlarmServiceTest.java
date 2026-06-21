package com.ops.server.service;

import com.ops.common.model.AlarmModel;
import com.ops.server.mapper.AlarmRecordMapper;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlarmServiceTest {

    @Mock
    private AlarmRecordMapper alarmRecordMapper;

    @InjectMocks
    private AlarmService alarmService;

    private AlarmModel sampleAlarm;

    @BeforeEach
    void setUp() {
        sampleAlarm = new AlarmModel();
        sampleAlarm.setId(1L);
        sampleAlarm.setNodeId(10L);
        sampleAlarm.setType("OFFLINE");
        sampleAlarm.setContent("Node 10 went offline");
        sampleAlarm.setSendResult(0);
        sampleAlarm.setCreateTime(System.currentTimeMillis());
    }

    @Test
    @DisplayName("findByFilters - 返回匹配的告警列表")
    void findByFilters_returnsMatchingAlarms() {
        List<AlarmModel> expected = Arrays.asList(
            createAlarm(1L, "OFFLINE"),
            createAlarm(2L, "OFFLINE")
        );
        when(alarmRecordMapper.findByFilters(1L, "OFFLINE", 0, 20))
            .thenReturn(expected);

        List<AlarmModel> result = alarmService.findByFilters(1L, "OFFLINE", 0, 20);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("OFFLINE", result.get(0).getType());
        verify(alarmRecordMapper).findByFilters(1L, "OFFLINE", 0, 20);
    }

    @Test
    @DisplayName("findByFilters - 空结果返回空列表")
    void findByFilters_returnsEmptyList() {
        when(alarmRecordMapper.findByFilters(anyLong(), any(), anyInt(), anyInt()))
            .thenReturn(Collections.emptyList());

        List<AlarmModel> result = alarmService.findByFilters(999L, "ERROR", 0, 10);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("countByFilters - 返回正确计数")
    void countByFilters_returnsCorrectCount() {
        when(alarmRecordMapper.countByFilters(1L, "OFFLINE")).thenReturn(5L);

        Long count = alarmService.countByFilters(1L, "OFFLINE");

        assertEquals(5L, count);
        verify(alarmRecordMapper).countByFilters(1L, "OFFLINE");
    }

    @Test
    @DisplayName("insert - 返回插入行数")
    void insert_returnsInsertCount() {
        when(alarmRecordMapper.insert(sampleAlarm)).thenReturn(1);

        int result = alarmService.insert(sampleAlarm);

        assertEquals(1, result);
        verify(alarmRecordMapper).insert(sampleAlarm);
    }

    private AlarmModel createAlarm(Long id, String type) {
        AlarmModel alarm = new AlarmModel();
        alarm.setId(id);
        alarm.setType(type);
        alarm.setContent("Test alarm " + id);
        alarm.setNodeId(id * 10);
        alarm.setSendResult(0);
        alarm.setCreateTime(System.currentTimeMillis());
        return alarm;
    }
}
