package com.ops.server.controller;

import com.ops.common.model.AlarmModel;
import com.ops.common.response.Result;
import com.ops.server.mapper.AlarmRecordMapper;
import com.ops.server.service.AuditLogService;
import com.ops.server.util.SecurityContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 告警管理接口：告警列表、告警配置（阈值）
 */
@RestController
@RequestMapping("/alarms")
public class AlarmController {

    @Autowired
    private AlarmRecordMapper alarmRecordMapper;

    @Autowired
    private AuditLogService auditLog;

    @Autowired
    private SecurityContext securityContext;

    /** 告警配置（内存存储，可通过接口修改） */
    private static final Map<String, Object> alarmConfig = new LinkedHashMap<>();
    static {
        alarmConfig.put("healthCheckEnabled", true);      // 健康检查失败告警
        alarmConfig.put("cpuThreshold", 90);               // CPU 告警阈值 (%)
        alarmConfig.put("cpuEnabled", true);               // CPU 告警开关
        alarmConfig.put("responseThreshold", 5000);        // 响应超时阈值 (ms)
        alarmConfig.put("responseEnabled", true);          // 响应超时告警开关
        alarmConfig.put("nodeOfflineEnabled", true);       // 节点离线告警开关
        alarmConfig.put("cooldownMinutes", 30);            // 同一告警冷却时间 (分钟)
    }

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
     * GET /api/alarms/config - 获取告警配置
     */
    @GetMapping("/config")
    public Result<?> getAlarmConfig() {
        return Result.success(new LinkedHashMap<>(alarmConfig));
    }

    /**
     * PUT /api/alarms/config - 保存告警配置
     */
    @PutMapping("/config")
    public Result<?> saveAlarmConfig(@RequestBody Map<String, Object> config) {
        if (config.containsKey("healthCheckEnabled")) alarmConfig.put("healthCheckEnabled", config.get("healthCheckEnabled"));
        if (config.containsKey("cpuThreshold")) alarmConfig.put("cpuThreshold", config.get("cpuThreshold"));
        if (config.containsKey("cpuEnabled")) alarmConfig.put("cpuEnabled", config.get("cpuEnabled"));
        if (config.containsKey("responseThreshold")) alarmConfig.put("responseThreshold", config.get("responseThreshold"));
        if (config.containsKey("responseEnabled")) alarmConfig.put("responseEnabled", config.get("responseEnabled"));
        if (config.containsKey("nodeOfflineEnabled")) alarmConfig.put("nodeOfflineEnabled", config.get("nodeOfflineEnabled"));
        if (config.containsKey("cooldownMinutes")) alarmConfig.put("cooldownMinutes", config.get("cooldownMinutes"));
        auditLog.log("ALARM", "UPDATE_CONFIG", "更新告警配置");
        return Result.success(new LinkedHashMap<>(alarmConfig));
    }

    /** 获取告警配置（供内部服务调用） */
    public static Map<String, Object> getConfig() {
        return new LinkedHashMap<>(alarmConfig);
    }

    /**
     * DELETE /api/alarms - 清空所有告警（仅管理员）
     */
    @DeleteMapping
    public Result<?> clearAlarms() {
        if (!securityContext.isAdmin()) {
            return Result.error(403, "仅管理员可以清空告警");
        }
        int count = alarmRecordMapper.deleteAll();
        auditLog.log("ALARM", "CLEAR", "清空所有告警: " + count + " 条");
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
