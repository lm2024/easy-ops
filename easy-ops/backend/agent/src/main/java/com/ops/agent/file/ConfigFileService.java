package com.ops.agent.file;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置文件读写与备份服务（yml/yaml）。
 */
public class ConfigFileService {

    /**
     * 读取配置文件内容。
     */
    public String readConfig(String configPath) throws IOException {
        Path path = validateConfigPath(configPath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    /**
     * 写入配置文件，可选先备份。
     *
     * @return backupPath（若执行了备份）
     */
    public Map<String, Object> writeConfig(String configPath, String content, boolean backup)
            throws IOException {
        Path path = validateConfigPath(configPath);
        File parent = path.getParent().toFile();
        if (!parent.exists() && !parent.mkdirs()) {
            throw new IOException("无法创建配置目录: " + parent.getAbsolutePath());
        }

        Map<String, Object> result = new HashMap<String, Object>();
        if (backup && Files.exists(path)) {
            String backupPath = backupConfig(configPath).get("backupPath").toString();
            result.put("backupPath", backupPath);
        }

        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
        result.put("configPath", path.toString());
        result.put("size", content.getBytes(StandardCharsets.UTF_8).length);
        return result;
    }

    /**
     * 备份配置文件到同级 .backup/{timestamp}/ 目录。
     */
    public Map<String, Object> backupConfig(String configPath) throws IOException {
        Path source = validateConfigPath(configPath);
        if (!Files.exists(source)) {
            throw new IOException("配置文件不存在: " + configPath);
        }

        String timestamp = new SimpleDateFormat("yyyyMMddHHmmssSSS").format(new Date());
        Path backupDir = source.getParent().resolve(".backup").resolve(timestamp);
        Files.createDirectories(backupDir);
        Path target = backupDir.resolve(source.getFileName().toString());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("backupPath", target.toString());
        result.put("backupDir", backupDir.toString());
        result.put("timestamp", timestamp);
        return result;
    }

    private Path validateConfigPath(String configPath) throws IOException {
        if (configPath == null || configPath.trim().isEmpty()) {
            throw new IOException("configPath 不能为空");
        }
        String trimmed = configPath.trim();
        if (!trimmed.endsWith(".yml") && !trimmed.endsWith(".yaml")) {
            throw new IOException("仅支持 .yml 和 .yaml 配置文件");
        }
        Path path = Paths.get(trimmed).toAbsolutePath().normalize();
        if (path.toString().contains(".." + File.separator) || trimmed.contains("..")) {
            throw new IOException("配置文件路径非法");
        }
        return path;
    }

    /**
     * 扫描指定目录下的配置文件，按 Spring Boot 外部配置加载优先级：
     * 1. {deployDir}/config/ 子目录（递归，最高优先级）
     * 2. {deployDir}/ 根目录（仅第一层，不含子目录）
     */
    public List<Map<String, Object>> discoverConfigs(String deployDir) throws IOException {
        String baseDir = deployDir != null ? deployDir.trim() : "";
        if (baseDir.isEmpty()) {
            throw new IOException("deployDir 不能为空");
        }
        Path basePath = Paths.get(baseDir).toAbsolutePath().normalize();
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        
        // 1. 扫描 {deployDir}/config/（Spring Boot 外部配置最高优先级）
        Path configDir = basePath.resolve("config");
        if (Files.exists(configDir) && Files.isDirectory(configDir)) {
            scanConfigDir(configDir, configDir, "config/", result);
        }
        
        // 2. 扫描 {deployDir}/ 根目录（仅第一层）
        scanConfigDir(basePath, basePath, "", result);
        
        return result;
    }

    private void scanConfigDir(Path root, Path dir, String prefix, List<Map<String, Object>> result) throws IOException {
        File[] files = dir.toFile().listFiles();
        if (files == null) return;
        boolean isRootScan = prefix.isEmpty();
        for (File f : files) {
            if (f.isDirectory()) {
                // config/ 内子目录递归扫描；根目录不递归
                if (!isRootScan && !f.getName().startsWith(".")) {
                    scanConfigDir(root, f.toPath(), prefix + f.getName() + "/", result);
                }
            } else if (isConfigFile(f.getName())) {
                String fullPath = f.getAbsolutePath();
                String relativePath = prefix + f.getName();
                Map<String, Object> item = new LinkedHashMap<String, Object>();
                item.put("fileName", f.getName());
                item.put("relativePath", relativePath);
                item.put("fullPath", fullPath);
                item.put("size", f.length());
                result.add(item);
            }
        }
    }

    private boolean isConfigFile(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".yml") || lower.endsWith(".yaml")
                || lower.endsWith(".properties") || lower.endsWith(".conf");
    }
}
