package com.ops.server.traffic.service;

import com.ops.common.model.NginxAccessSourceModel;
import com.ops.common.model.NginxMinuteStatModel;
import com.ops.server.mapper.NginxAccessSourceMapper;
import com.ops.server.mapper.NginxMinuteStatMapper;
import com.ops.server.mapper.NodeMapper;
import com.ops.server.traffic.service.NginxTrafficAlarmService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Nginx 流量监控：配置管理、数据入库与统计查询。
 */
@Service
public class NginxTrafficService {

    @Autowired
    private NginxAccessSourceMapper sourceMapper;
    @Autowired
    private NginxMinuteStatMapper minuteStatMapper;
    @Autowired
    private NodeMapper nodeMapper;
    @Autowired
    private NginxTrafficAlarmService nginxTrafficAlarmService;

    @Value("${easyops.nginx-traffic.minute-retain-days:7}")
    private int minuteRetainDays;

    public List<NginxAccessSourceModel> listSources() {
        return sourceMapper.findAll();
    }

    public NginxAccessSourceModel getSource(Long id) {
        return sourceMapper.findById(id);
    }

    public NginxAccessSourceModel saveSource(NginxAccessSourceModel model) {
        long now = System.currentTimeMillis();
        if (model.getLogFormat() == null || model.getLogFormat().isEmpty()) {
            model.setLogFormat("main");
        }
        if (model.getEnabled() == null) {
            model.setEnabled(1);
        }
        if (model.getSlowThresholdSec() == null) {
            model.setSlowThresholdSec(3D);
        }
        if (model.getMaxKeysPerMinute() == null) {
            model.setMaxKeysPerMinute(2000);
        }
        if (model.getId() == null) {
            model.setCreateTime(now);
            model.setUpdateTime(now);
            sourceMapper.insert(model);
            nginxTrafficAlarmService.ensureDefaultRules(model.getId());
        } else {
            model.setUpdateTime(now);
            sourceMapper.update(model);
        }
        return model;
    }

    public void deleteSource(Long id) {
        if (id != null) {
            nginxTrafficAlarmService.deleteBySourceId(id);
        }
        sourceMapper.deleteById(id);
    }

    public List<NginxAccessSourceModel> listAgentSources(Long nodeId) {
        if (nodeId == null) {
            return Collections.emptyList();
        }
        List<NginxAccessSourceModel> result = new ArrayList<NginxAccessSourceModel>();
        for (NginxAccessSourceModel source : sourceMapper.findAll()) {
            if (!nodeId.equals(source.getNodeId())) {
                continue;
            }
            if (source.getEnabled() == null || source.getEnabled() == 1) {
                result.add(source);
            }
        }
        return result;
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public void ingest(Long nodeId, Long sourceId, List<Map<String, Object>> rows) {
        NginxAccessSourceModel source = sourceMapper.findById(sourceId);
        if (source == null || !nodeId.equals(source.getNodeId())) {
            throw new IllegalArgumentException("日志源不存在或不属于当前节点");
        }
        if (rows == null || rows.isEmpty()) {
            sourceMapper.updateReportStatus(sourceId, System.currentTimeMillis(), null);
            return;
        }
        long now = System.currentTimeMillis();
        for (Map<String, Object> row : rows) {
            NginxMinuteStatModel stat = toStatModel(sourceId, row, now);
            if (stat == null) {
                continue;
            }
            if (minuteStatMapper.incrementStat(stat) == 0) {
                minuteStatMapper.insertStat(stat);
            }
        }
        sourceMapper.updateReportStatus(sourceId, now, null);
    }

    public Map<String, Object> overview(List<Long> sourceIds, Integer windowMinutes,
                                        Long startTime, Long endTime) {
        TimeRange range = resolveRange(windowMinutes, startTime, endTime);
        List<Long> ids = resolveSourceIds(sourceIds);
        Map<String, Object> data = normalizeMap(minuteStatMapper.overview(ids, range.start, range.end));
        data.put("startTime", range.start);
        data.put("endTime", range.end);
        data.put("retainDays", minuteRetainDays);
        long total = longVal(data, "totalRequests");
        data.put("totalRequests", total);
        long windowMs = range.end - range.start;
        double avgRps = windowMs <= 0 ? 0 : total * 1000D / windowMs;
        data.put("avgRps", Math.round(avgRps * 100D) / 100D);
        data.put("qps", Math.round(avgRps * 100D) / 100D);
        data.put("peakRps", computePeakRps(ids, range));
        return data;
    }

    public Map<String, Object> rankIp(List<Long> sourceIds, Integer windowMinutes,
                                      Long startTime, Long endTime, String keyword,
                                      Integer page, Integer pageSize) {
        TimeRange range = resolveRange(windowMinutes, startTime, endTime);
        List<Long> ids = resolveSourceIds(sourceIds);
        int p = normalizePage(page);
        int ps = normalizePageSize(pageSize);
        int offset = (p - 1) * ps;
        int total = minuteStatMapper.countByIp(ids, range.start, range.end, keyword);
        List<Map<String, Object>> list = normalizeRows(
                minuteStatMapper.sumByIp(ids, range.start, range.end, keyword, offset, ps));
        return buildRankPage(list, total, p, ps, "requestCount", "desc");
    }

    public Map<String, Object> rankUri(List<Long> sourceIds, Integer windowMinutes,
                                       Long startTime, Long endTime, String keyword,
                                       Integer page, Integer pageSize) {
        TimeRange range = resolveRange(windowMinutes, startTime, endTime);
        List<Long> ids = resolveSourceIds(sourceIds);
        int p = normalizePage(page);
        int ps = normalizePageSize(pageSize);
        int offset = (p - 1) * ps;
        int total = minuteStatMapper.countByUri(ids, range.start, range.end, keyword);
        List<Map<String, Object>> list = normalizeRows(
                minuteStatMapper.sumByUri(ids, range.start, range.end, keyword, offset, ps));
        return buildRankPage(list, total, p, ps, "requestCount", "desc");
    }

    public Map<String, Object> rankIpUri(List<Long> sourceIds, Integer windowMinutes,
                                           Long startTime, Long endTime,
                                           String clientIp, String uri,
                                           Integer page, Integer pageSize) {
        TimeRange range = resolveRange(windowMinutes, startTime, endTime);
        List<Long> ids = resolveSourceIds(sourceIds);
        int p = normalizePage(page);
        int ps = normalizePageSize(pageSize);
        int offset = (p - 1) * ps;
        int total = minuteStatMapper.countByIpUri(ids, range.start, range.end, clientIp, uri);
        List<Map<String, Object>> list = normalizeRows(
                minuteStatMapper.sumByIpUri(ids, range.start, range.end, clientIp, uri, offset, ps));
        return buildRankPage(list, total, p, ps, "requestCount", "desc");
    }

    public Map<String, Object> rankSlow(List<Long> sourceIds, Integer windowMinutes,
                                        Long startTime, Long endTime,
                                        Integer page, Integer pageSize) {
        TimeRange range = resolveRange(windowMinutes, startTime, endTime);
        List<Long> ids = resolveSourceIds(sourceIds);
        int p = normalizePage(page);
        int ps = normalizePageSize(pageSize);
        int offset = (p - 1) * ps;
        int total = minuteStatMapper.countSlowByUri(ids, range.start, range.end);
        List<Map<String, Object>> list = normalizeRows(
                minuteStatMapper.sumSlowByUri(ids, range.start, range.end, offset, ps));
        return buildRankPage(list, total, p, ps, "slowCount", "desc");
    }

    public Map<String, Object> trend(List<Long> sourceIds, Integer windowMinutes,
                                     Long startTime, Long endTime) {
        TimeRange range = resolveRange(windowMinutes, startTime, endTime);
        List<Map<String, Object>> rows = normalizeRows(
                minuteStatMapper.trendByMinute(resolveSourceIds(sourceIds), range.start, range.end));
        boolean byDay = range.end - range.start > 48L * 3600 * 1000;
        if (byDay) {
            rows = aggregateTrendByDay(rows);
        }
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("granularity", byDay ? "day" : "minute");
        result.put("startTime", range.start);
        result.put("endTime", range.end);
        result.put("points", rows);
        return result;
    }

    public Map<String, Object> todayOverview(List<Long> sourceIds) {
        TimeRange range = buildRange(null, true);
        Map<String, Object> data = normalizeMap(minuteStatMapper.overview(resolveSourceIds(sourceIds), range.start, range.end));
        data.put("totalRequests", longVal(data, "totalRequests"));
        return data;
    }

    public int cleanupExpiredMinuteStats() {
        long cutoff = System.currentTimeMillis() - minuteRetainDays * 24L * 3600L * 1000L;
        return minuteStatMapper.deleteBeforeBucketTime(cutoff);
    }

    private List<Long> resolveSourceIds(List<Long> sourceIds) {
        if (sourceIds != null && !sourceIds.isEmpty()) {
            return sourceIds;
        }
        List<Long> all = new ArrayList<Long>();
        for (NginxAccessSourceModel source : sourceMapper.findAll()) {
            if (source.getId() != null) {
                all.add(source.getId());
            }
        }
        return all.isEmpty() ? Collections.singletonList(-1L) : all;
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 20;
        }
        return Math.min(pageSize, 100);
    }

    private Map<String, Object> buildRankPage(List<Map<String, Object>> list, int total,
                                                int page, int pageSize, String sortBy, String sortOrder) {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        result.put("sortBy", sortBy);
        result.put("sortOrder", sortOrder);
        return result;
    }

    /** 区间内分钟桶峰值 RPS（请求/秒） */
    private double computePeakRps(List<Long> sourceIds, TimeRange range) {
        List<Map<String, Object>> rows = minuteStatMapper.trendByMinute(sourceIds, range.start, range.end);
        double peak = 0D;
        if (rows == null) {
            return 0D;
        }
        for (Map<String, Object> row : rows) {
            long count = longVal(normalizeMap(row), "requestCount");
            double rps = count / 60D;
            if (rps > peak) {
                peak = rps;
            }
        }
        return Math.round(peak * 100D) / 100D;
    }

    private TimeRange resolveRange(Integer windowMinutes, Long startTime, Long endTime) {
        long now = System.currentTimeMillis();
        if (startTime != null && endTime != null && endTime > startTime) {
            return new TimeRange(startTime, Math.min(endTime, now));
        }
        int minutes = windowMinutes == null || windowMinutes <= 0 ? 60 : windowMinutes;
        return new TimeRange(now - minutes * 60L * 1000L, now);
    }

    private List<Map<String, Object>> aggregateTrendByDay(List<Map<String, Object>> minuteRows) {
        if (minuteRows == null || minuteRows.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, long[]> dayBuckets = new TreeMap<Long, long[]>();
        for (Map<String, Object> row : minuteRows) {
            Long bucketTime = toLong(getIgnoreCase(row, "bucketTime"));
            if (bucketTime == null) {
                continue;
            }
            long dayStart = floorDayStart(bucketTime);
            long[] sums = dayBuckets.get(dayStart);
            if (sums == null) {
                sums = new long[3];
                dayBuckets.put(dayStart, sums);
            }
            sums[0] += longVal(row, "requestCount");
            sums[1] += longVal(row, "status4xx");
            sums[2] += longVal(row, "status5xx");
        }
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Map.Entry<Long, long[]> entry : dayBuckets.entrySet()) {
            Map<String, Object> item = new HashMap<String, Object>();
            item.put("bucketTime", entry.getKey());
            item.put("requestCount", entry.getValue()[0]);
            item.put("status4xx", entry.getValue()[1]);
            item.put("status5xx", entry.getValue()[2]);
            result.add(item);
        }
        return result;
    }

    private long floorDayStart(long timestampMs) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestampMs);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private TimeRange buildRange(Integer windowMinutes, boolean todayOnly) {
        long end = System.currentTimeMillis();
        long start;
        if (todayOnly) {
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            start = cal.getTimeInMillis();
        } else {
            int minutes = windowMinutes == null || windowMinutes <= 0 ? 10 : windowMinutes;
            start = end - minutes * 60L * 1000L;
        }
        return new TimeRange(start, end);
    }

    private NginxMinuteStatModel toStatModel(Long sourceId, Map<String, Object> row, long now) {
        if (row == null) {
            return null;
        }
        Long bucketTime = toLong(row.get("bucketTime"));
        String clientIp = row.get("clientIp") == null ? "-" : String.valueOf(row.get("clientIp"));
        String uri = row.get("uri") == null ? "-" : String.valueOf(row.get("uri"));
        String method = row.get("method") == null ? "GET" : String.valueOf(row.get("method"));
        if (bucketTime == null) {
            return null;
        }
        NginxMinuteStatModel stat = new NginxMinuteStatModel();
        stat.setSourceId(sourceId);
        stat.setBucketTime(bucketTime);
        stat.setClientIp(clientIp);
        stat.setUri(uri);
        stat.setMethod(method);
        stat.setRequestCount(toInt(row.get("requestCount")));
        stat.setSumRequestTimeMs(toLong(row.get("sumRequestTimeMs")));
        stat.setMaxRequestTimeMs(toLong(row.get("maxRequestTimeMs")));
        stat.setSumUpstreamTimeMs(toLong(row.get("sumUpstreamTimeMs")));
        stat.setStatus2xx(toInt(row.get("status2xx")));
        stat.setStatus4xx(toInt(row.get("status4xx")));
        stat.setStatus5xx(toInt(row.get("status5xx")));
        stat.setSlowCount(toInt(row.get("slowCount")));
        stat.setCreateTime(now);
        return stat;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private int toInt(Object value) {
        Long l = toLong(value);
        return l == null ? 0 : l.intValue();
    }

    private long longVal(Map<String, Object> map, String key) {
        Object v = getIgnoreCase(map, key);
        Long l = toLong(v);
        return l == null ? 0L : l.longValue();
    }

    private Object getIgnoreCase(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        if (map.containsKey(key)) {
            return map.get(key);
        }
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (key.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private Map<String, Object> normalizeMap(Map<String, Object> raw) {
        Map<String, Object> out = new HashMap<String, Object>();
        if (raw == null) {
            return out;
        }
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            out.put(normalizeKey(entry.getKey()), entry.getValue());
        }
        return out;
    }

    private List<Map<String, Object>> normalizeRows(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : rows) {
            result.add(normalizeMap(row));
        }
        return result;
    }

    /** H2/MyBatis 可能返回 TOTALREQUESTS，统一转为 camelCase */
    private String normalizeKey(String key) {
        if (key == null || key.isEmpty()) {
            return key;
        }
        if (key.contains("_")) {
            StringBuilder sb = new StringBuilder();
            boolean upperNext = false;
            for (int i = 0; i < key.length(); i++) {
                char c = key.charAt(i);
                if (c == '_') {
                    upperNext = true;
                    continue;
                }
                if (sb.length() == 0) {
                    sb.append(Character.toLowerCase(c));
                } else if (upperNext) {
                    sb.append(Character.toUpperCase(c));
                    upperNext = false;
                } else {
                    sb.append(Character.toLowerCase(c));
                }
            }
            return sb.toString();
        }
        String lower = key.toLowerCase();
        if ("totalrequests".equals(lower)) {
            return "totalRequests";
        }
        if ("status4xx".equals(lower)) {
            return "status4xx";
        }
        if ("status5xx".equals(lower)) {
            return "status5xx";
        }
        if ("slowcount".equals(lower)) {
            return "slowCount";
        }
        if ("requestcount".equals(lower)) {
            return "requestCount";
        }
        if ("clientip".equals(lower)) {
            return "clientIp";
        }
        if ("avgrequesttimems".equals(lower)) {
            return "avgRequestTimeMs";
        }
        if ("maxrequesttimems".equals(lower)) {
            return "maxRequestTimeMs";
        }
        if ("buckettime".equals(lower)) {
            return "bucketTime";
        }
        if (key.equals(key.toUpperCase())) {
            return lower;
        }
        return key;
    }

    private static class TimeRange {
        final long start;
        final long end;

        TimeRange(long start, long end) {
            this.start = start;
            this.end = end;
        }
    }
}
