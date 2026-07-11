package com.ops.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * Server 启动时打印关键路径，便于私有化部署排错。
 */
@Component
@Order(1)
public class ServerStartupPathLogger implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ServerStartupPathLogger.class);

    @Value("${server.path:./data}")
    private String serverPath;

    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    @Value("${server.port:8081}")
    private int serverPort;

    @Autowired
    private GlobalPathProperties globalPathProperties;

    @Override
    public void run(ApplicationArguments args) {
        File dataDir = new File(serverPath).getAbsoluteFile();
        File versionsDir = new File(dataDir, "versions");
        File logsDir = new File(dataDir, "logs");

        log.info("========== EasyOps Server 启动路径 ==========");
        log.info("监听端口: {}", serverPort);
        log.info("数据根目录 server.path: {}", dataDir.getAbsolutePath());
        log.info("H2 数据源: {}", datasourceUrl);
        log.info("版本包目录: {}", versionsDir.getAbsolutePath());
        log.info("日志目录: {}", logsDir.getAbsolutePath());
        log.info("Agent 数据根目录 ops.global.agent-data-path: {}",
                new File(globalPathProperties.getAgentDataPath()).getAbsolutePath());
        log.info("应用部署根目录 ops.global.deploy-base-dir: {}",
                new File(globalPathProperties.getDeployBaseDir()).getAbsolutePath());
        log.info("工作目录 user.dir: {}", System.getProperty("user.dir"));
        log.info("=============================================");
    }
}
