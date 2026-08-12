package com.ops.server.traffic.service;

import com.ops.common.model.NginxAccessSourceModel;
import com.ops.common.model.NginxMinuteStatModel;
import com.ops.common.model.NginxSourceWhitelistModel;
import com.ops.server.mapper.NginxAccessSourceMapper;
import com.ops.server.mapper.NginxDimensionStatMapper;
import com.ops.server.mapper.NginxIpStatMapper;
import com.ops.server.mapper.NginxMinuteStatMapper;
import com.ops.server.mapper.NginxSourceWhitelistMapper;
import com.ops.server.mapper.NodeMapper;
import com.ops.server.util.SecurityContext;
import com.ops.server.traffic.service.NginxTrafficAlarmService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
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
    private NginxDimensionStatMapper dimensionMapper;
    @Autowired
    private NginxIpStatMapper ipStatMapper;
    @Autowired
    private NginxSourceWhitelistMapper whitelistMapper;
    @Autowired
    private NginxSourceWhitelistService whitelistService;
    @Autowired
    private NodeMapper nodeMapper;
    @Autowired
    private NginxTrafficAlarmService nginxTrafficAlarmService;
    @Autowired
    private SecurityContext securityContext;

    @Value("${easyops.nginx-traffic.minute-retain-days:7}")
    private int minuteRetainDays;

    public List<NginxAccessSourceModel> listSources() {
        return filterAuthorizedSources(sourceMapper.findAll());
    }

    public NginxAccessSourceModel getSource(Long id) {
        NginxAccessSourceModel source = sourceMapper.findById(id);
        assertSourceAccess(source);
        return source;
    }

    public NginxAccessSourceModel saveSource(NginxAccessSourceModel model) {
        if (model.getId() != null) assertSourceAccess(sourceMapper.findById(model.getId()));
        if (model.getNodeId() == null) throw new IllegalArgumentException("日志源必须绑定节点");
        assertNodeAccess(model.getNodeId());
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
            model.setMaxKeysPerMinute(500);
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
        assertSourceAccess(sourceMapper.findById(id));
        if (id != null) {
            nginxTrafficAlarmService.deleteBySourceId(id);
            whitelistService.deleteBySource(id);
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
    public void ingest(Long nodeId, Long sourceId, Map<String, Object> payload) {
        NginxAccessSourceModel source = sourceMapper.findById(sourceId);
        if (source == null || !nodeId.equals(source.getNodeId())) {
            throw new IllegalArgumentException("日志源不存在或不属于当前节点");
        }
        long now = System.currentTimeMillis();
        List<Map<String, Object>> rows = (List<Map<String, Object>>) payload.get("rows");
        List<Map<String, Object>> ipRows = (List<Map<String, Object>>) payload.get("ipRows");
        List<Map<String, Object>> uaRows = (List<Map<String, Object>>) payload.get("uaRows");
        List<Map<String, Object>> refererRows = (List<Map<String, Object>>) payload.get("refererRows");
        List<Map<String, Object>> samples = (List<Map<String, Object>>) payload.get("samples");
        if ((rows == null || rows.isEmpty())
                && (ipRows == null || ipRows.isEmpty())
                && (uaRows == null || uaRows.isEmpty())
                && (refererRows == null || refererRows.isEmpty())
                && (samples == null || samples.isEmpty())) {
            sourceMapper.updateReportStatus(sourceId, now, null);
            return;
        }
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                NginxMinuteStatModel stat = toStatModel(sourceId, row, now);
                if (stat == null) {
                    continue;
                }
                if (minuteStatMapper.incrementStat(stat) == 0) {
                    try {
                        minuteStatMapper.insertStat(stat);
                    } catch (DuplicateKeyException e) {
                        // 并发 insert 冲突，回退到 increment 补偿
                        minuteStatMapper.incrementStat(stat);
                    }
                }
            }
        }
        // IP 维度统计（独立表，支持 rank/ip 查询）
        if (ipRows != null && !ipRows.isEmpty()) {
            for (Map<String, Object> r : ipRows) {
                String ip = r.get("clientIp") == null ? "" : String.valueOf(r.get("clientIp"));
                int rc = toInt(r.get("requestCount"));
                long srt = toLong(r.get("sumRequestTimeMs")) == null ? 0L : toLong(r.get("sumRequestTimeMs"));
                long mrt = toLong(r.get("maxRequestTimeMs")) == null ? 0L : toLong(r.get("maxRequestTimeMs"));
                int sc = toInt(r.get("slowCount"));
                int s5 = toInt(r.get("status5xx"));
                Long bt = toLong(r.get("bucketTime"));
                if (bt == null) continue;
                safeUpsertIp(sourceId, bt, ip, rc, srt, mrt, sc, s5, r, now);
            }
        }
        if (uaRows != null && !uaRows.isEmpty()) {
            for (Map<String, Object> r : uaRows) {
                String userAgent = r.get("userAgent") == null ? "" : String.valueOf(r.get("userAgent"));
                int rc = toInt(r.get("requestCount"));
                long srt = toLong(r.get("sumRequestTimeMs")) == null ? 0L : toLong(r.get("sumRequestTimeMs"));
                long mrt = toLong(r.get("maxRequestTimeMs")) == null ? 0L : toLong(r.get("maxRequestTimeMs"));
                int sc = toInt(r.get("slowCount"));
                int s5 = toInt(r.get("status5xx"));
                Long bt = toLong(r.get("bucketTime"));
                if (bt == null) continue;
                safeUpsertUa(sourceId, bt, userAgent, rc, srt, mrt, sc, s5, r, now);
            }
        }
        if (refererRows != null && !refererRows.isEmpty()) {
            for (Map<String, Object> r : refererRows) {
                String ref = r.get("referer") == null ? "" : String.valueOf(r.get("referer"));
                int rc = toInt(r.get("requestCount"));
                long srt = toLong(r.get("sumRequestTimeMs")) == null ? 0L : toLong(r.get("sumRequestTimeMs"));
                long mrt = toLong(r.get("maxRequestTimeMs")) == null ? 0L : toLong(r.get("maxRequestTimeMs"));
                int sc = toInt(r.get("slowCount"));
                int s5 = toInt(r.get("status5xx"));
                Long bt = toLong(r.get("bucketTime"));
                if (bt == null) continue;
                safeUpsertReferer(sourceId, bt, ref, rc, srt, mrt, sc, s5, r, now);
            }
        }
        if (samples != null && !samples.isEmpty()) {
            List<Map<String, Object>> batch = new ArrayList<Map<String, Object>>(samples.size());
            for (Map<String, Object> s : samples) {
                Map<String, Object> m = new HashMap<String, Object>(s);
                m.put("sourceId", sourceId);
                m.put("createTime", now);
                batch.add(m);
            }
            dimensionMapper.batchInsertSample(batch);
        }
        sourceMapper.updateReportStatus(sourceId, now, null);
    }

    public Map<String, Object> overview(List<Long> sourceIds, Integer windowMinutes,
                                        Long startTime, Long endTime) {
        TimeRange range = resolveRange(windowMinutes, startTime, endTime);
        List<Long> ids = resolveSourceIds(sourceIds);
        Map<String, Object> wl = buildWhitelistParam(ids);
        Map<String, Object> data = normalizeMap(minuteStatMapper.overview(ids, range.start, range.end, wl));
        data.put("startTime", range.start);
        data.put("endTime", range.end);
        data.put("retainDays", minuteRetainDays);
        long total = longVal(data, "totalRequests");
        data.put("totalRequests", total);
        long windowMsComputed = range.end - range.start;
        long sumRt = longVal(data, "sumRequestTimeMs");
        long maxRt = longVal(data, "maxRequestTimeMs");
        data.put("avgRequestTimeMs", total <= 0 ? 0 : Math.round(sumRt * 100D / total) / 100D);
        data.put("maxRequestTimeMs", maxRt);
        long sumUp = longVal(data, "sumUpstreamTimeMs");
        data.put("avgUpstreamTimeMs", total <= 0 ? 0 : Math.round(sumUp * 100D / total) / 100D);
        data.put("sumUpstreamConnectTimeMs", longVal(data, "sumUpstreamConnectTimeMs"));
        data.put("sumUpstreamHeaderTimeMs", longVal(data, "sumUpstreamHeaderTimeMs"));
        data.put("upstream5xx", longVal(data, "upstream5xx"));
        long cacheHit = longVal(data, "cacheHit");
        long cacheMiss = longVal(data, "cacheMiss");
        long cacheTotal = cacheHit + cacheMiss;
        data.put("cacheHit", cacheHit);
        data.put("cacheMiss", cacheMiss);
        data.put("cacheHitRate",
                cacheTotal <= 0 ? 0 : Math.round(cacheHit * 10000D / cacheTotal) / 100D);
        long sumBody = longVal(data, "sumBodyBytes");
        data.put("sumBodyBytes", sumBody);
        data.put("bandwidthMbps",
                windowMsComputed <= 0 ? 0 : Math.round(sumBody * 8D / (windowMsComputed / 1000D) / 1000000D * 100D) / 100D);
        data.put("httpsCount", longVal(data, "httpsCount"));
        double avgRps = windowMsComputed <= 0 ? 0 : total * 1000D / windowMsComputed;
        data.put("avgRps", Math.round(avgRps * 100D) / 100D);
        data.put("qps", Math.round(avgRps * 100D) / 100D);
        data.put("peakRps", computePeakRps(ids, range, wl));
        return data;
    }

    public Map<String, Object> rankIp(List<Long> sourceIds, Integer windowMinutes,
                                      Long startTime, Long endTime, String keyword,
                                      Integer page, Integer pageSize, String sort) {
        TimeRange range = resolveRange(windowMinutes, startTime, endTime);
        List<Long> ids = resolveSourceIds(sourceIds);
        Map<String, Object> wl = buildWhitelistParam(ids);
        int p = normalizePage(page);
        int ps = normalizePageSize(pageSize);
        int offset = (p - 1) * ps;
        int total = ipStatMapper.countByIp(ids, range.start, range.end, keyword, wl);
        List<Map<String, Object>> list = normalizeRows(
                ipStatMapper.sumByIp(ids, range.start, range.end, keyword, offset, ps, sort, wl));
        return buildRankPage(list, total, p, ps, "requestCount", "desc");
    }

    public Map<String, Object> rankUri(List<Long> sourceIds, Integer windowMinutes,
                                       Long startTime, Long endTime, String keyword,
                                       Integer page, Integer pageSize, String sort) {
        TimeRange range = resolveRange(windowMinutes, startTime, endTime);
        List<Long> ids = resolveSourceIds(sourceIds);
        Map<String, Object> wl = buildWhitelistParam(ids);
        int p = normalizePage(page);
        int ps = normalizePageSize(pageSize);
        int offset = (p - 1) * ps;
        int total = minuteStatMapper.countByUri(ids, range.start, range.end, keyword, wl);
        List<Map<String, Object>> list = normalizeRows(
                minuteStatMapper.sumByUri(ids, range.start, range.end, keyword, offset, ps, sort, wl));
        return buildRankPage(list, total, p, ps, "requestCount", "desc");
    }

    public Map<String, Object> rankIpUri(List<Long> sourceIds, Integer windowMinutes,
                                           Long startTime, Long endTime,
                                           String clientIp, String uri,
                                           Integer page, Integer pageSize, String sort) {
        TimeRange range = resolveRange(windowMinutes, startTime, endTime);
        List<Long> ids = resolveSourceIds(sourceIds);
        Map<String, Object> wl = buildWhitelistParam(ids);
        int p = normalizePage(page);
        int ps = normalizePageSize(pageSize);
        int offset = (p - 1) * ps;
        int total = ipStatMapper.countByIpUri(ids, range.start, range.end, clientIp, uri, wl);
        List<Map<String, Object>> list = normalizeRows(
                ipStatMapper.sumByIpUri(ids, range.start, range.end, clientIp, uri, offset, ps, sort, wl));
        return buildRankPage(list, total, p, ps, "requestCount", "desc");
    }

    public Map<String, Object> rankSlow(List<Long> sourceIds, Integer windowMinutes,
                                        Long startTime, Long endTime,
                                        Integer page, Integer pageSize, String sort) {
        TimeRange range = resolveRange(windowMinutes, startTime, endTime);
        List<Long> ids = resolveSourceIds(sourceIds);
        Map<String, Object> wl = buildWhitelistParam(ids);
        int p = normalizePage(page);
        int ps = normalizePageSize(pageSize);
        int offset = (p - 1) * ps;
        int total = minuteStatMapper.countSlowByUri(ids, range.start, range.end, wl);
        List<Map<String, Object>> list = normalizeRows(
                minuteStatMapper.sumSlowByUri(ids, range.start, range.end, offset, ps, sort, wl));
        return buildRankPage(list, total, p, ps, "slowCount", "desc");
    }

    public Map<String, Object> rankMethod(List<Long> sourceIds, Integer windowMinutes,
                                          Long startTime, Long endTime,
                                          Integer page, Integer pageSize, String sort) {
        TimeRange range = resolveRange(windowMinutes, startTime, endTime);
        List<Long> ids = resolveSourceIds(sourceIds);
        Map<String, Object> wl = buildWhitelistParam(ids);
        int p = normalizePage(page);
        int ps = normalizePageSize(pageSize);
        int offset = (p - 1) * ps;
        int total = minuteStatMapper.countByMethod(ids, range.start, range.end, wl);
        List<Map<String, Object>> list = normalizeRows(
                minuteStatMapper.sumByMethod(ids, range.start, range.end, offset, ps, sort, wl));
        return buildRankPage(list, total, p, ps, "requestCount", "desc");
    }

    public Map<String, Object> rankUa(List<Long> sourceIds, Integer windowMinutes,
                                      Long startTime, Long endTime, String keyword,
                                      Integer page, Integer pageSize, String sort) {
        TimeRange range = resolveRange(windowMinutes, startTime, endTime);
        List<Long> ids = resolveSourceIds(sourceIds);
        int p = normalizePage(page);
        int ps = normalizePageSize(pageSize);
        int offset = (p - 1) * ps;
        int total = dimensionMapper.countByUa(ids, range.start, range.end, keyword);
        List<Map<String, Object>> list = normalizeRows(
                dimensionMapper.sumByUa(ids, range.start, range.end, keyword, offset, ps, sort));
        return buildRankPage(list, total, p, ps, "requestCount", "desc");
    }

    public Map<String, Object> rankReferer(List<Long> sourceIds, Integer windowMinutes,
                                           Long startTime, Long endTime, String keyword,
                                           Integer page, Integer pageSize, String sort) {
        TimeRange range = resolveRange(windowMinutes, startTime, endTime);
        List<Long> ids = resolveSourceIds(sourceIds);
        int p = normalizePage(page);
        int ps = normalizePageSize(pageSize);
        int offset = (p - 1) * ps;
        int total = dimensionMapper.countByReferer(ids, range.start, range.end, keyword);
        List<Map<String, Object>> list = normalizeRows(
                dimensionMapper.sumByReferer(ids, range.start, range.end, keyword, offset, ps, sort));
        return buildRankPage(list, total, p, ps, "requestCount", "desc");
    }

    /**
     * 原始样本：瞬时耗时 + 百分位(p50/p95/p99)。
     */
    public Map<String, Object> latencySamples(List<Long> sourceIds, Integer windowMinutes,
                                              Long startTime, Long endTime,
                                              Integer page, Integer pageSize) {
        TimeRange range = resolveRange(windowMinutes, startTime, endTime);
        List<Long> ids = resolveSourceIds(sourceIds);
        int p = normalizePage(page);
        int ps = normalizePageSize(pageSize);
        int offset = (p - 1) * ps;
        List<Map<String, Object>> list = normalizeRows(
                dimensionMapper.listSamples(ids, range.start, range.end, offset, ps));
        // 计算百分位需要全量样本（不限页），再返回当前页明细
        List<Map<String, Object>> all = normalizeRows(
                dimensionMapper.listSamples(ids, range.start, range.end, 0, 100000));
        double[] pct = computePercentiles(all);
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("list", list);
        result.put("total", all.size());
        result.put("p50", pct[0]);
        result.put("p95", pct[1]);
        result.put("p99", pct[2]);
        result.put("max", pct[3]);
        result.put("page", p);
        result.put("pageSize", ps);
        return result;
    }

    private double[] computePercentiles(List<Map<String, Object>> samples) {
        List<Long> vals = new ArrayList<Long>();
        double max = 0;
        for (Map<String, Object> s : samples) {
            long rt = longVal(s, "requestTimeMs");
            vals.add(rt);
            if (rt > max) {
                max = rt;
            }
        }
        double[] r = new double[]{0, 0, 0, max};
        if (vals.isEmpty()) {
            return r;
        }
        Collections.sort(vals);
        int n = vals.size();
        r[0] = percentile(vals, 50);
        r[1] = percentile(vals, 95);
        r[2] = percentile(vals, 99);
        return r;
    }

    private double percentile(List<Long> sorted, int p) {
        if (sorted.isEmpty()) {
            return 0;
        }
        double idx = (p / 100D) * (sorted.size() - 1);
        int lo = (int) Math.floor(idx);
        int hi = (int) Math.ceil(idx);
        if (lo == hi) {
            return sorted.get(lo);
        }
        double w = idx - lo;
        return sorted.get(lo) * (1 - w) + sorted.get(hi) * w;
    }

    public Map<String, Object> trend(List<Long> sourceIds, Integer windowMinutes,
                                     Long startTime, Long endTime) {
        TimeRange range = resolveRange(windowMinutes, startTime, endTime);
        Map<String, Object> wl = buildWhitelistParam(resolveSourceIds(sourceIds));
        List<Map<String, Object>> rows = normalizeRows(
                minuteStatMapper.trendByMinute(resolveSourceIds(sourceIds), range.start, range.end, wl));
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
        List<Long> ids = resolveSourceIds(sourceIds);
        Map<String, Object> wl = buildWhitelistParam(ids);
        Map<String, Object> data = normalizeMap(minuteStatMapper.overview(ids, range.start, range.end, wl));
        data.put("totalRequests", longVal(data, "totalRequests"));
        return data;
    }

    public int cleanupExpiredMinuteStats() {
        long cutoff = System.currentTimeMillis() - minuteRetainDays * 24L * 3600L * 1000L;
        return minuteStatMapper.deleteBeforeBucketTime(cutoff);
    }

    private List<Long> resolveSourceIds(List<Long> sourceIds) {
        if (sourceIds != null && !sourceIds.isEmpty()) {
            List<Long> authorized = new ArrayList<Long>();
            for (Long sourceId : sourceIds) {
                NginxAccessSourceModel source = sourceMapper.findById(sourceId);
                if (source != null && isSourceAuthorized(source)) authorized.add(sourceId);
            }
            return authorized.isEmpty() ? Collections.singletonList(-1L) : authorized;
        }
        List<Long> all = new ArrayList<Long>();
        for (NginxAccessSourceModel source : filterAuthorizedSources(sourceMapper.findAll())) {
            if (source.getId() != null) {
                all.add(source.getId());
            }
        }
        return all.isEmpty() ? Collections.singletonList(-1L) : all;
    }

    public void assertSourceAccess(Long sourceId) {
        assertSourceAccess(sourceMapper.findById(sourceId));
    }

    private void assertSourceAccess(NginxAccessSourceModel source) {
        if (source == null) throw new IllegalArgumentException("日志源不存在");
        if (!isSourceAuthorized(source)) throw new IllegalArgumentException("无权访问该日志源");
    }

    private void assertNodeAccess(Long nodeId) {
        if (securityContext.getCurrentTenantId() == null || securityContext.isPlatformAdmin()) return;
        com.ops.common.model.NodeModel node = nodeMapper.findById(nodeId);
        if (node == null || !securityContext.getCurrentTenantId().equals(node.getTenantId())) {
            throw new IllegalArgumentException("无权访问该节点");
        }
    }

    private boolean isSourceAuthorized(NginxAccessSourceModel source) {
        if (securityContext.getCurrentTenantId() == null || securityContext.isPlatformAdmin()) return true;
        com.ops.common.model.NodeModel node = nodeMapper.findById(source.getNodeId());
        return node != null && securityContext.getCurrentTenantId().equals(node.getTenantId());
    }

    private List<NginxAccessSourceModel> filterAuthorizedSources(List<NginxAccessSourceModel> sources) {
        if (securityContext.getCurrentTenantId() == null || securityContext.isPlatformAdmin()) return sources;
        List<NginxAccessSourceModel> result = new ArrayList<NginxAccessSourceModel>();
        if (sources != null) for (NginxAccessSourceModel source : sources) {
            if (isSourceAuthorized(source)) result.add(source);
        }
        return result;
    }

    /**
     * 构建查询侧白名单 SQL 参数：把启用的白名单按维度拆成 exact / like 两类。
     * like 列表已带好通配符（前缀用 v%，包含用 %v%），供 MyBatis 直接拼 NOT LIKE。
     * 空白名单返回空 map，Mapper 端据此跳过过滤条件。
     */
    private Map<String, Object> buildWhitelistParam(List<Long> sourceIds) {
        Map<String, Object> param = new HashMap<String, Object>();
        if (sourceIds == null || sourceIds.isEmpty()) {
            param.put("hasWhitelist", false);
            return param;
        }
        List<NginxSourceWhitelistModel> ws = whitelistMapper.findEnabledBySourceIds(sourceIds);
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
    private double computePeakRps(List<Long> sourceIds, TimeRange range, Map<String, Object> wl) {
        List<Map<String, Object>> rows = minuteStatMapper.trendByMinute(sourceIds, range.start, range.end, wl);
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
        stat.setSumUpstreamConnectTimeMs(toLong(row.get("sumUpstreamConnectTimeMs")));
        stat.setSumUpstreamHeaderTimeMs(toLong(row.get("sumUpstreamHeaderTimeMs")));
        stat.setSumBodyBytes(toLong(row.get("sumBodyBytes")));
        stat.setStatus2xx(toInt(row.get("status2xx")));
        stat.setStatus4xx(toInt(row.get("status4xx")));
        stat.setStatus5xx(toInt(row.get("status5xx")));
        stat.setUpstream5xx(toInt(row.get("upstream5xx")));
        stat.setCacheHitCount(toInt(row.get("cacheHit")));
        stat.setCacheMissCount(toInt(row.get("cacheMiss")));
        stat.setHttpsCount(toInt(row.get("httpsCount")));
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

    /** 截断字符串到数据库列长度限制（VARCHAR(500)），避免唯一索引截断导致数据错乱 */
    private String truncate(String s) {
        if (s == null) return "";
        return s.length() > 480 ? s.substring(0, 480) : s;
    }

    /**
     * 安全 UPSERT：先尝试 UPDATE，失败则 INSERT，唯一索引冲突时回退到 UPDATE。
     * 解决并发上报导致的 DuplicateKeyException 事务回滚问题。
     */
    private void safeUpsertIp(Long sourceId, Long bucketTime, String clientIp,
                              int rc, long srt, long mrt, int sc, int s5,
                              Map<String, Object> row, long now) {
        if (ipStatMapper.incrementIpStat(sourceId, bucketTime, clientIp, rc, srt, mrt, sc, s5) > 0) {
            return;
        }
        Map<String, Object> m = new HashMap<String, Object>(row);
        m.put("sourceId", sourceId);
        m.put("createTime", now);
        try {
            ipStatMapper.insertIpStat(m);
        } catch (DuplicateKeyException e) {
            // 并发 insert 冲突，回退到 increment 补偿
            ipStatMapper.incrementIpStat(sourceId, bucketTime, clientIp, rc, srt, mrt, sc, s5);
        }
    }

    private void safeUpsertUa(Long sourceId, Long bucketTime, String userAgent,
                              int rc, long srt, long mrt, int sc, int s5,
                              Map<String, Object> row, long now) {
        String uaTrunc = truncate(userAgent);
        if (dimensionMapper.incrementUaStat(sourceId, bucketTime, uaTrunc, rc, srt, mrt, sc, s5) > 0) {
            return;
        }
        Map<String, Object> m = new HashMap<String, Object>(row);
        m.put("sourceId", sourceId);
        m.put("createTime", now);
        m.put("userAgent", uaTrunc);
        try {
            dimensionMapper.insertUaStat(m);
        } catch (DuplicateKeyException e) {
            dimensionMapper.incrementUaStat(sourceId, bucketTime, uaTrunc, rc, srt, mrt, sc, s5);
        }
    }

    private void safeUpsertReferer(Long sourceId, Long bucketTime, String referer,
                                   int rc, long srt, long mrt, int sc, int s5,
                                   Map<String, Object> row, long now) {
        String refTrunc = truncate(referer);
        if (dimensionMapper.incrementRefererStat(sourceId, bucketTime, refTrunc, rc, srt, mrt, sc, s5) > 0) {
            return;
        }
        Map<String, Object> m = new HashMap<String, Object>(row);
        m.put("sourceId", sourceId);
        m.put("createTime", now);
        m.put("referer", refTrunc);
        try {
            dimensionMapper.insertRefererStat(m);
        } catch (DuplicateKeyException e) {
            dimensionMapper.incrementRefererStat(sourceId, bucketTime, refTrunc, rc, srt, mrt, sc, s5);
        }
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
