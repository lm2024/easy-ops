package com.ops.server.logmgmt.service;

import com.ops.common.constant.ErrorCode;
import com.ops.common.exception.BusinessException;
import com.ops.common.model.NodeModel;
import com.ops.common.model.ProjectLogProfileModel;
import com.ops.common.model.ProjectModel;
import com.ops.server.client.AgentClient;
import com.ops.server.config.GlobalPathProperties;
import com.ops.server.mapper.NodeMapper;
import com.ops.server.mapper.ProjectLogProfileMapper;
import com.ops.server.mapper.ProjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 日志管理服务
 */
@Service
public class LogMgmtService {

    private static final String LEGACY_AGENT_LOG_DIR = "/app/data/logs";
    private static final String LEGACY_AGENT_LOG_FILE = "agent.log";
    private static final String DEFAULT_MAIN_LOG = "";
    private static final String DEFAULT_ROLLING_PATTERN = "*.log";
    private static final String DEFAULT_TIMESTAMP_REGEX = "\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}";
    private static final String DEFAULT_TIMESTAMP_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS";
    private static final int DEFAULT_MAX_LINE_LENGTH = 4096;
    private static final int DEFAULT_RECENT_DAYS = 7;

    @Autowired
    private ProjectLogProfileMapper logProfileMapper;

    @Autowired
    private ProjectMapper projectMapper;

    @Autowired
    private NodeMapper nodeMapper;

    @Autowired
    private AgentClient agentClient;

    @Autowired
    private LogAggregateService logAggregateService;

    @Autowired
    private LogSearchService logSearchService;

    @Autowired
    private GlobalPathProperties globalPathProperties;

    public ProjectLogProfileModel getProfile(Long projectId) {
        ProjectLogProfileModel profile = logProfileMapper.findByProjectId(projectId);
        ProjectModel project = projectMapper.findById(projectId);
        if (profile == null) {
            profile = createDefaultProfile(projectId, project);
            logProfileMapper.insert(profile);
            return profile;
        }
        migrateLegacyProfile(profile, project);
        migrateAutoDetectProfile(profile);
        return profile;
    }

    public ProjectLogProfileModel saveProfile(ProjectLogProfileModel profile) {
        long now = System.currentTimeMillis();
        ProjectLogProfileModel existing = logProfileMapper.findByProjectId(profile.getProjectId());
        if (existing == null) {
            profile.setCreateTime(now);
            profile.setUpdateTime(now);
            if (profile.getMaxLineLength() == null) {
                profile.setMaxLineLength(DEFAULT_MAX_LINE_LENGTH);
            }
            if (profile.getRollingPattern() == null || profile.getRollingPattern().trim().isEmpty()) {
                profile.setRollingPattern(DEFAULT_ROLLING_PATTERN);
            }
            logProfileMapper.insert(profile);
        } else {
            profile.setId(existing.getId());
            profile.setCreateTime(existing.getCreateTime());
            profile.setUpdateTime(now);
            logProfileMapper.update(profile);
        }
        return profile;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> discoverLogFiles(Long projectId, Long nodeId) {
        ProjectLogProfileModel profile = requireProfile(projectId);
        ProjectModel project = requireProject(projectId);
        NodeModel node = requireOnlineNode(nodeId);
        String deployDir = resolveDeployDir(project);

        Map<String, String> params = new HashMap<>();
        params.put("deployDir", deployDir);
        params.put("logDir", profile.getLogDir());

        Map<String, Object> agentResp = agentClient.getForMap(node, "/file/log/discover", params);
        agentClient.ensureAgentSuccess(agentResp);
        Map<String, Object> data = agentClient.extractDataMap(agentResp);
        if (data == null) {
            data = new HashMap<>();
        }

        List<Map<String, Object>> files = data.get("files") instanceof List
                ? (List<Map<String, Object>>) data.get("files")
                : java.util.Collections.emptyList();

        Map<String, Object> result = new HashMap<>();
        result.put("files", files);
        result.put("hint", data.get("hint"));
        result.put("scannedDirs", data.get("scannedDirs"));
        result.put("suggestedMain", data.get("suggestedMain"));
        result.put("agentLogDir", data.get("agentLogDir"));
        result.put("deployDir", deployDir);
        result.put("total", files.size());
        return result;
    }

    /** @deprecated 兼容旧调用，请使用 discoverLogFiles */
    public List<Map<String, Object>> listLogFiles(Long projectId, Long nodeId) {
        Map<String, Object> discovered = discoverLogFiles(projectId, nodeId);
        Object files = discovered.get("files");
        if (files instanceof List) {
            return (List<Map<String, Object>>) files;
        }
        return java.util.Collections.emptyList();
    }

    public Map<String, Object> viewLog(Long projectId, Long nodeId, String fileName,
                                       int offset, int lines, String level, String mode) {
        ProjectLogProfileModel profile = requireProfile(projectId);
        ProjectModel project = requireProject(projectId);
        NodeModel node = requireOnlineNode(nodeId);
        String logPath = resolveViewPath(project, profile, fileName);
        if (logPath == null || logPath.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请先选择日志文件");
        }

        Map<String, String> params = new HashMap<>();
        params.put("logPath", logPath);
        params.put("lines", String.valueOf(lines));
        if (level != null && !level.trim().isEmpty() && !"ALL".equalsIgnoreCase(level.trim())) {
            params.put("level", level.trim());
        }
        Map<String, Object> agentResp;
        if ("tail".equalsIgnoreCase(mode)) {
            agentResp = agentClient.getForMap(node, "/file/log/tail", params);
        } else {
            params.put("offset", String.valueOf(offset));
            params.put("mode", "page");
            agentResp = agentClient.getForMap(node, "/file/log", params);
        }
        agentClient.ensureAgentSuccess(agentResp);
        Map<String, Object> agentData = agentClient.extractDataMap(agentResp);

        String content = agentData != null && agentData.get("content") != null
                ? agentData.get("content").toString() : "";

        Map<String, Object> data = new HashMap<>();
        data.put("content", LogAggregateService.maskSensitive(content));
        data.put("logPath", logPath);
        data.put("offset", offset);
        data.put("lines", lines);
        data.put("level", level);
        data.put("mode", mode);
        if (agentData != null) {
            data.put("hasMore", agentData.get("hasMore"));
            data.put("lineCount", agentData.get("totalLines"));
        }
        return data;
    }

    public Map<String, Object> aggregate(Long projectId, List<Long> nodeIds,
                                         int page, int pageSize, Long since, String level) {
        ProjectLogProfileModel profile = requireProfile(projectId);
        Long effectiveSince = resolveSince(since);
        return logAggregateService.aggregate(projectId, nodeIds, profile, page, pageSize,
                effectiveSince, level);
    }

    public Map<String, Object> search(Long projectId, String keyword, String scope,
                                      List<Long> nodeIds, int contextLines, int maxResults,
                                      String level, String filePath) {
        if (keyword == null || keyword.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "关键词不能为空");
        }
        ProjectLogProfileModel profile = requireProfile(projectId);
        return logSearchService.search(projectId, keyword, scope, nodeIds,
                contextLines, maxResults, profile, level, filePath);
    }

    private ProjectLogProfileModel requireProfile(Long projectId) {
        return getProfile(projectId);
    }

    private ProjectModel requireProject(Long projectId) {
        ProjectModel project = projectMapper.findById(projectId);
        if (project == null) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "项目不存在");
        }
        return project;
    }

    private NodeModel requireOnlineNode(Long nodeId) {
        NodeModel node = nodeMapper.findById(nodeId);
        if (node == null || node.getStatus() == null || node.getStatus() != 1) {
            throw new BusinessException(1002, "节点不存在或离线");
        }
        return node;
    }

    private String resolveDeployDir(ProjectModel project) {
        String deployDir = project != null ? project.getDeployDir() : null;
        if (deployDir == null || deployDir.trim().isEmpty()) {
            deployDir = globalPathProperties.resolveDeployDir(
                    project != null ? project.getName() : "app");
        }
        return deployDir;
    }

    private String resolveViewPath(ProjectModel project, ProjectLogProfileModel profile, String fileRef) {
        if (fileRef != null && fileRef.trim().startsWith("/")) {
            return fileRef.trim();
        }
        return logAggregateService.resolveLogPath(project, profile, fileRef);
    }

    private ProjectLogProfileModel createDefaultProfile(Long projectId, ProjectModel project) {
        String deployDir = resolveDeployDir(project);
        String defaultLogDir = globalPathProperties.resolveLogDir(deployDir);
        long now = System.currentTimeMillis();
        ProjectLogProfileModel profile = new ProjectLogProfileModel();
        profile.setProjectId(projectId);
        profile.setLogDir(defaultLogDir);
        profile.setMainLogFile("");
        profile.setRollingPattern(DEFAULT_ROLLING_PATTERN);
        profile.setTimestampRegex(DEFAULT_TIMESTAMP_REGEX);
        profile.setTimestampFormat(DEFAULT_TIMESTAMP_FORMAT);
        profile.setMaxLineLength(DEFAULT_MAX_LINE_LENGTH);
        profile.setCreateTime(now);
        profile.setUpdateTime(now);
        return profile;
    }

    private void migrateLegacyProfile(ProjectLogProfileModel profile, ProjectModel project) {
        if (!LEGACY_AGENT_LOG_DIR.equals(profile.getLogDir())
                || !LEGACY_AGENT_LOG_FILE.equals(profile.getMainLogFile())) {
            return;
        }
        if (project == null) {
            return;
        }
        String deployDir = project.getDeployDir();
        if (deployDir == null || deployDir.trim().isEmpty()) {
            deployDir = globalPathProperties.resolveDeployDir(project.getName());
        }
        profile.setLogDir(globalPathProperties.resolveLogDir(deployDir));
        profile.setMainLogFile("");
        profile.setRollingPattern(DEFAULT_ROLLING_PATTERN);
        profile.setUpdateTime(System.currentTimeMillis());
        logProfileMapper.update(profile);
    }

    private void migrateAutoDetectProfile(ProjectLogProfileModel profile) {
        if (!"app.log".equals(profile.getMainLogFile())) {
            return;
        }
        profile.setMainLogFile("");
        profile.setRollingPattern(DEFAULT_ROLLING_PATTERN);
        profile.setUpdateTime(System.currentTimeMillis());
        logProfileMapper.update(profile);
    }

    private Long resolveSince(Long since) {
        if (since != null && since > 0) {
            return since;
        }
        return System.currentTimeMillis() - DEFAULT_RECENT_DAYS * 24L * 60L * 60L * 1000L;
    }
}
