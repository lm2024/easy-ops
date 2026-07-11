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
 * 日志搜索服务：在发现的全部日志文件中关键词搜索。
 */
@Service
public class LogSearchService {

    private static final int SEARCH_TIMEOUT_SEC = 60;
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(10);

    @Autowired
    private AgentClient agentClient;

    @Autowired
    private NodeMapper nodeMapper;

    @Autowired
    private ProjectMapper projectMapper;

    @Autowired
    private LogAggregateService logAggregateService;

    @Autowired
    private LogDiscoverSupport logDiscoverSupport;

    /**
     * 关键词搜索日志（扫描各节点发现的所有日志文件）。
     */
    public Map<String, Object> search(Long projectId, String keyword, String scope,
                                      List<Long> nodeIds, int contextLines, int maxResults,
                                      ProjectLogProfileModel profile, String level, String filePath) {
        List<Long> targets = resolveNodeIds(projectId, nodeIds, scope);
        List<Map<String, Object>> hits = new ArrayList<>();
        List<Map<String, Object>> scopeList = new ArrayList<>();
        List<Future<NodeSearchResult>> futures = new ArrayList<>();

        for (Long nodeId : targets) {
            futures.add(EXECUTOR.submit(new SearchTask(nodeId, keyword, contextLines,
                    maxResults, profile, level, filePath)));
        }
        for (Future<NodeSearchResult> future : futures) {
            try {
                NodeSearchResult result = future.get(SEARCH_TIMEOUT_SEC, TimeUnit.SECONDS);
                if (result != null) {
                    if (result.scope != null) {
                        scopeList.add(result.scope);
                    }
                    if (result.hits != null) {
                        hits.addAll(result.hits);
                    }
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
        data.put("scope", scope);
        data.put("totalHits", hits.size());
        data.put("hits", hits);
        data.put("nodeScopes", scopeList);
        data.put("searchDescription", buildSearchDescription(scope, scopeList, filePath));
        return data;
    }

    private String buildSearchDescription(String scope, List<Map<String, Object>> scopeList, String filePath) {
        if (filePath != null && !filePath.trim().isEmpty()) {
            return "单文件搜索: " + filePath.trim();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("AGGREGATE".equals(scope) ? "聚合搜索" : "单节点搜索");
        sb.append("，扫描 ").append(scopeList.size()).append(" 个节点上的日志文件：");
        for (Map<String, Object> nodeScope : scopeList) {
            sb.append(" [").append(nodeScope.get("nodeName")).append(": ");
            Object files = nodeScope.get("files");
            if (files instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> fileList = (List<Map<String, Object>>) files;
                for (int i = 0; i < fileList.size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(fileList.get(i).get("path"));
                }
            }
            sb.append("]");
        }
        return sb.toString();
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

    private static class NodeSearchResult {
        private final List<Map<String, Object>> hits;
        private final Map<String, Object> scope;

        NodeSearchResult(List<Map<String, Object>> hits, Map<String, Object> scope) {
            this.hits = hits;
            this.scope = scope;
        }
    }

    private class SearchTask implements Callable<NodeSearchResult> {
        private final Long nodeId;
        private final String keyword;
        private final int contextLines;
        private final int maxResults;
        private final ProjectLogProfileModel profile;
        private final String level;
        private final String filePath;

        SearchTask(Long nodeId, String keyword, int contextLines, int maxResults,
                   ProjectLogProfileModel profile, String level, String filePath) {
            this.nodeId = nodeId;
            this.keyword = keyword;
            this.contextLines = contextLines;
            this.maxResults = maxResults;
            this.profile = profile;
            this.level = level;
            this.filePath = filePath;
        }

        @Override
        public NodeSearchResult call() throws Exception {
            NodeModel node = nodeMapper.findById(nodeId);
            if (node == null || node.getStatus() == null || node.getStatus() != 1) {
                return new NodeSearchResult(Collections.emptyList(), null);
            }
            ProjectModel project = projectMapper.findById(profile.getProjectId());
            Map<String, Object> nodeScope = logDiscoverSupport.buildNodeScope(node, project, profile);

            List<Map<String, Object>> targets = new ArrayList<>();
            if (filePath != null && !filePath.trim().isEmpty()) {
                Map<String, Object> one = new HashMap<>();
                one.put("path", filePath.trim());
                one.put("name", fileNameFromPath(filePath.trim()));
                targets.add(one);
            } else {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> files = (List<Map<String, Object>>) nodeScope.get("files");
                if (files != null) {
                    targets.addAll(files);
                }
            }

            List<Map<String, Object>> allHits = new ArrayList<>();
            int perFile = Math.max(maxResults / Math.max(targets.size(), 1), 20);
            for (Map<String, Object> file : targets) {
                String path = file.get("path") != null ? file.get("path").toString() : null;
                if (path == null || path.isEmpty()) {
                    continue;
                }
                allHits.addAll(searchOneFile(node, path, file.get("name"), perFile));
            }
            return new NodeSearchResult(allHits, nodeScope);
        }

        @SuppressWarnings("unchecked")
        private List<Map<String, Object>> searchOneFile(NodeModel node, String logPath,
                                                         Object fileName, int limit) {
            Map<String, Object> body = new HashMap<>();
            body.put("logPath", logPath);
            body.put("keyword", keyword);
            body.put("maxResults", limit);
            body.put("contextLines", contextLines);
            if (level != null && !level.trim().isEmpty() && !"ALL".equalsIgnoreCase(level.trim())) {
                body.put("level", level.trim());
            }

            try {
                Map<String, Object> agentResp = agentClient.postForMap(node, "/file/log/search", body);
                agentClient.ensureAgentSuccess(agentResp);
                Map<String, Object> data = agentClient.extractDataMap(agentResp);
                Object hitsObj = data.get("hits");
                if (!(hitsObj instanceof List)) {
                    hitsObj = data.get("matches");
                }
                if (!(hitsObj instanceof List)) {
                    return Collections.emptyList();
                }
                List<Map<String, Object>> hits = (List<Map<String, Object>>) hitsObj;
                for (Map<String, Object> hit : hits) {
                    enrichHit(hit, node, logPath, fileName);
                }
                return hits;
            } catch (Exception ignored) {
                return Collections.emptyList();
            }
        }

        private void enrichHit(Map<String, Object> hit, NodeModel node, String logPath, Object fileName) {
            hit.put("nodeId", nodeId);
            hit.put("nodeName", node.getName());
            hit.put("file", logPath);
            hit.put("fileName", fileName != null ? fileName.toString() : fileNameFromPath(logPath));
            if (hit.get("matchedLine") != null) {
                String line = hit.get("matchedLine").toString();
                hit.put("matchedLine", LogAggregateService.maskSensitive(line));
                hit.put("timestamp", logAggregateService.parseTimestamp(line, profile));
                hit.put("content", hit.get("matchedLine"));
            }
            hit.put("context", buildContext(hit));
        }

        @SuppressWarnings("unchecked")
        private List<String> buildContext(Map<String, Object> hit) {
            List<String> ctx = new ArrayList<>();
            Object before = hit.get("contextBefore");
            if (before instanceof List) {
                for (Object line : (List<?>) before) {
                    ctx.add(LogAggregateService.maskSensitive(String.valueOf(line)));
                }
            }
            if (hit.get("matchedLine") != null) {
                ctx.add(">>> " + hit.get("matchedLine"));
            }
            Object after = hit.get("contextAfter");
            if (after instanceof List) {
                for (Object line : (List<?>) after) {
                    ctx.add(LogAggregateService.maskSensitive(String.valueOf(line)));
                }
            }
            return ctx;
        }

        private String fileNameFromPath(String path) {
            int idx = path.lastIndexOf('/');
            return idx >= 0 ? path.substring(idx + 1) : path;
        }
    }
}
