package com.ops.server.logmgmt.service;

import com.ops.common.model.NodeModel;
import com.ops.common.model.ProjectLogProfileModel;
import com.ops.common.model.ProjectModel;
import com.ops.server.client.AgentClient;
import com.ops.server.mapper.NodeMapper;
import com.ops.server.mapper.ProjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 多节点日志聚合服务
 */
@Service
public class LogAggregateService {

    private static final int MAX_NODES = 20;
    private static final int TAIL_LINES = 200;
    private static final int FETCH_TIMEOUT_SEC = 15;
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(10);

    @Autowired
    private AgentClient agentClient;

    @Autowired
    private NodeMapper nodeMapper;

    @Autowired
    private ProjectMapper projectMapper;

    /**
     * 聚合多节点日志，按时间倒序
     */
    public Map<String, Object> aggregate(Long projectId, List<Long> nodeIds,
                                         ProjectLogProfileModel profile,
                                         int page, int pageSize, Long since) {
        List<Long> targets = resolveNodeIds(projectId, nodeIds);
        if (targets.size() > MAX_NODES) {
            targets = targets.subList(0, MAX_NODES);
        }

        List<Map<String, Object>> allLines = new ArrayList<>();
        List<Future<List<Map<String, Object>>>> futures = new ArrayList<>();
        for (Long nodeId : targets) {
            futures.add(EXECUTOR.submit(new TailFetchTask(nodeId, profile, since)));
        }
        for (Future<List<Map<String, Object>>> future : futures) {
            try {
                List<Map<String, Object>> lines = future.get(FETCH_TIMEOUT_SEC, TimeUnit.SECONDS);
                if (lines != null) {
                    allLines.addAll(lines);
                }
            } catch (Exception ignored) {
                // 单节点失败跳过
            }
        }

        allLines.sort(Comparator.comparingLong(
                (Map<String, Object> line) -> toLong(line.get("timestamp"))).reversed());

        int total = allLines.size();
        int from = Math.max(0, (page - 1) * pageSize);
        int to = Math.min(from + pageSize, total);
        List<Map<String, Object>> pageLines = from < total
                ? allLines.subList(from, to) : Collections.emptyList();

        Map<String, Object> data = new HashMap<>();
        data.put("total", total);
        data.put("page", page);
        data.put("pageSize", pageSize);
        data.put("lines", pageLines);
        return data;
    }

    /**
     * 解析日志行时间戳
     */
    public long parseTimestamp(String line, ProjectLogProfileModel profile) {
        if (line == null || profile == null) {
            return 0L;
        }
        try {
            Pattern pattern = Pattern.compile(profile.getTimestampRegex());
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                String tsText = matcher.group(1) != null ? matcher.group(1) : matcher.group();
                SimpleDateFormat sdf = new SimpleDateFormat(profile.getTimestampFormat());
                return sdf.parse(tsText).getTime();
            }
        } catch (Exception ignored) {
            // 解析失败降级为 0
        }
        return 0L;
    }

    String resolveLogPath(ProjectModel project, ProjectLogProfileModel profile, String fileName) {
        String base = project.getDeployDir();
        String logDir = profile.getLogDir();
        String file = fileName != null ? fileName : profile.getMainLogFile();
        String dir = logDir.startsWith("/") ? base + logDir : joinPath(base, logDir);
        return joinPath(dir, file);
    }

    private List<Long> resolveNodeIds(Long projectId, List<Long> nodeIds) {
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

    private String joinPath(String base, String relative) {
        if (base == null) {
            return relative;
        }
        if (relative.startsWith("/")) {
            return base + relative;
        }
        return base.endsWith("/") ? base + relative : base + "/" + relative;
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

    private class TailFetchTask implements Callable<List<Map<String, Object>>> {
        private final Long nodeId;
        private final ProjectLogProfileModel profile;
        private final Long since;

        TailFetchTask(Long nodeId, ProjectLogProfileModel profile, Long since) {
            this.nodeId = nodeId;
            this.profile = profile;
            this.since = since;
        }

        @Override
        public List<Map<String, Object>> call() {
            NodeModel node = nodeMapper.findById(nodeId);
            if (node == null || node.getStatus() == null || node.getStatus() != 1) {
                return Collections.emptyList();
            }
            ProjectModel project = projectMapper.findById(profile.getProjectId());
            String logPath = resolveLogPath(project, profile, null);
            Map<String, String> params = new HashMap<>();
            params.put("logPath", logPath);
            params.put("lines", String.valueOf(TAIL_LINES));
            String content;
            try {
                content = agentClient.extractDataString(
                        agentClient.getForMap(node, "/file/log/tail", params));
            } catch (Exception e) {
                Map<String, String> fallback = new HashMap<>();
                fallback.put("logPath", logPath);
                fallback.put("offset", "0");
                fallback.put("lines", String.valueOf(TAIL_LINES));
                content = agentClient.extractDataString(
                        agentClient.getForMap(node, "/file/log", fallback));
            }

            List<Map<String, Object>> lines = new ArrayList<>();
            if (content == null || content.isEmpty()) {
                return lines;
            }
            String[] parts = content.split("\n");
            int maxLen = profile.getMaxLineLength() != null ? profile.getMaxLineLength() : 4096;
            for (int i = 0; i < parts.length; i++) {
                String line = truncate(parts[i], maxLen);
                long ts = parseTimestamp(line, profile);
                if (since != null && since > 0 && ts > 0 && ts < since) {
                    continue;
                }
                Map<String, Object> item = new HashMap<>();
                item.put("nodeId", nodeId);
                item.put("nodeName", node.getName());
                item.put("timestamp", ts);
                item.put("lineNo", i + 1);
                item.put("content", maskSensitive(line));
                item.put("sourceFile", profile.getMainLogFile());
                lines.add(item);
            }
            return lines;
        }
    }

    static String truncate(String line, int maxLen) {
        if (line == null) {
            return "";
        }
        return line.length() > maxLen ? line.substring(0, maxLen) + "..." : line;
    }

    static String maskSensitive(String line) {
        if (line == null) {
            return "";
        }
        return line.replaceAll("(?i)(password|token|secret)\\s*=\\s*\\S+", "$1=***");
    }
}
