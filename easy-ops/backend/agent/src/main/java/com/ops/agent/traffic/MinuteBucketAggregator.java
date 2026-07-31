package com.ops.agent.traffic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 按分钟桶在内存中聚合访问统计。
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
        int status2xx;
        int status4xx;
        int status5xx;
        int slowCount;
    }

    private final int maxKeys;
    private final double slowThresholdSec;
    private long currentMinuteStart = -1L;
    private final Map<BucketKey, BucketValue> buckets = new HashMap<BucketKey, BucketValue>();
    private int overflowCount;

    public MinuteBucketAggregator(int maxKeys, double slowThresholdSec) {
        this.maxKeys = Math.max(100, maxKeys);
        this.slowThresholdSec = slowThresholdSec <= 0 ? 3D : slowThresholdSec;
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
        int status = line.status;
        if (status >= 200 && status < 300) {
            value.status2xx++;
        } else if (status >= 400 && status < 500) {
            value.status4xx++;
        } else if (status >= 500) {
            value.status5xx++;
        }
        if (line.requestTimeMs >= slowThresholdSec * 1000D) {
            value.slowCount++;
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
        if (currentMinuteStart < 0 || buckets.isEmpty()) {
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
            row.put("status2xx", value.status2xx);
            row.put("status4xx", value.status4xx);
            row.put("status5xx", value.status5xx);
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
            other.put("status2xx", 0);
            other.put("status4xx", 0);
            other.put("status5xx", 0);
            other.put("slowCount", 0);
            rows.add(other);
        }
        buckets.clear();
        overflowCount = 0;
        return rows.isEmpty() ? null : rows;
    }

    public synchronized long getCurrentMinuteStart() {
        return currentMinuteStart;
    }

    private long floorMinute(long ts) {
        return ts - (ts % 60000L);
    }
}
