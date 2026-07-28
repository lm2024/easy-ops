package com.ops.agent.file;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 脚本文件服务（支持任意文件类型的读写、备份、扫描）
 * 与 ConfigFileService 不同，此服务支持任意目录和文件类型
 */
public class ScriptFileService {

    /**
     * 读取脚本文件内容
     */
    public String readScript(String filePath) throws IOException {
        Path path = validateAndNormalizePath(filePath);
        if (!Files.exists(path)) {
            throw new IOException("脚本文件不存在: " + filePath);
        }
        if (!Files.isRegularFile(path)) {
            throw new IOException("路径不是文件: " + filePath);
        }
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    /**
     * 写入脚本文件，可选先备份、设置可执行权限
     *
     * @return 操作结果
     */
    public Map<String, Object> writeScript(String filePath, String content, boolean backup, boolean setExecutable)
            throws IOException {
        Path path = validateAndNormalizePath(filePath);
        File parent = path.getParent().toFile();
        if (!parent.exists() && !parent.mkdirs()) {
            throw new IOException("无法创建目录: " + parent.getAbsolutePath());
        }

        Map<String, Object> result = new HashMap<String, Object>();
        if (backup && Files.exists(path)) {
            String backupPath = backupScript(filePath).get("backupPath").toString();
            result.put("backupPath", backupPath);
        }

        Files.write(path, content.getBytes(StandardCharsets.UTF_8));

        // 设置可执行权限
        if (setExecutable) {
            try {
                // 尝试设置 POSIX 权限（Linux/Mac）
                Set<PosixFilePermission> perms = new HashSet<PosixFilePermission>();
                perms.add(PosixFilePermission.OWNER_READ);
                perms.add(PosixFilePermission.OWNER_WRITE);
                perms.add(PosixFilePermission.OWNER_EXECUTE);
                perms.add(PosixFilePermission.GROUP_READ);
                perms.add(PosixFilePermission.GROUP_EXECUTE);
                perms.add(PosixFilePermission.OTHERS_READ);
                perms.add(PosixFilePermission.OTHERS_EXECUTE);
                Files.setPosixFilePermissions(path, perms);
                result.put("fileMode", 755);
            } catch (UnsupportedOperationException e) {
                // Windows 系统，使用 File.setExecutable
                path.toFile().setExecutable(true, false);
                result.put("fileMode", 755);
                result.put("note", "Windows 系统使用 setExecutable");
            }
        }

        result.put("filePath", path.toString());
        result.put("size", content.getBytes(StandardCharsets.UTF_8).length);
        return result;
    }

    /**
     * 备份脚本文件到同级 .backup/{timestamp}/ 目录
     */
    public Map<String, Object> backupScript(String filePath) throws IOException {
        Path source = validateAndNormalizePath(filePath);
        if (!Files.exists(source)) {
            throw new IOException("脚本文件不存在: " + filePath);
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

    /**
     * 获取文件状态信息
     */
    public Map<String, Object> getFileStatus(String filePath) throws IOException {
        Path path = validateAndNormalizePath(filePath);
        Map<String, Object> status = new HashMap<String, Object>();

        if (!Files.exists(path)) {
            status.put("exists", false);
            return status;
        }

        status.put("exists", true);
        status.put("isFile", Files.isRegularFile(path));
        status.put("isDirectory", Files.isDirectory(path));
        status.put("isExecutable", Files.isExecutable(path));
        status.put("isReadable", Files.isReadable(path));
        status.put("isWritable", Files.isWritable(path));
        status.put("size", Files.size(path));
        status.put("lastModified", Files.getLastModifiedTime(path).toMillis());

        try {
            // 尝试获取 POSIX 权限（Linux/Mac）
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(path);
            status.put("posixPermissions", permsToString(perms));
        } catch (UnsupportedOperationException e) {
            // Windows 系统
            status.put("posixPermissions", "N/A (Windows)");
        }

        return status;
    }

    /**
     * 扫描指定目录下的脚本文件（支持任意文件类型）
     * 
     * @param scanDir 扫描目录
     * @param maxDepth 最大扫描深度（默认 3）
     * @param maxFiles 最大文件数（默认 500）
     */
    public List<Map<String, Object>> discoverScripts(String scanDir, int maxDepth, int maxFiles) throws IOException {
        String baseDir = scanDir != null ? scanDir.trim() : "";
        if (baseDir.isEmpty()) {
            throw new IOException("scanDir 不能为空");
        }
        Path basePath = Paths.get(baseDir).toAbsolutePath().normalize();
        if (!Files.exists(basePath)) {
            throw new IOException("扫描目录不存在: " + baseDir);
        }
        if (!Files.isDirectory(basePath)) {
            throw new IOException("路径不是目录: " + baseDir);
        }

        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        scanDirectory(basePath, basePath, "", maxDepth > 0 ? maxDepth : 3, maxFiles > 0 ? maxFiles : 500, result);
        return result;
    }

    /**
     * 递归扫描目录
     */
    private void scanDirectory(Path root, Path dir, String relativePrefix, int maxDepth, int maxFiles,
                               List<Map<String, Object>> result) throws IOException {
        if (result.size() >= maxFiles) return;
        if (maxDepth <= 0) return;

        File[] files = dir.toFile().listFiles();
        if (files == null) return;

        for (File f : files) {
            if (result.size() >= maxFiles) break;

            if (f.isDirectory()) {
                // 跳过隐藏目录和备份目录
                if (!f.getName().startsWith(".") && !f.getName().equals("backup")
                        && !f.getName().equals(".backup") && !f.getName().equals("node_modules")) {
                    String subPrefix = relativePrefix.isEmpty() ? f.getName() : relativePrefix + "/" + f.getName();
                    scanDirectory(root, f.toPath(), subPrefix, maxDepth - 1, maxFiles, result);
                }
            } else if (f.isFile() && !f.getName().startsWith(".")) {
                String relativePath = relativePrefix.isEmpty() ? f.getName() : relativePrefix + "/" + f.getName();
                Map<String, Object> item = new LinkedHashMap<String, Object>();
                item.put("fileName", f.getName());
                item.put("filePath", f.getAbsolutePath());
                item.put("relativePath", relativePath);
                item.put("size", f.length());
                item.put("lastModified", f.lastModified());
                item.put("isExecutable", f.canExecute());
                result.add(item);
            }
        }
    }

    /**
     * 验证和规范化路径
     */
    private Path validateAndNormalizePath(String filePath) throws IOException {
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IOException("文件路径不能为空");
        }
        String trimmed = filePath.trim();
        Path path = Paths.get(trimmed).toAbsolutePath().normalize();

        // 安全检查：防止路径遍历攻击
        if (trimmed.contains("..")) {
            // 检查规范化后的路径是否仍然包含 ..
            String normalized = path.toString();
            if (normalized.contains(".." + File.separator) || normalized.endsWith("..")) {
                throw new IOException("文件路径非法");
            }
        }

        return path;
    }

    /**
     * 将 POSIX 权限集合转换为八进制字符串
     */
    private String permsToString(Set<PosixFilePermission> perms) {
        int owner = 0, group = 0, others = 0;
        if (perms.contains(PosixFilePermission.OWNER_READ)) owner += 4;
        if (perms.contains(PosixFilePermission.OWNER_WRITE)) owner += 2;
        if (perms.contains(PosixFilePermission.OWNER_EXECUTE)) owner += 1;
        if (perms.contains(PosixFilePermission.GROUP_READ)) group += 4;
        if (perms.contains(PosixFilePermission.GROUP_WRITE)) group += 2;
        if (perms.contains(PosixFilePermission.GROUP_EXECUTE)) group += 1;
        if (perms.contains(PosixFilePermission.OTHERS_READ)) others += 4;
        if (perms.contains(PosixFilePermission.OTHERS_WRITE)) others += 2;
        if (perms.contains(PosixFilePermission.OTHERS_EXECUTE)) others += 1;
        return "" + owner + group + others;
    }
}
