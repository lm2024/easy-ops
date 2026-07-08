package com.ops.server.monitorapp.service;

import com.ops.common.model.MonitorSnapshotModel;
import com.ops.server.client.AgentClient;
import com.ops.server.mapper.MonitorSnapshotMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 构建 AI 诊断上下文，按 token 预算智能截取
 */
@Service
public class ContextBudgetService {

    private static final int MAX_INPUT_TOKENS = 80000;
    private static final Pattern ERROR_PATTERN = Pattern.compile("(?i)(ERROR|Exception|FATAL|OOM)");
    private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
            "(?i)(password|passwd|secret|api[_-]?key|token)\\s*[=:]\\s*\\S+");

    @Autowired
    private MonitorSnapshotMapper snapshotMapper;
    @Autowired
    private AgentClient agentClient;

    /**
     * 构建诊断上下文并返回元信息
     */
    public Map<String, Object> buildContext(Long projectId, Long nodeId, String logPath) {
        StringBuilder ctx = new StringBuilder();
        List<MonitorSnapshotModel> snapshots = snapshotMapper.findRecent(projectId, nodeId, 5);
        int snapshotCount = snapshots != null ? snapshots.size() : 0;

        ctx.append("## 监控快照\n");
        if (snapshots != null) {
            for (MonitorSnapshotModel s : snapshots) {
                ctx.append("- 时间=").append(s.getCollectTime())
                        .append(" 健康=").append(s.getHealthStatus())
                        .append(" 进程=").append(s.getProcessStatus())
                        .append(" CPU=").append(s.getCpuPercent())
                        .append(" 内存MB=").append(s.getMemoryMb())
                        .append(" 响应ms=").append(s.getResponseMs())
                        .append("\n");
            }
        }

        String logContent = "";
        int errorLines = 0;
        if (nodeId != null && logPath != null && !logPath.isEmpty()) {
            logContent = agentClient.readLog(nodeId, logPath, 500);
            String errorSnippet = extractErrorLogs(logContent, 50000);
            errorLines = countLines(errorSnippet);
            ctx.append("\n## 错误日志\n").append(errorSnippet);
        }

        ctx.append("\n## 变更记录\n暂无最近变更记录\n");

        String masked = maskSensitive(ctx.toString());
        while (estimateTokens(masked) > MAX_INPUT_TOKENS && masked.length() > 1000) {
            masked = masked.substring(0, masked.length() * 3 / 4);
        }

        Map<String, Object> meta = new HashMap<String, Object>();
        meta.put("context", masked);
        meta.put("monitorSnapshots", snapshotCount);
        meta.put("errorLogLines", errorLines);
        meta.put("tokenEstimated", estimateTokens(masked));
        return meta;
    }

    /**
     * 估算 token（字符数 / 2）
     */
    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return text.length() / 2;
    }

    private String extractErrorLogs(String logContent, int maxChars) {
        if (logContent == null || logContent.isEmpty()) {
            return "";
        }
        String[] lines = logContent.split("\n");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (ERROR_PATTERN.matcher(lines[i]).find()) {
                int start = Math.max(0, i - 5);
                int end = Math.min(lines.length, i + 6);
                for (int j = start; j < end; j++) {
                    sb.append(lines[j]).append("\n");
                }
                sb.append("---\n");
            }
            if (sb.length() > maxChars) {
                break;
            }
        }
        return sb.length() > maxChars ? sb.substring(0, maxChars) : sb.toString();
    }

    private String maskSensitive(String text) {
        return SENSITIVE_PATTERN.matcher(text).replaceAll("$1=***");
    }

    private int countLines(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return text.split("\n").length;
    }
}
