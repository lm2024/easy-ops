package com.ops.agent.file;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
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
}
