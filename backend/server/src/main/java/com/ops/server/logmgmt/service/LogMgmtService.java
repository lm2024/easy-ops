package com.ops.server.logmgmt.service;

import com.ops.common.constant.ErrorCode;
import com.ops.common.exception.BusinessException;
import com.ops.common.model.NodeModel;
import com.ops.common.model.ProjectLogProfileModel;
import com.ops.common.model.ProjectModel;
import com.ops.server.client.AgentClient;
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

    public ProjectLogProfileModel getProfile(Long projectId) {
        return logProfileMapper.findByProjectId(projectId);
    }

    public ProjectLogProfileModel saveProfile(ProjectLogProfileModel profile) {
        long now = System.currentTimeMillis();
        ProjectLogProfileModel existing = logProfileMapper.findByProjectId(profile.getProjectId());
        if (existing == null) {
            profile.setCreateTime(now);
            profile.setUpdateTime(now);
            if (profile.getMaxLineLength() == null) {
                profile.setMaxLineLength(4096);
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
    public Map<String, Object> listLogFiles(Long projectId, Long nodeId) {
        ProjectLogProfileModel profile = requireProfile(projectId);
        ProjectModel project = requireProject(projectId);
        NodeModel node = requireOnlineNode(nodeId);
        String logDir = logAggregateService.resolveLogPath(project, profile, "").replaceAll("/$", "");

        Map<String, String> params = new HashMap<>();
        params.put("logDir", logDir);
        if (profile.getRollingPattern() != null && !profile.getRollingPattern().isEmpty()) {
            params.put("pattern", profile.getRollingPattern());
        }
        try {
            return agentClient.extractDataMap(agentClient.getForMap(node, "/file/log/list", params));
        } catch (Exception e) {
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("files", java.util.Collections.singletonList(profile.getMainLogFile()));
            return fallback;
        }
    }

    public Map<String, Object> viewLog(Long projectId, Long nodeId, String fileName,
                                       int offset, int lines) {
        ProjectLogProfileModel profile = requireProfile(projectId);
        ProjectModel project = requireProject(projectId);
        NodeModel node = requireOnlineNode(nodeId);
        String logPath = logAggregateService.resolveLogPath(project, profile, fileName);

        Map<String, String> params = new HashMap<>();
        params.put("logPath", logPath);
        params.put("offset", String.valueOf(offset));
        params.put("lines", String.valueOf(lines));
        String content = agentClient.extractDataString(agentClient.getForMap(node, "/file/log", params));

        Map<String, Object> data = new HashMap<>();
        data.put("content", LogAggregateService.maskSensitive(content));
        data.put("logPath", logPath);
        data.put("offset", offset);
        data.put("lines", lines);
        return data;
    }

    public Map<String, Object> aggregate(Long projectId, List<Long> nodeIds,
                                           int page, int pageSize, Long since) {
        ProjectLogProfileModel profile = requireProfile(projectId);
        return logAggregateService.aggregate(projectId, nodeIds, profile, page, pageSize, since);
    }

    public Map<String, Object> search(Long projectId, String keyword, String scope,
                                      List<Long> nodeIds, int contextLines, int maxResults) {
        if (keyword == null || keyword.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "关键词不能为空");
        }
        ProjectLogProfileModel profile = requireProfile(projectId);
        return logSearchService.search(projectId, keyword, scope, nodeIds,
                contextLines, maxResults, profile);
    }

    private ProjectLogProfileModel requireProfile(Long projectId) {
        ProjectLogProfileModel profile = logProfileMapper.findByProjectId(projectId);
        if (profile == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "项目日志配置不存在，请先配置");
        }
        return profile;
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
}
