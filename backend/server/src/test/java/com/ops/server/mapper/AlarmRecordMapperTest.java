package com.ops.server.mapper;

import com.ops.common.model.AlarmModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AlarmRecordMapperTest {

    @Autowired
    private AlarmRecordMapper alarmRecordMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanup() {
        jdbcTemplate.execute("DELETE FROM alarm_record");
    }

    @Test
    @DisplayName("insert - 插入告警记录")
    void insert_generatesId() {
        AlarmModel alarm = createAlarm(1L, 1L, "CPU_HIGH");
        int rows = alarmRecordMapper.insert(alarm);
        assertEquals(1, rows);
        assertNotNull(alarm.getId());
    }

    @Test
    @DisplayName("countByFilters - 返回计数")
    void countByFilters_returnsCount() {
        assertEquals(0L, alarmRecordMapper.countByFilters(null, null));
        alarmRecordMapper.insert(createAlarm(1L, 1L, "DISK_FULL"));
        alarmRecordMapper.insert(createAlarm(2L, 1L, "DISK_FULL"));
        assertEquals(2L, alarmRecordMapper.countByFilters(null, "DISK_FULL"));
    }

    private AlarmModel createAlarm(Long projectId, Long nodeId, String type) {
        AlarmModel alarm = new AlarmModel();
        alarm.setProjectId(projectId);
        alarm.setNodeId(nodeId);
        alarm.setType(type);
        alarm.setContent("Test alarm content");
        alarm.setCreateTime(System.currentTimeMillis());
        return alarm;
    }
}
