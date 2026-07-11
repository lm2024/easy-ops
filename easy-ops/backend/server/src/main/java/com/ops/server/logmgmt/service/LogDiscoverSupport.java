package com.ops.server.logmgmt.service;

import com.ops.common.model.NodeModel;
import com.ops.common.model.ProjectLogProfileModel;
import com.ops.common.model.ProjectModel;
import com.ops.server.client.AgentClient;
import com.ops.server.config.GlobalPathProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 节点日志发现（扫描目录与文件列表），供查看/聚合/搜索共用。
 */
@Service
public class LogDiscoverSupport {

    public static final int MAX_FILES_PER_NODE = 10;

    @Autowired
    private AgentClient agentClient;

    @Autowired
    private GlobalPathProperties globalPathProperties;

    /**
     * 调用 Agent 发现日志文件。
     */
    public Map<String, Object> discover(NodeModel node, ProjectModel project, ProjectLogProfileModel profile) {
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
        data.put("deployDir", deployDir);
        return data;
    }

    /**
     * 获取节点上发现的日志文件列表（已按修改时间排序）。
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listFiles(NodeModel node, ProjectModel project,
                                                ProjectLogProfileModel profile) {
        Map<String, Object> data = discover(node, project, profile);
        Object filesObj = data.get("files");
        if (!(filesObj instanceof List)) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> files = (List<Map<String, Object>>) filesObj;
        if (files.size() <= MAX_FILES_PER_NODE) {
            return files;
        }
        return files.subList(0, MAX_FILES_PER_NODE);
    }

    /**
     * 构建节点扫描范围说明（返回给前端展示）。
     */
    public Map<String, Object> buildNodeScope(NodeModel node, ProjectModel project,
                                               ProjectLogProfileModel profile) {
        Map<String, Object> discoverData = discover(node, project, profile);
        List<Map<String, Object>> files = listFilesFromDiscover(discoverData);

        List<Map<String, Object>> fileBriefs = new ArrayList<>();
        for (Map<String, Object> file : files) {
            Map<String, Object> brief = new HashMap<>();
            brief.put("name", file.get("name"));
            brief.put("path", file.get("path"));
            brief.put("sourceDir", file.get("sourceDir"));
            brief.put("category", file.get("category"));
            brief.put("size", file.get("size"));
            fileBriefs.add(brief);
        }

        Map<String, Object> scope = new HashMap<>();
        scope.put("nodeId", node.getId());
        scope.put("nodeName", node.getName());
        scope.put("deployDir", discoverData.get("deployDir"));
        scope.put("agentLogDir", discoverData.get("agentLogDir"));
        scope.put("scannedDirs", discoverData.get("scannedDirs"));
        scope.put("files", fileBriefs);
        scope.put("fileCount", fileBriefs.size());
        return scope;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listFilesFromDiscover(Map<String, Object> discoverData) {
        Object filesObj = discoverData.get("files");
        if (!(filesObj instanceof List)) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> files = (List<Map<String, Object>>) filesObj;
        if (files.size() <= MAX_FILES_PER_NODE) {
            return files;
        }
        return files.subList(0, MAX_FILES_PER_NODE);
    }

    public String resolveDeployDir(ProjectModel project) {
        String deployDir = project != null ? project.getDeployDir() : null;
        if (deployDir == null || deployDir.trim().isEmpty()) {
            deployDir = globalPathProperties.resolveDeployDir(
                    project != null ? project.getName() : "app");
        }
        return deployDir;
    }

    public String resolveAppLogDir(ProjectModel project) {
        return globalPathProperties.resolveLogDir(resolveDeployDir(project));
    }
}
