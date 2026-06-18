package com.ops.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Agent启动类 - 运行在目标服务器上
 * 负责心跳上报、进程管理、文件接收、日志上报
 */
@SpringBootApplication
@EnableScheduling
public class AgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
    }
}
