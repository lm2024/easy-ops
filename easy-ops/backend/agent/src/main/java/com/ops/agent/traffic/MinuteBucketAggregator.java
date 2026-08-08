package com.ops.agent.traffic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 按分钟桶在内存中聚合访问统计。
 * 主维度：(client_ip, uri, method)；扩展：UA / Referer 独立聚合（Top-N 封顶）+ 慢/错/抽样原始样本。
 */
public class MinuteBucketAggregator {

    static class BucketKey {
        final String clientIp;
        final String uri;
        final String method;

        BucketKey(String clientIp, String uri, String method) {
            this.clientIp = clientIp;
            this.uri = uri;
            this.method = method;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof BucketKey)) {
                return false;
            }
            BucketKey other = (BucketKey) o;
            return clientIp.equals(other.clientIp) && uri.equals(other.uri) && method.equals(other.method);
        }

        @Override
        public int hashCode() {
            int h = 17;
            h = 31 * h + clientIp.hashCode();
            h = 31 * h + uri.hashCode();
            h = 31 * h + method.hashCode();
            return h;
        }
    }

    static class BucketValue {
        int requestCount;
        long sumRequestTimeMs;
        long maxRequestTimeMs;
        long sumUpstreamTimeMs;
        long sumUpstreamConnectTimeMs;
        long sumUpstreamHeaderTimeMs;
        long sumBodyBytes;
        int status2xx;
        int status4xx;
        int status5xx;
        int upstream5xx;
        int cacheHit;
        int cacheMiss;
        int httpsCount;
        int slowCount;
    }

    static class DimValue {
        int requestCount;
        long sumRequestTimeMs;
        long maxRequestTimeMs;
        int slowCount;
        int status5xx;
    }

    private final int maxKeys;
    private final double slowThresholdSec;
    private final int maxSamples;
    private long currentMinuteStart = -1L;
    private final Map<BucketKey, BucketValue> buckets = new HashMap<BucketKey, BucketValue>();
    private final Map<String, DimValue> uaBuckets = new HashMap<String, DimValue>();
    private final Map<String, DimValue> refererBuckets = new HashMap<String, DimValue>();
    private final List<Map<String, Object>> samples = new ArrayList<Map<String, Object>>();
    private int overflowCount;
    private int sampleCounter;

    public MinuteBucketAggregator(int maxKeys, double slowThresholdSec) {
        this.maxKeys = Math.max(100, maxKeys);
        this.slowThresholdSec = slowThresholdSec <= 0 ? 3D : slowThresholdSec;
        this.maxSamples = Math.max(50, maxKeys / 5);
    }

    /**
     * 累加一条解析后的访问记录；若跨分钟则返回上一分钟待上报数据。
     */
    public synchronized List<Map<String, Object>> add(NginxLogParser.ParsedLine line) {
        long minuteStart = floorMinute(line.timestampMs);
        if (currentMinuteStart < 0) {
            currentMinuteStart = minuteStart;
        }
        List<Map<String, Object>> rotated = null;
        if (minuteStart != currentMinuteStart) {
            rotated = drainCurrent();
            currentMinuteStart = minuteStart;
        }
        addToBucket(line);
        return rotated;
    }

    private void addToBucket(NginxLogParser.ParsedLine line) {
        String method = line.method == null || line.method.isEmpty() ? "GET" : line.method;
        BucketKey key = new BucketKey(line.clientIp, line.uri, method);
        if (!buckets.containsKey(key) && buckets.size() >= maxKeys) {
            overflowCount++;
            return;
        }
        BucketValue value = buckets.get(key);
        if (value == null) {
            value = new BucketValue();
            buckets.put(key, value);
        }
        value.requestCount++;
        value.sumRequestTimeMs += line.requestTimeMs;
        if (line.requestTimeMs > value.maxRequestTimeMs) {
            value.maxRequestTimeMs = line.requestTimeMs;
        }
        value.sumUpstreamTimeMs += line.upstreamTimeMs;
        value.sumUpstreamConnectTimeMs += line.upstreamConnectTimeMs;
        value.sumUpstreamHeaderTimeMs += line.upstreamHeaderTimeMs;
        value.sumBodyBytes += line.bodyBytes;
        int status = line.status;
        if (status >= 200 && status < 300) {
            value.status2xx++;
        } else if (status >= 400 && status < 500) {
            value.status4xx++;
        } else if (status >= 500) {
            value.status5xx++;
        }
        if (line.upstreamStatus >= 500) {
            value.upstream5xx++;
        }
        if ("HIT".equals(line.cacheStatus)) {
            value.cacheHit++;
        } else if (line.cacheStatus != null && !line.cacheStatus.isEmpty() && !"-".equals(line.cacheStatus)) {
            value.cacheMiss++;
        }
        if ("https".equalsIgnoreCase(line.scheme)) {
            value.httpsCount++;
        }
        if (line.requestTimeMs >= slowThresholdSec * 1000D) {
            value.slowCount++;
        }

        // UA 维度
        if (line.userAgent != null && !line.userAgent.isEmpty() && !"-".equals(line.userAgent)) {
            DimValue dv = uaBuckets.get(line.userAgent);
            if (dv == null) {
                if (uaBuckets.size() >= maxKeys) {
                    // 超出 Top-N 上限，归入 __OTHER__
                    dv = uaBuckets.get("__OTHER__");
                    if (dv == null) {
                        dv = new DimValue();
                        uaBuckets.put("__OTHER__", dv);
                    }
                } else {
                    dv = new DimValue();
                    uaBuckets.put(line.userAgent, dv);
                }
            }
            dv.requestCount++;
            dv.sumRequestTimeMs += line.requestTimeMs;
            if (line.requestTimeMs > dv.maxRequestTimeMs) {
                dv.maxRequestTimeMs = line.requestTimeMs;
            }
            if (line.requestTimeMs >= slowThresholdSec * 1000D) {
                dv.slowCount++;
            }
            if (status >= 500) {
                dv.status5xx++;
            }
        }

        // Referer 维度
        if (line.referer != null && !line.referer.isEmpty() && !"-".equals(line.referer)) {
            DimValue dv = refererBuckets.get(line.referer);
            if (dv == null) {
                if (refererBuckets.size() >= maxKeys) {
                    dv = refererBuckets.get("__OTHER__");
                    if (dv == null) {
                        dv = new DimValue();
                        refererBuckets.put("__OTHER__", dv);
                    }
                } else {
                    dv = new DimValue();
                    refererBuckets.put(line.referer, dv);
                }
            }
            dv.requestCount++;
            dv.sumRequestTimeMs += line.requestTimeMs;
            if (line.requestTimeMs > dv.maxRequestTimeMs) {
                dv.maxRequestTimeMs = line.requestTimeMs;
            }
            if (line.requestTimeMs >= slowThresholdSec * 1000D) {
                dv.slowCount++;
            }
            if (status >= 500) {
                dv.status5xx++;
            }
        }

        // 原始样本：慢请求 / 错误 / 按比例抽样（封顶）
        boolean keep = line.requestTimeMs >= slowThresholdSec * 1000D
                || status >= 400
                || (sampleCounter++ % 10 == 0);
        if (keep && samples.size() < maxSamples) {
            Map<String, Object> s = new HashMap<String, Object>();
            s.put("ts", line.msec > 0 ? line.msec : line.timestampMs);
            s.put("clientIp", line.clientIp);
            s.put("uri", line.uri);
            s.put("method", method);
            s.put("requestTimeMs", line.requestTimeMs);
            s.put("upstreamTimeMs", line.upstreamTimeMs);
            s.put("status", status);
            s.put("userAgent", line.userAgent);
            s.put("referer", line.referer);
            samples.add(s);
        }
    }

    /**
     * 若进入新分钟，返回上一分钟聚合结果并切换桶。
     */
    public synchronized List<Map<String, Object>> rotateIfNeeded(long nowMs) {
        long minuteStart = floorMinute(nowMs);
        if (currentMinuteStart < 0) {
            currentMinuteStart = minuteStart;
            return null;
        }
        if (minuteStart <= currentMinuteStart) {
            return null;
        }
        List<Map<String, Object>> rows = drainCurrent();
        currentMinuteStart = minuteStart;
        return rows;
    }

    /**
     * 强制导出当前分钟数据（上报前调用）。
     */
    public synchronized List<Map<String, Object>> flushCurrent() {
        if (currentMinuteStart < 0 || (buckets.isEmpty() && uaBuckets.isEmpty() && refererBuckets.isEmpty() && samples.isEmpty())) {
            return null;
        }
        return drainCurrent();
    }

    private List<Map<String, Object>> drainCurrent() {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        for (Map.Entry<BucketKey, BucketValue> entry : buckets.entrySet()) {
            BucketKey key = entry.getKey();
            BucketValue value = entry.getValue();
            Map<String, Object> row = new HashMap<String, Object>();
            row.put("bucketTime", currentMinuteStart);
            row.put("clientIp", key.clientIp);
            row.put("uri", key.uri);
            row.put("method", key.method);
            row.put("requestCount", value.requestCount);
            row.put("sumRequestTimeMs", value.sumRequestTimeMs);
            row.put("maxRequestTimeMs", value.maxRequestTimeMs);
            row.put("sumUpstreamTimeMs", value.sumUpstreamTimeMs);
            row.put("sumUpstreamConnectTimeMs", value.sumUpstreamConnectTimeMs);
            row.put("sumUpstreamHeaderTimeMs", value.sumUpstreamHeaderTimeMs);
            row.put("sumBodyBytes", value.sumBodyBytes);
            row.put("status2xx", value.status2xx);
            row.put("status4xx", value.status4xx);
            row.put("status5xx", value.status5xx);
            row.put("upstream5xx", value.upstream5xx);
            row.put("cacheHit", value.cacheHit);
            row.put("cacheMiss", value.cacheMiss);
            row.put("httpsCount", value.httpsCount);
            row.put("slowCount", value.slowCount);
            rows.add(row);
        }
        if (overflowCount > 0) {
            Map<String, Object> other = new HashMap<String, Object>();
            other.put("bucketTime", currentMinuteStart);
            other.put("clientIp", "__OTHER__");
            other.put("uri", "__OTHER__");
            other.put("method", "-");
            other.put("requestCount", overflowCount);
            other.put("sumRequestTimeMs", 0L);
            other.put("maxRequestTimeMs", 0L);
            other.put("sumUpstreamTimeMs", 0L);
            other.put("sumUpstreamConnectTimeMs", 0L);
            other.put("sumUpstreamHeaderTimeMs", 0L);
            other.put("sumBodyBytes", 0L);
            other.put("status2xx", 0);
            other.put("status4xx", 0);
            other.put("status5xx", 0);
            other.put("upstream5xx", 0);
            other.put("cacheHit", 0);
            other.put("cacheMiss", 0);
            other.put("httpsCount", 0);
            other.put("slowCount", 0);
            rows.add(other);
        }

        // UA 维度行
        List<Map<String, Object>> uaRows = new ArrayList<Map<String, Object>>();
        for (Map.Entry<String, DimValue> e : uaBuckets.entrySet()) {
            Map<String, Object> r = new HashMap<String, Object>();
            r.put("bucketTime", currentMinuteStart);
            r.put("userAgent", e.getKey());
            r.put("requestCount", e.getValue().requestCount);
            r.put("sumRequestTimeMs", e.getValue().sumRequestTimeMs);
            r.put("maxRequestTimeMs", e.getValue().maxRequestTimeMs);
            r.put("slowCount", e.getValue().slowCount);
            r.put("status5xx", e.getValue().status5xx);
            uaRows.add(r);
        }
        // Referer 维度行
        List<Map<String, Object>> refererRows = new ArrayList<Map<String, Object>>();
        for (Map.Entry<String, DimValue> e : refererBuckets.entrySet()) {
            Map<String, Object> r = new HashMap<String, Object>();
            r.put("bucketTime", currentMinuteStart);
            r.put("referer", e.getKey());
            r.put("requestCount", e.getValue().requestCount);
            r.put("sumRequestTimeMs", e.getValue().sumRequestTimeMs);
            r.put("maxRequestTimeMs", e.getValue().maxRequestTimeMs);
            r.put("slowCount", e.getValue().slowCount);
            r.put("status5xx", e.getValue().status5xx);
            refererRows.add(r);
        }

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("rows", rows);
        result.put("uaRows", uaRows);
        result.put("refererRows", refererRows);
        result.put("samples", new ArrayList<Map<String, Object>>(samples));

        // 清桶
        buckets.clear();
        uaBuckets.clear();
        refererBuckets.clear();
        samples.clear();
        overflowCount = 0;

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> flat = (List<Map<String, Object>>) result.get("rows");
        return flat.isEmpty() && uaRows.isEmpty() && refererRows.isEmpty()
                ? null : java.util.Collections.singletonList(result);
    }

    public synchronized long getCurrentMinuteStart() {
        return currentMinuteStart;
    }

    private long floorMinute(long ts) {
        return ts - (ts % 60000L);
    }
}
