package com.ops.demo.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
public class DemoController {

    @Value("${demo.app.name:DemoApp}")
    private String appName;

    @Value("${demo.app.version:1.0.0}")
    private String appVersion;

    @Value("${demo.app.description:A simple Spring Boot demo app}")
    private String appDescription;

    /**
     * GET /hello - 验证部署成功
     */
    @GetMapping("/hello")
    public Map<String, Object> hello() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Hello World!");
        result.put("appName", appName);
        result.put("version", appVersion);
        result.put("status", "DEPLOYED");
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }

    /**
     * GET /config - 显示当前 application.yml 配置内容
     */
    @GetMapping("/config")
    public Map<String, Object> showConfig() throws Exception {
        Map<String, Object> result = new HashMap<>();
        result.put("appName", appName);
        result.put("version", appVersion);
        result.put("description", appDescription);

        // 读取 application.yml 原始内容
        ClassPathResource resource = new ClassPathResource("application.yml");
        try (InputStream is = resource.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String ymlContent = reader.lines().collect(Collectors.joining("\n"));
            result.put("ymlContent", ymlContent);
        }

        return result;
    }

    /**
     * GET /health - 健康检查
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "UP");
        result.put("appName", appName);
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }
}
