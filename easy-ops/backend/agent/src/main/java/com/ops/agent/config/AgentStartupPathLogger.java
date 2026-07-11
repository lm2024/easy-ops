package com.ops.agent.config;

import com.ops.agent.upgrade.AgentUpgradeLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * Agent 启动时打印关键路径，便于私有化部署排错。
 */
@Component
@Order(1)
public class AgentStartupPathLogger implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AgentStartupPathLogger.class);

    @Value("${agent.data-path:/app/data}")
    private String dataPath;

    @Value("${agent.jar-path:/app/agent.jar}")
    private String jarPath;

    @Value("${agent.server-url:}")
    private String serverUrl;

    @Value("${agent.node-name:}")
    private String nodeName;

    @Value("${server.port:2123}")
    private int agentPort;

    @Value("${agent.restart-mode:auto}")
    private String restartMode;

    @Value("${agent.restart-script:}")
    private String restartScriptPath;

    @Override
    public void run(ApplicationArguments args) {
        File dataDir = new File(dataPath).getAbsoluteFile();
        File versionsDir = new File(dataDir, "versions");
        File logsDir = new File(dataDir, "logs");
        File upgradeLog = new File(logsDir, AgentUpgradeLog.LOG_FILE_NAME);
        File jarFile = new File(jarPath).getAbsoluteFile();

        log.info("========== EasyOps Agent 启动路径 ==========");
        log.info("节点名称: {}", nodeName);
        log.info("监听端口: {}", agentPort);
        log.info("Server 地址: {}", serverUrl);
        log.info("数据根目录 agent.data-path: {}", dataDir.getAbsolutePath());
        log.info("版本包目录: {}", versionsDir.getAbsolutePath());
        log.info("日志目录: {}", logsDir.getAbsolutePath());
        log.info("Jar 路径 agent.jar-path: {}", jarFile.getAbsolutePath());
        log.info("重启模式 agent.restart-mode: {}", restartMode);
        log.info("重启脚本 agent.restart-script: {}", restartScriptPath);
        log.info("升级专用日志: {}", upgradeLog.getAbsolutePath());
        log.info("工作目录 user.dir: {}", System.getProperty("user.dir"));
        log.info("============================================");
    }
}
