package com.ops.server.traffic.service;

import com.ops.common.enums.NginxTrafficAlarmType;
import com.ops.common.model.NginxAccessSourceModel;
import com.ops.common.model.NginxSourceWhitelistModel;
import com.ops.common.model.NginxTrafficAlarmRuleModel;
import com.ops.common.model.NotificationRecordModel;
import com.ops.server.mapper.NginxAccessSourceMapper;
import com.ops.server.mapper.NginxMinuteStatMapper;
import com.ops.server.mapper.NginxSourceWhitelistMapper;
import com.ops.server.mapper.NginxTrafficAlarmRuleMapper;
import com.ops.server.selfheal.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nginx 流量告警：规则 CRUD + 定时评估 + 站内通知。
 */
@Service
public class NginxTrafficAlarmService {

    private static final Logger log = LoggerFactory.getLogger(NginxTrafficAlarmService.class);
    private static final String SOURCE_TYPE = "NGINX_TRAFFIC";
    private static final int TOP_DETAIL_LIMIT = 3;

    private final ConcurrentHashMap<String, Long> cooldownMap = new ConcurrentHashMap<String, Long>();

    @Autowired
    private NginxTrafficAlarmRuleMapper ruleMapper;
    @Autowired
    private NginxAccessSourceMapper sourceMapper;
    @Autowired
    private NginxMinuteStatMapper minuteStatMapper;
    @Autowired
    private NginxSourceWhitelistMapper whitelistMapper;
    @Autowired
    private NotificationService notificationService;

    public List<NginxTrafficAlarmRuleModel> listBySourceId(Long sourceId) {
        if (sourceId == null) {
            return new ArrayList<NginxTrafficAlarmRuleModel>();
        }
        List<NginxTrafficAlarmRuleModel> rules = ruleMapper.findBySourceId(sourceId);
        if (rules == null || rules.isEmpty()) {
            ensureDefaultRules(sourceId);
            rules = ruleMapper.findBySourceId(sourceId);
        }
        return rules == null ? new ArrayList<NginxTrafficAlarmRuleModel>() : rules;
    }

    @Transactional
    public void deleteBySourceId(Long sourceId, Long tenantId) {
        if (sourceId != null) {
            ruleMapper.deleteBySourceId(sourceId, tenantId);
        }
    }

    @Transactional
    public List<NginxTrafficAlarmRuleModel> saveRules(Long sourceId, Long tenantId, List<NginxTrafficAlarmRuleModel> rules) {
        if (sourceId == null) {
            throw new IllegalArgumentException("sourceId 不能为空");
        }
        if (rules == null || rules.isEmpty()) {
            return listBySourceId(sourceId);
        }
        long now = System.currentTimeMillis();
        for (NginxTrafficAlarmRuleModel rule : rules) {
            normalizeRule(rule, sourceId, tenantId, now);
            if (rule.getId() == null) {
                NginxTrafficAlarmRuleModel existing = ruleMapper.findBySourceIdAndType(sourceId, rule.getRuleType());
                if (existing != null) {
                    rule.setId(existing.getId());
                }
            }
            if (rule.getId() == null) {
                ruleMapper.insert(rule);
            } else {
                ruleMapper.update(rule);
            }
        }
        return listBySourceId(sourceId);
    }

    @Transactional
    public void ensureDefaultRules(Long sourceId) {
        if (sourceId == null) {
            return;
        }
        NginxAccessSourceModel source = sourceMapper.findById(sourceId);
        Long tenantId = source != null ? source.getTenantId() : null;
        long now = System.currentTimeMillis();
        createDefaultIfMissing(sourceId, tenantId, NginxTrafficAlarmType.IP_FREQ, 500L, 10, "WARNING", 15, now);
        createDefaultIfMissing(sourceId, tenantId, NginxTrafficAlarmType.URI_FREQ, 1000L, 10, "WARNING", 15, now);
        createDefaultIfMissing(sourceId, tenantId, NginxTrafficAlarmType.STATUS_4XX, 100L, 10, "WARNING", 15, now);
        createDefaultIfMissing(sourceId, tenantId, NginxTrafficAlarmType.STATUS_5XX, 10L, 10, "CRITICAL", 15, now);
        createDefaultIfMissing(sourceId, tenantId, NginxTrafficAlarmType.SLOW, 50L, 10, "WARNING", 15, now);
    }

    /**
     * 评估所有启用的告警规则。
     */
    public int evaluateAll() {
        List<NginxTrafficAlarmRuleModel> enabledRules = ruleMapper.findAllEnabled();
        if (enabledRules == null || enabledRules.isEmpty()) {
            return 0;
        }
        int fired = 0;
        Map<Long, NginxAccessSourceModel> sourceCache = new HashMap<Long, NginxAccessSourceModel>();
        for (NginxTrafficAlarmRuleModel rule : enabledRules) {
            NginxAccessSourceModel source = sourceCache.get(rule.getSourceId());
            if (source == null) {
                source = sourceMapper.findById(rule.getSourceId());
                if (source == null || source.getEnabled() == null || source.getEnabled() != 1) {
                    continue;
                }
                sourceCache.put(rule.getSourceId(), source);
            }
            if (evaluateRule(source, rule)) {
                fired++;
            }
        }
        if (fired > 0) {
            log.info("Nginx流量告警评估完成，触发 {} 条", fired);
        }
        return fired;
    }

    private boolean evaluateRule(NginxAccessSourceModel source, NginxTrafficAlarmRuleModel rule) {
        int windowMinutes = rule.getWindowMinutes() == null || rule.getWindowMinutes() <= 0 ? 10 : rule.getWindowMinutes();
        long threshold = rule.getThreshold() == null || rule.getThreshold() <= 0 ? 1L : rule.getThreshold();
        long end = System.currentTimeMillis();
        long start = end - windowMinutes * 60L * 1000L;
        String ruleType = rule.getRuleType();
        Map<String, Object> wl = buildWhitelistParam(source.getId());

        if (NginxTrafficAlarmType.IP_FREQ.equals(ruleType)) {
            List<Map<String, Object>> hits = minuteStatMapper.listIpAboveThreshold(
                    source.getId(), start, end, threshold, TOP_DETAIL_LIMIT, wl);
            if (hits == null || hits.isEmpty()) {
                return false;
            }
            StringBuilder detail = new StringBuilder();
            for (Map<String, Object> row : hits) {
                detail.append(row.get("clientIp")).append("(").append(row.get("requestCount")).append("次) ");
            }
            return fire(source, rule, "单IP访问过频",
                    "近" + windowMinutes + "分钟内单IP请求 ≥ " + threshold + " 次\n" + detail.toString().trim(),
                    ruleType + "-" + hits.get(0).get("clientIp"));
        }

        if (NginxTrafficAlarmType.URI_FREQ.equals(ruleType)) {
            List<Map<String, Object>> hits = minuteStatMapper.listUriAboveThreshold(
                    source.getId(), start, end, threshold, TOP_DETAIL_LIMIT, wl);
            if (hits == null || hits.isEmpty()) {
                return false;
            }
            StringBuilder detail = new StringBuilder();
            for (Map<String, Object> row : hits) {
                detail.append(row.get("uri")).append("(").append(row.get("requestCount")).append("次)\n");
            }
            return fire(source, rule, "接口访问过频",
                    "近" + windowMinutes + "分钟内单接口请求 ≥ " + threshold + " 次\n" + detail.toString().trim(),
                    ruleType + "-" + hits.get(0).get("uri"));
        }

        Map<String, Object> sums = normalizeMap(minuteStatMapper.sumStatusAndSlow(source.getId(), start, end, wl));
        if (NginxTrafficAlarmType.STATUS_4XX.equals(ruleType)) {
            long count = longVal(sums, "status4xx");
            if (count < threshold) {
                return false;
            }
            return fire(source, rule, "4xx 错误过多",
                    "近" + windowMinutes + "分钟 4xx 共 " + count + " 次（阈值 " + threshold + "）",
                    ruleType);
        }
        if (NginxTrafficAlarmType.STATUS_5XX.equals(ruleType)) {
            long count = longVal(sums, "status5xx");
            if (count < threshold) {
                return false;
            }
            return fire(source, rule, "5xx 错误过多",
                    "近" + windowMinutes + "分钟 5xx 共 " + count + " 次（阈值 " + threshold + "）",
                    ruleType);
        }
        if (NginxTrafficAlarmType.SLOW.equals(ruleType)) {
            long count = longVal(sums, "slowCount");
            if (count < threshold) {
                return false;
            }
            double slowSec = source.getSlowThresholdSec() == null ? 3D : source.getSlowThresholdSec();
            return fire(source, rule, "慢请求过多",
                    "近" + windowMinutes + "分钟慢请求 " + count + " 次（阈值 " + threshold
                            + "，慢判定 ≥ " + slowSec + "s）",
                    ruleType);
        }
        return false;
    }

    private boolean fire(NginxAccessSourceModel source, NginxTrafficAlarmRuleModel rule,
                         String titleSuffix, String content, String detailKey) {
        int cooldownMin = rule.getCooldownMinutes() == null || rule.getCooldownMinutes() <= 0 ? 15 : rule.getCooldownMinutes();
        String cooldownKey = source.getId() + "-" + rule.getRuleType() + "-" + detailKey;
        if (!shouldFire(cooldownKey, cooldownMin * 60L * 1000L)) {
            return false;
        }
        String level = rule.getLevel() == null ? "WARNING" : rule.getLevel();
        String title = "【Nginx】" + source.getName() + " " + titleSuffix;
        String fullContent = content + "\n日志源: " + source.getLogPath() + "\n节点ID: " + source.getNodeId();

        if (rule.getRequireAck() != null && rule.getRequireAck() == 1) {
            NotificationRecordModel record = new NotificationRecordModel();
            record.setType("ALERT");
            record.setLevel(level);
            record.setTitle(title);
            record.setContent(fullContent);
            record.setNodeId(source.getNodeId());
            record.setSourceType(SOURCE_TYPE);
            record.setSourceId(source.getId());
            record.setRequireAck(1);
            record.setBroadcast(1);
            notificationService.create(record);
        } else {
            notificationService.createBroadcastNotification(
                    "ALERT", level, title, fullContent,
                    null, source.getNodeId(), SOURCE_TYPE);
        }
        log.info("Nginx流量告警触发 source={} type={} level={}", source.getName(), rule.getRuleType(), level);
        return true;
    }

    private boolean shouldFire(String key, long cooldownMs) {
        long now = System.currentTimeMillis();
        long cutoff = now - cooldownMs;
        return cooldownMap.compute(key, (k, last) -> {
            if (last != null && last > cutoff) {
                return last;
            }
            return now;
        }) == now;
    }

    /**
     * 构建查询侧白名单 SQL 参数（同 NginxTrafficService.buildWhitelistParam）。
     * 告警评估与统计共用同一份白名单，保证业务白名单不误报。
     */
    private Map<String, Object> buildWhitelistParam(Long sourceId) {
        Map<String, Object> param = new HashMap<String, Object>();
        if (sourceId == null) {
            param.put("hasWhitelist", false);
            return param;
        }
        List<NginxSourceWhitelistModel> ws = whitelistMapper.findEnabledBySourceIds(
                java.util.Collections.singletonList(sourceId));
        WhitelistFilter filter = WhitelistFilter.from(ws);
        if (filter.isEmpty()) {
            param.put("hasWhitelist", false);
            return param;
        }
        param.put("hasWhitelist", true);
        param.put("ipExact", filter.ipExact);
        param.put("ipLike", filter.getIpLike());
        param.put("uriExact", filter.uriExact);
        param.put("uriLike", filter.getUriLike());
        param.put("methodExact", filter.methodExact);
        return param;
    }

    private void createDefaultIfMissing(Long sourceId, Long tenantId, String type, long threshold,
                                        int windowMinutes, String level, int cooldownMin, long now) {
        if (ruleMapper.findBySourceIdAndType(sourceId, type) != null) {
            return;
        }
        NginxTrafficAlarmRuleModel rule = new NginxTrafficAlarmRuleModel();
        rule.setSourceId(sourceId);
        rule.setTenantId(tenantId);
        rule.setRuleType(type);
        rule.setEnabled(0);
        rule.setThreshold(threshold);
        rule.setWindowMinutes(windowMinutes);
        rule.setLevel(level);
        rule.setCooldownMinutes(cooldownMin);
        rule.setRequireAck("CRITICAL".equals(level) ? 1 : 0);
        rule.setCreateTime(now);
        rule.setUpdateTime(now);
        ruleMapper.insert(rule);
    }

    private void normalizeRule(NginxTrafficAlarmRuleModel rule, Long sourceId, Long tenantId, long now) {
        rule.setSourceId(sourceId);
        rule.setTenantId(tenantId);
        if (rule.getEnabled() == null) {
            rule.setEnabled(0);
        }
        if (rule.getWindowMinutes() == null || rule.getWindowMinutes() <= 0) {
            rule.setWindowMinutes(10);
        }
        if (rule.getThreshold() == null || rule.getThreshold() <= 0) {
            rule.setThreshold(100L);
        }
        if (rule.getLevel() == null || rule.getLevel().isEmpty()) {
            rule.setLevel("WARNING");
        }
        if (rule.getCooldownMinutes() == null || rule.getCooldownMinutes() <= 0) {
            rule.setCooldownMinutes(15);
        }
        if (rule.getRequireAck() == null) {
            rule.setRequireAck("CRITICAL".equals(rule.getLevel()) ? 1 : 0);
        }
        rule.setUpdateTime(now);
        if (rule.getCreateTime() == null) {
            rule.setCreateTime(now);
        }
    }

    private Map<String, Object> normalizeMap(Map<String, Object> raw) {
        Map<String, Object> out = new HashMap<String, Object>();
        if (raw == null) {
            return out;
        }
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            String key = e.getKey();
            if (key != null && key.equals(key.toUpperCase())) {
                out.put(key.toLowerCase(), e.getValue());
                if ("status4xx".equalsIgnoreCase(key) || key.toUpperCase().contains("4XX")) {
                    out.put("status4xx", e.getValue());
                }
                if ("status5xx".equalsIgnoreCase(key) || key.toUpperCase().contains("5XX")) {
                    out.put("status5xx", e.getValue());
                }
                if ("slowcount".equalsIgnoreCase(key)) {
                    out.put("slowCount", e.getValue());
                }
            } else {
                out.put(key, e.getValue());
            }
        }
        return out;
    }

    private long longVal(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) {
            for (Map.Entry<String, Object> e : map.entrySet()) {
                if (key.equalsIgnoreCase(e.getKey())) {
                    v = e.getValue();
                    break;
                }
            }
        }
        if (v instanceof Number) {
            return ((Number) v).longValue();
        }
        try {
            return v == null ? 0L : Long.parseLong(String.valueOf(v));
        } catch (Exception e) {
            return 0L;
        }
    }
}
