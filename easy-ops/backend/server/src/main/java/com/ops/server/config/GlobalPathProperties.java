package com.ops.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 全局运维路径配置（一次配置，全平台生效）
 */
@Component
@ConfigurationProperties(prefix = "ops.global")
public class GlobalPathProperties {

    /** 应用部署根目录，如 /home/stms/apps */
    private String deployBaseDir = "/app/data/apps";

    /** Agent 端数据根目录（Server 侧计算 Agent 路径时使用，需与 agent.data-path 一致） */
    private String agentDataPath = "/app/data";

    /** 日志子目录名（相对部署目录），默认 logs */
    private String logSubDir = "logs";

    /** 配置文件子目录名（相对部署目录），默认 config */
    private String configSubDir = "config";

    /** 前端静态资源部署目录（相对部署根目录下的应用名），默认 frontend */
    private String frontendSubDir = "frontend";

    public String getDeployBaseDir() {
        return deployBaseDir;
    }

    public void setDeployBaseDir(String deployBaseDir) {
        this.deployBaseDir = deployBaseDir;
    }

    public String getAgentDataPath() {
        return agentDataPath;
    }

    public void setAgentDataPath(String agentDataPath) {
        this.agentDataPath = agentDataPath;
    }

    /**
     * Agent 上某项目的版本包目录（与 FileController.receive 一致）
     */
    public String resolveAgentVersionDir(Long projectId, String version) {
        String base = normalizeDir(agentDataPath);
        if (version == null || version.isEmpty()) {
            return base + "/versions/" + projectId;
        }
        return base + "/versions/" + projectId + "/" + version;
    }

    private String normalizeDir(String dir) {
        if (dir == null || dir.isEmpty()) {
            return "/app/data";
        }
        return dir.endsWith("/") ? dir.substring(0, dir.length() - 1) : dir;
    }

    public String getLogSubDir() {
        return logSubDir;
    }

    public void setLogSubDir(String logSubDir) {
        this.logSubDir = logSubDir;
    }

    public String getConfigSubDir() {
        return configSubDir;
    }

    public void setConfigSubDir(String configSubDir) {
        this.configSubDir = configSubDir;
    }

    public String getFrontendSubDir() {
        return frontendSubDir;
    }

    public void setFrontendSubDir(String frontendSubDir) {
        this.frontendSubDir = frontendSubDir;
    }

    /**
     * 根据应用名生成默认部署目录
     */
    public String resolveDeployDir(String appName) {
        String slug = appName == null ? "app" : appName.toLowerCase().replaceAll("\\s+", "-");
        String base = deployBaseDir.endsWith("/") ? deployBaseDir.substring(0, deployBaseDir.length() - 1) : deployBaseDir;
        return base + "/" + slug;
    }

    /**
     * 根据部署目录生成默认日志目录
     */
    public String resolveLogDir(String deployDir) {
        if (deployDir == null || deployDir.isEmpty()) {
            return deployBaseDir + "/" + logSubDir;
        }
        return deployDir.endsWith("/") ? deployDir + logSubDir : deployDir + "/" + logSubDir;
    }

    /**
     * 根据部署目录生成默认配置目录
     */
    public String resolveConfigDir(String deployDir) {
        if (deployDir == null || deployDir.isEmpty()) {
            return deployBaseDir + "/" + configSubDir;
        }
        return deployDir.endsWith("/") ? deployDir + configSubDir : deployDir + "/" + configSubDir;
    }

    /**
     * 前端 dist 解压目标目录
     */
    public String resolveFrontendDir(String deployDir, String customFrontendDir) {
        if (customFrontendDir != null && !customFrontendDir.trim().isEmpty()) {
            return customFrontendDir.trim();
        }
        if (deployDir == null || deployDir.isEmpty()) {
            return deployBaseDir + "/" + frontendSubDir;
        }
        return deployDir.endsWith("/") ? deployDir + frontendSubDir : deployDir + "/" + frontendSubDir;
    }
}
