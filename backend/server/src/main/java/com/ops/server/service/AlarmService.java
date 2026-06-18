package com.ops.server.service;

import com.ops.common.model.AlarmModel;
import com.ops.server.mapper.AlarmRecordMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AlarmService {
    @Autowired
    private AlarmRecordMapper alarmRecordMapper;

    public List<AlarmModel> findByFilters(Long projectId, String type, Integer page, Integer pageSize) {
        return alarmRecordMapper.findByFilters(projectId, type, page, pageSize);
    }
    public Long countByFilters(Long projectId, String type) { return alarmRecordMapper.countByFilters(projectId, type); }
    public int insert(AlarmModel alarm) { return alarmRecordMapper.insert(alarm); }
}
