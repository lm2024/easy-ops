package com.ops.server.logmgmt.service;

import com.ops.common.util.LogLevelUtil;
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
 * 多节点日志聚合服务：扫描各节点发现的全部日志文件并合并。
 */
@Service
public class LogAggregateService {

    private static final int MAX_NODES = 20;
    private static final int TAIL_LINES_PER_FILE = 100;
    private static final int FETCH_TIMEOUT_SEC = 30;
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(10);

    @Autowired
    private AgentClient agentClient;

    @Autowired
    private NodeMapper nodeMapper;

    @Autowired
    private ProjectMapper projectMapper;

    @Autowired
    private LogDiscoverSupport logDiscoverSupport;

    /**
     * 聚合多节点日志，按时间倒序。
     */
    public Map<String, Object> aggregate(Long projectId, List<Long> nodeIds,
                                         ProjectLogProfileModel profile,
                                         int page, int pageSize, Long since, String level) {
        List<Long> targets = resolveNodeIds(projectId, nodeIds);
        if (targets.size() > MAX_NODES) {
            targets = targets.subList(0, MAX_NODES);
        }

        List<Map<String, Object>> allLines = new ArrayList<>();
        List<Map<String, Object>> nodeScopes = new ArrayList<>();
        List<Future<NodeTailResult>> futures = new ArrayList<>();
        for (Long nodeId : targets) {
            futures.add(EXECUTOR.submit(new TailFetchTask(nodeId, profile, since, level)));
        }
        for (Future<NodeTailResult> future : futures) {
            try {
                NodeTailResult result = future.get(FETCH_TIMEOUT_SEC, TimeUnit.SECONDS);
                if (result != null) {
                    if (result.scope != null) {
                        nodeScopes.add(result.scope);
                    }
                    if (result.lines != null) {
                        allLines.addAll(result.lines);
                    }
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
        data.put("nodeScopes", nodeScopes);
        data.put("aggregateDescription", buildAggregateDescription(nodeScopes));
        return data;
    }

    private String buildAggregateDescription(List<Map<String, Object>> nodeScopes) {
        StringBuilder sb = new StringBuilder();
        sb.append("聚合扫描 ").append(nodeScopes.size()).append(" 个节点，每节点最多 ")
                .append(LogDiscoverSupport.MAX_FILES_PER_NODE)
                .append(" 个日志文件各取尾部 ").append(TAIL_LINES_PER_FILE).append(" 行：");
        for (Map<String, Object> scope : nodeScopes) {
            sb.append(" [").append(scope.get("nodeName")).append(": ");
            Object files = scope.get("files");
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

    /**
     * 解析日志行时间戳。
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
        if (fileName != null && fileName.trim().startsWith("/")) {
            return fileName.trim();
        }
        String logDir = profile.getLogDir();
        String file = fileName != null && !fileName.trim().isEmpty()
                ? fileName.trim()
                : (profile.getMainLogFile() != null ? profile.getMainLogFile().trim() : "");
        if (file.isEmpty()) {
            return null;
        }
        String dir;
        if (logDir.startsWith("/")) {
            dir = logDir;
        } else {
            String base = project.getDeployDir();
            dir = joinPath(base, logDir);
        }
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

    private static class NodeTailResult {
        private final List<Map<String, Object>> lines;
        private final Map<String, Object> scope;

        NodeTailResult(List<Map<String, Object>> lines, Map<String, Object> scope) {
            this.lines = lines;
            this.scope = scope;
        }
    }

    private class TailFetchTask implements Callable<NodeTailResult> {
        private final Long nodeId;
        private final ProjectLogProfileModel profile;
        private final Long since;
        private final String level;

        TailFetchTask(Long nodeId, ProjectLogProfileModel profile, Long since, String level) {
            this.nodeId = nodeId;
            this.profile = profile;
            this.since = since;
            this.level = level;
        }

        @Override
        public NodeTailResult call() {
            NodeModel node = nodeMapper.findById(nodeId);
            if (node == null || node.getStatus() == null || node.getStatus() != 1) {
                return new NodeTailResult(Collections.emptyList(), null);
            }
            ProjectModel project = projectMapper.findById(profile.getProjectId());
            Map<String, Object> nodeScope = logDiscoverSupport.buildNodeScope(node, project, profile);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> files = (List<Map<String, Object>>) nodeScope.get("files");
            if (files == null || files.isEmpty()) {
                return new NodeTailResult(Collections.emptyList(), nodeScope);
            }

            List<Map<String, Object>> lines = new ArrayList<>();
            for (Map<String, Object> file : files) {
                String logPath = file.get("path") != null ? file.get("path").toString() : null;
                if (logPath == null || logPath.isEmpty()) {
                    continue;
                }
                lines.addAll(tailOneFile(node, logPath, file));
            }
            return new NodeTailResult(lines, nodeScope);
        }

        private List<Map<String, Object>> tailOneFile(NodeModel node, String logPath,
                                                       Map<String, Object> fileMeta) {
            Map<String, String> params = new HashMap<>();
            params.put("logPath", logPath);
            params.put("lines", String.valueOf(TAIL_LINES_PER_FILE));
            if (level != null && !level.trim().isEmpty() && !"ALL".equalsIgnoreCase(level.trim())) {
                params.put("level", level.trim());
            }
            String content = "";
            try {
                Map<String, Object> agentResp = agentClient.getForMap(node, "/file/log/tail", params);
                agentClient.ensureAgentSuccess(agentResp);
                content = agentClient.extractDataString(agentResp);
            } catch (Exception ignored) {
                return Collections.emptyList();
            }

            List<Map<String, Object>> lines = new ArrayList<>();
            if (content == null || content.isEmpty()) {
                return lines;
            }
            String[] parts = content.split("\n");
            int maxLen = profile.getMaxLineLength() != null ? profile.getMaxLineLength() : 4096;
            String fileName = fileMeta.get("name") != null ? fileMeta.get("name").toString()
                    : logPath.substring(logPath.lastIndexOf('/') + 1);
            String sourceDir = fileMeta.get("sourceDir") != null ? fileMeta.get("sourceDir").toString() : "";
            for (int i = 0; i < parts.length; i++) {
                String line = truncate(parts[i], maxLen);
                if (!LogLevelUtil.matches(line, level)) {
                    continue;
                }
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
                item.put("sourceFile", fileName);
                item.put("sourcePath", logPath);
                item.put("sourceDir", sourceDir);
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
