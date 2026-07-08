package com.ops.server.logmgmt.service;

import com.ops.common.model.NodeModel;
import com.ops.common.model.ProjectLogProfileModel;
import com.ops.common.model.ProjectModel;
import com.ops.server.client.AgentClient;
import com.ops.server.mapper.NodeMapper;
import com.ops.server.mapper.ProjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 日志搜索服务
 */
@Service
public class LogSearchService {

    private static final int SEARCH_TIMEOUT_SEC = 30;
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(10);

    @Autowired
    private AgentClient agentClient;

    @Autowired
    private NodeMapper nodeMapper;

    @Autowired
    private ProjectMapper projectMapper;

    @Autowired
    private LogAggregateService logAggregateService;

    /**
     * 关键词搜索日志
     */
    public Map<String, Object> search(Long projectId, String keyword, String scope,
                                      List<Long> nodeIds, int contextLines, int maxResults,
                                      ProjectLogProfileModel profile) {
        List<Long> targets = resolveNodeIds(projectId, nodeIds, scope);
        List<Map<String, Object>> hits = new ArrayList<>();
        List<Future<List<Map<String, Object>>>> futures = new ArrayList<>();

        for (Long nodeId : targets) {
            futures.add(EXECUTOR.submit(new SearchTask(nodeId, keyword, contextLines, maxResults, profile)));
        }
        for (Future<List<Map<String, Object>>> future : futures) {
            try {
                List<Map<String, Object>> nodeHits = future.get(SEARCH_TIMEOUT_SEC, TimeUnit.SECONDS);
                if (nodeHits != null) {
                    hits.addAll(nodeHits);
                }
            } catch (Exception ignored) {
                // 单节点搜索失败跳过
            }
        }

        hits.sort(Comparator.comparingLong(
                (Map<String, Object> hit) -> toLong(hit.get("timestamp"))).reversed());
        if (hits.size() > maxResults) {
            hits = hits.subList(0, maxResults);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("keyword", keyword);
        data.put("totalHits", hits.size());
        data.put("hits", hits);
        return data;
    }

    private List<Long> resolveNodeIds(Long projectId, List<Long> nodeIds, String scope) {
        if ("SINGLE".equals(scope) && nodeIds != null && !nodeIds.isEmpty()) {
            return nodeIds.subList(0, 1);
        }
        if (nodeIds != null && !nodeIds.isEmpty()) {
            return nodeIds;
        }
        ProjectModel project = projectMapper.findById(projectId);
        List<Long> ids = new ArrayList<>();
        if (project == null || project.getNodeIds() == null) {
            return ids;
        }
        for (String part : project.getNodeIds().split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                ids.add(Long.parseLong(trimmed));
            }
        }
        return ids;
    }

    private long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.parseLong(value.toString());
    }

    private class SearchTask implements Callable<List<Map<String, Object>>> {
        private final Long nodeId;
        private final String keyword;
        private final int contextLines;
        private final int maxResults;
        private final ProjectLogProfileModel profile;

        SearchTask(Long nodeId, String keyword, int contextLines, int maxResults,
                   ProjectLogProfileModel profile) {
            this.nodeId = nodeId;
            this.keyword = keyword;
            this.contextLines = contextLines;
            this.maxResults = maxResults;
            this.profile = profile;
        }

        @Override
        @SuppressWarnings("unchecked")
        public List<Map<String, Object>> call() {
            NodeModel node = nodeMapper.findById(nodeId);
            if (node == null || node.getStatus() == null || node.getStatus() != 1) {
                return Collections.emptyList();
            }
            ProjectModel project = projectMapper.findById(profile.getProjectId());
            String logPath = logAggregateService.resolveLogPath(project, profile, null);

            Map<String, Object> body = new HashMap<>();
            body.put("logPath", logPath);
            body.put("keyword", keyword);
            body.put("maxResults", maxResults);
            body.put("contextLines", contextLines);
            body.put("timeoutSec", 30);

            try {
                Map<String, Object> data = agentClient.extractDataMap(
                        agentClient.postForMap(node, "/file/log/search", body));
                Object hitsObj = data.get("hits");
                if (hitsObj instanceof List) {
                    List<Map<String, Object>> hits = (List<Map<String, Object>>) hitsObj;
                    for (Map<String, Object> hit : hits) {
                        hit.put("nodeId", nodeId);
                        hit.put("nodeName", node.getName());
                        if (hit.get("matchedLine") != null) {
                            hit.put("matchedLine", LogAggregateService.maskSensitive(
                                    hit.get("matchedLine").toString()));
                        }
                    }
                    return hits;
                }
            } catch (Exception ignored) {
                // fallback: 简单内容匹配
            }
            return fallbackSearch(node, logPath, profile);
        }

        private List<Map<String, Object>> fallbackSearch(NodeModel node, String logPath,
                                                           ProjectLogProfileModel profile) {
            Map<String, String> params = new HashMap<>();
            params.put("logPath", logPath);
            params.put("offset", "0");
            params.put("lines", String.valueOf(maxResults * 5));
            String content = agentClient.extractDataString(
                    agentClient.getForMap(node, "/file/log", params));
            List<Map<String, Object>> hits = new ArrayList<>();
            if (content == null || content.isEmpty()) {
                return hits;
            }
            String[] lines = content.split("\n");
            for (int i = 0; i < lines.length && hits.size() < maxResults; i++) {
                if (lines[i].contains(keyword)) {
                    Map<String, Object> hit = new HashMap<>();
                    hit.put("nodeId", nodeId);
                    hit.put("nodeName", node.getName());
                    hit.put("file", logPath);
                    hit.put("lineNo", i + 1);
                    hit.put("timestamp", logAggregateService.parseTimestamp(lines[i], profile));
                    hit.put("matchedLine", LogAggregateService.maskSensitive(lines[i]));
                    // 添加上下文行
                    if (contextLines > 0) {
                        List<String> ctx = new ArrayList<>();
                        for (int j = Math.max(0, i - contextLines); j < i; j++) {
                            ctx.add(LogAggregateService.maskSensitive(lines[j]));
                        }
                        ctx.add(">>> " + LogAggregateService.maskSensitive(lines[i]));
                        for (int j = i + 1; j < Math.min(lines.length, i + contextLines + 1); j++) {
                            ctx.add(LogAggregateService.maskSensitive(lines[j]));
                        }
                        hit.put("context", ctx);
                    }
                    hits.add(hit);
                }
            }
            return hits;
        }
    }
}
