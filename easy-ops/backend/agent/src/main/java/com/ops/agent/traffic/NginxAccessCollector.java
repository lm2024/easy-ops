package com.ops.agent.traffic;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nginx 访问日志增量采集：tail 新行 → 分钟聚合 → 上报 Server。
 */
@Component
public class NginxAccessCollector {

    private static final Logger log = LoggerFactory.getLogger(NginxAccessCollector.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${agent.server-url:http://localhost:8081/api}")
    private String serverUrl;

    @Value("${agent.token:}")
    private String agentToken;

    @Value("${agent.data-path:/app/data}")
    private String agentDataPath;

    private final NginxLogParser parser = new NginxLogParser();
    private OffsetTracker offsetTracker;
    private RestTemplate restTemplate;

    private final Map<Long, SourceRuntime> runtimes = new ConcurrentHashMap<Long, SourceRuntime>();
    private volatile List<Map<String, Object>> cachedSources = new ArrayList<Map<String, Object>>();

    @PostConstruct
    public void init() {
        offsetTracker = new OffsetTracker(agentDataPath);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(15000);
        restTemplate = new RestTemplate(factory);
    }

    @Scheduled(initialDelay = 5000, fixedDelay = 30000)
    public void refreshSources() {
        if (agentToken == null || agentToken.trim().isEmpty()) {
            return;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Token", agentToken);
            HttpEntity<Void> entity = new HttpEntity<Void>(headers);
            String url = serverUrl + "/nginx-traffic/agent/sources";
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            if (resp.getBody() == null) {
                return;
            }
            Map<String, Object> body = MAPPER.readValue(resp.getBody(), Map.class);
            if (body == null || toInt(body.get("code"), 0) != 200) {
                return;
            }
            Object data = body.get("data");
            if (!(data instanceof List)) {
                cachedSources = new ArrayList<Map<String, Object>>();
                return;
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> list = (List<Map<String, Object>>) data;
            cachedSources = list;
            log.info("Nginx 日志源已刷新，共 {} 个", list.size());
            for (Map<String, Object> source : list) {
                Long sourceId = toLong(source.get("id"));
                if (sourceId == null) {
                    continue;
                }
                if (!runtimes.containsKey(sourceId)) {
                    int maxKeys = toInt(source.get("maxKeysPerMinute"), 500);
                    double slow = toDouble(source.get("slowThresholdSec"), 3D);
                    runtimes.put(sourceId, new SourceRuntime(sourceId, maxKeys, slow));
                }
            }
        } catch (Exception e) {
            log.warn("刷新 Nginx 日志源失败: {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelay = 2000)
    public void collectTick() {
        if (cachedSources.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Map<String, Object> source : cachedSources) {
            if (!Integer.valueOf(1).equals(toInt(source.get("enabled"), 0))) {
                continue;
            }
            Long sourceId = toLong(source.get("id"));
            String logPath = source.get("logPath") == null ? "" : String.valueOf(source.get("logPath"));
            if (sourceId == null || logPath.trim().isEmpty()) {
                continue;
            }
            SourceRuntime runtime = runtimes.get(sourceId);
            if (runtime == null) {
                int maxKeys = toInt(source.get("maxKeysPerMinute"), 500);
                double slow = toDouble(source.get("slowThresholdSec"), 3D);
                runtime = new SourceRuntime(sourceId, maxKeys, slow);
                runtimes.put(sourceId, runtime);
            }
            try {
                readNewLines(sourceId, logPath.trim(), runtime);
                List<Map<String, Object>> rotated = runtime.aggregator.rotateIfNeeded(now);
                if (rotated != null && !rotated.isEmpty()) {
                    report(sourceId, rotated);
                }
            } catch (Exception e) {
                offsetTracker.markError(logPath, e.getMessage());
                log.warn("采集 Nginx 日志失败 sourceId={} path={}", sourceId, logPath, e);
            }
        }
    }

    @Scheduled(fixedDelay = 60000)
    public void flushTick() {
        long now = System.currentTimeMillis();
        for (Map.Entry<Long, SourceRuntime> entry : runtimes.entrySet()) {
            SourceRuntime runtime = entry.getValue();
            List<Map<String, Object>> rows = runtime.aggregator.flushCurrent();
            if (rows != null && !rows.isEmpty()) {
                report(entry.getKey(), rows);
            }
            runtime.aggregator.rotateIfNeeded(now);
        }
    }

    private void readNewLines(Long sourceId, String logPath, SourceRuntime runtime) throws Exception {
        File file = new File(logPath);
        if (!file.exists() || !file.isFile()) {
            throw new IllegalStateException("日志文件不存在: " + logPath);
        }
        OffsetTracker.OffsetState state = offsetTracker.get(logPath);
        long inode = inodeOf(file);
        long offset = state.offset;
        if (inode > 0 && state.inode > 0 && inode != state.inode) {
            offset = 0L;
        }
        if (file.length() < offset) {
            offset = 0L;
        }
        if (file.length() == offset) {
            return;
        }
        RandomAccessFile raf = new RandomAccessFile(file, "r");
        try {
            raf.seek(offset);
            String line;
            while ((line = raf.readLine()) != null) {
                String decoded = new String(line.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
                NginxLogParser.ParsedLine parsed = parser.parse(decoded);
                if (parsed != null) {
                    List<Map<String, Object>> rotated = runtime.aggregator.add(parsed);
                    runtime.parsedCount++;
                    if (rotated != null && !rotated.isEmpty()) {
                        report(sourceId, rotated);
                    }
                }
            }
            offsetTracker.update(logPath, raf.getFilePointer(), inode);
            if (runtime.parsedCount > 0 && runtime.parsedCount % 50 == 0) {
                log.info("Nginx 日志采集 sourceId={} 已解析 {} 行", sourceId, runtime.parsedCount);
            }
        } finally {
            raf.close();
        }
        runtime.lastReadAt = System.currentTimeMillis();
        runtime.lastOffset = offsetTracker.get(logPath).offset;
    }

    @SuppressWarnings("unchecked")
    private void report(Long sourceId, List<Map<String, Object>> wrapped) {
        if (agentToken == null || agentToken.trim().isEmpty() || wrapped == null || wrapped.isEmpty()) {
            return;
        }
        try {
            // drainCurrent 返回单元素包裹：{rows, ipRows, uaRows, refererRows, samples}
            Map<String, Object> bundle = wrapped.get(0);
            List<Map<String, Object>> rows = (List<Map<String, Object>>) bundle.get("rows");
            List<Map<String, Object>> ipRows = (List<Map<String, Object>>) bundle.get("ipRows");
            List<Map<String, Object>> uaRows = (List<Map<String, Object>>) bundle.get("uaRows");
            List<Map<String, Object>> refererRows = (List<Map<String, Object>>) bundle.get("refererRows");
            List<Map<String, Object>> samples = (List<Map<String, Object>>) bundle.get("samples");
            if ((rows == null || rows.isEmpty())
                    && (ipRows == null || ipRows.isEmpty())
                    && (uaRows == null || uaRows.isEmpty())
                    && (refererRows == null || refererRows.isEmpty())
                    && (samples == null || samples.isEmpty())) {
                return;
            }
            Map<String, Object> payload = new HashMap<String, Object>();
            payload.put("sourceId", sourceId);
            payload.put("rows", rows == null ? java.util.Collections.emptyList() : rows);
            payload.put("ipRows", ipRows == null ? java.util.Collections.emptyList() : ipRows);
            payload.put("uaRows", uaRows == null ? java.util.Collections.emptyList() : uaRows);
            payload.put("refererRows", refererRows == null ? java.util.Collections.emptyList() : refererRows);
            payload.put("samples", samples == null ? java.util.Collections.emptyList() : samples);
            payload.put("reportTime", System.currentTimeMillis());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Token", agentToken);
            HttpEntity<String> entity = new HttpEntity<String>(MAPPER.writeValueAsString(payload), headers);
            restTemplate.postForEntity(serverUrl + "/nginx-traffic/ingest", entity, String.class);
            int total = (rows == null ? 0 : rows.size()) + (ipRows == null ? 0 : ipRows.size())
                    + (uaRows == null ? 0 : uaRows.size()) + (refererRows == null ? 0 : refererRows.size())
                    + (samples == null ? 0 : samples.size());
            log.info("Nginx 统计已上报 sourceId={} 行数={}", sourceId, total);
        } catch (Exception e) {
            log.warn("上报 Nginx 统计失败 sourceId={}", sourceId, e);
        }
    }

    private long inodeOf(File file) {
        try {
            Object attr = Files.getAttribute(file.toPath(), "unix:ino");
            if (attr instanceof Number) {
                return ((Number) attr).longValue();
            }
        } catch (Exception ignored) {
        }
        return file.lastModified();
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

    private int toInt(Object value, int defaultValue) {
        Long l = toLong(value);
        return l == null ? defaultValue : l.intValue();
    }

    private double toDouble(Object value, double defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    static class SourceRuntime {
        final Long sourceId;
        final MinuteBucketAggregator aggregator;
        long parsedCount;
        long lastReadAt;
        long lastOffset;

        SourceRuntime(Long sourceId, int maxKeys, double slowThresholdSec) {
            this.sourceId = sourceId;
            this.aggregator = new MinuteBucketAggregator(maxKeys, slowThresholdSec);
        }
    }
}
