package com.ops.server.controller;

import com.ops.common.model.AlarmModel;
import com.ops.common.response.Result;
import com.ops.server.mapper.AlarmRecordMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 告警管理接口 (T-01-41 ~ T-01-43)
 */
@RestController
@RequestMapping("/alarms")
public class AlarmController {

    @Autowired
    private AlarmRecordMapper alarmRecordMapper;

    /**
     * GET /api/alarms - 告警列表
     */
    @GetMapping
    public Result<?> listAlarms(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        List<AlarmModel> alarms = alarmRecordMapper.findByFilters(projectId, type, page, pageSize);
        Long total = alarmRecordMapper.countByFilters(projectId, type);
        Map<String, Object> data = new HashMap<>();
        data.put("list", alarms);
        data.put("total", total);
        return Result.success(data);
    }

    /**
     * GET /api/alarms/config - 告警配置
     */
    @GetMapping("/config")
    public Result<?> getAlarmConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("enabled", true);
        config.put("smtpHost", "smtp.example.com");
        config.put("smtpPort", 465);
        config.put("sslEnabled", true);
        config.put("receivers", "admin@example.com");
        return Result.success(config);
    }

    /**
     * PUT /api/alarms/config - 保存告警配置
     */
    @PutMapping("/config")
    public Result<?> saveAlarmConfig(@RequestBody Map<String, Object> config) {
        // Save to database
        return Result.success();
    }

    /**
     * DELETE /api/alarms - 清空所有告警
     */
    @DeleteMapping
    public Result<?> clearAlarms() {
        int count = alarmRecordMapper.deleteAll();
        return Result.success("已清空 " + count + " 条告警");
    }

    /**
     * POST /api/alarms/send - 发送告警
     */
    @PostMapping("/send")
    public Result<?> sendAlarm(@RequestBody Map<String, String> request) {
        String nodeId = request.get("nodeId");
        String content = request.get("content");
        String type = request.get("type");

        AlarmModel alarm = new AlarmModel();
        alarm.setNodeId(Long.parseLong(nodeId));
        alarm.setType(type);
        alarm.setContent(content);
        alarm.setSendResult(0);
        alarm.setSendTime(System.currentTimeMillis());
        alarm.setCreateTime(System.currentTimeMillis());
        alarmRecordMapper.insert(alarm);

        return Result.success();
    }
}
