package com.ops.server.monitorapp.service;

import com.ops.server.mapper.SysConfigMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 应用监控采集频率配置（持久化到 sys_config）。
 */
@Service
public class MonitorCollectConfigService {

    public static final String CONFIG_KEY = "monitor.collect_interval_sec";
    public static final int DEFAULT_INTERVAL_SEC = 60;
    public static final int MIN_INTERVAL_SEC = 1;
    public static final int MAX_INTERVAL_SEC = 3600;

    @Autowired
    private SysConfigMapper sysConfigMapper;

    /**
     * 获取采集间隔（秒）。
     */
    public int getIntervalSec() {
        String raw = sysConfigMapper.getValue(CONFIG_KEY);
        if (raw == null || raw.trim().isEmpty()) {
            return DEFAULT_INTERVAL_SEC;
        }
        try {
            return clamp(Integer.parseInt(raw.trim()));
        } catch (NumberFormatException e) {
            return DEFAULT_INTERVAL_SEC;
        }
    }

    /**
     * 保存采集间隔（秒）。
     */
    public int saveIntervalSec(int intervalSec) {
        int safe = clamp(intervalSec);
        sysConfigMapper.upsert(CONFIG_KEY, String.valueOf(safe),
                "应用监控自动采集间隔（秒）", System.currentTimeMillis());
        return safe;
    }

    private int clamp(int value) {
        if (value < MIN_INTERVAL_SEC) {
            return MIN_INTERVAL_SEC;
        }
        if (value > MAX_INTERVAL_SEC) {
            return MAX_INTERVAL_SEC;
        }
        return value;
    }
}
