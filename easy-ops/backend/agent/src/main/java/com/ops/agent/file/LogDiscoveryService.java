package com.ops.agent.file;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 智能扫描节点上的日志文件（不依赖固定文件名）。
 */
public class LogDiscoveryService {

    private static final int MAX_DEPTH = 2;
    private static final int MAX_FILES = 200;

    /**
     * 在部署目录及常见日志目录中扫描所有日志类文件。
     *
     * @param deployDir       应用部署根目录
     * @param configuredLogDir 项目配置的日志目录（可为绝对或相对路径）
     * @param agentDataPath   Agent 数据根目录，如 /app/data
     */
    public Map<String, Object> discover(String deployDir, String configuredLogDir, String agentDataPath)
            throws IOException {
        LinkedHashMap<String, Map<String, Object>> unique = new LinkedHashMap<String, Map<String, Object>>();
        List<String> scannedDirs = new ArrayList<String>();

        collectRoots(deployDir, configuredLogDir, agentDataPath).forEach(root -> {
            Path dir = Paths.get(root).toAbsolutePath().normalize();
            if (!Files.isDirectory(dir)) {
                return;
            }
            scannedDirs.add(dir.toString());
            try {
                scanDirectory(dir, dir, 0, unique);
            } catch (IOException ignored) {
                // 单目录失败跳过
            }
        });

        List<Map<String, Object>> files = new ArrayList<Map<String, Object>>(unique.values());
        files.sort(new Comparator<Map<String, Object>>() {
            @Override
            public int compare(Map<String, Object> a, Map<String, Object> b) {
                Long ta = (Long) a.get("lastModified");
                Long tb = (Long) b.get("lastModified");
                int cmp = tb.compareTo(ta);
                if (cmp != 0) {
                    return cmp;
                }
                Long sa = (Long) a.get("size");
                Long sb = (Long) b.get("size");
                return sb.compareTo(sa);
            }
        });

        if (files.size() > MAX_FILES) {
            files = files.subList(0, MAX_FILES);
        }

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("files", files);
        result.put("scannedDirs", scannedDirs);
        result.put("total", files.size());
        if (files.isEmpty()) {
            result.put("hint", "未在扫描目录中发现日志文件。Agent 平台日志目录: "
                    + join(agentDataPath != null ? agentDataPath : "./data", "logs")
                    + "；业务应用日志目录: {部署目录}/logs");
            result.put("agentLogDir", join(agentDataPath != null ? agentDataPath : "./data", "logs"));
        } else {
            result.put("suggestedMain", files.get(0).get("path"));
        }
        return result;
    }

    private List<String> collectRoots(String deployDir, String configuredLogDir, String agentDataPath) {
        List<String> roots = new ArrayList<String>();
        // Logback 实际写入目录（与 agent.data-path/logs 对齐，兼容 ./data/logs）
        roots.add(Paths.get("./data/logs").toAbsolutePath().normalize().toString());
        if (deployDir != null && !deployDir.trim().isEmpty()) {
            String base = deployDir.trim();
            roots.add(base);
            roots.add(join(base, "logs"));
            roots.add(join(base, "log"));
        }
        if (configuredLogDir != null && !configuredLogDir.trim().isEmpty()) {
            String cfg = configuredLogDir.trim();
            if (cfg.startsWith("/")) {
                roots.add(cfg);
            } else if (deployDir != null && !deployDir.trim().isEmpty()) {
                roots.add(join(deployDir.trim(), cfg));
            } else {
                roots.add(cfg);
            }
        }
        if (agentDataPath != null && !agentDataPath.trim().isEmpty()) {
            roots.add(join(agentDataPath.trim(), "logs"));
        }
        return dedupe(roots);
    }

    private void scanDirectory(Path root, Path dir, int depth, Map<String, Map<String, Object>> unique)
            throws IOException {
        File[] children = dir.toFile().listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isFile() && isLogLikeFile(child.getName())) {
                addFile(unique, child, root);
            }
        }
        if (depth >= MAX_DEPTH) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                String name = child.getName();
                if ("versions".equals(name) || "config".equals(name) || "frontend".equals(name)) {
                    continue;
                }
                scanDirectory(root, child.toPath(), depth + 1, unique);
            }
        }
    }

    private void addFile(Map<String, Map<String, Object>> unique, File file, Path root) {
        String path = file.getAbsolutePath();
        if (unique.containsKey(path)) {
            return;
        }
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("name", file.getName());
        item.put("path", path);
        item.put("size", file.length());
        item.put("lastModified", file.lastModified());
        item.put("sourceDir", file.getParent());
        item.put("category", classify(file, root));
        unique.put(path, item);
    }

    private String classify(File file, Path root) {
        String name = file.getName();
        String parent = file.getParent() != null ? file.getParent() : "";
        if (name.startsWith("agent") && name.endsWith(".log")) {
            return "agent";
        }
        if (parent.endsWith("/data/logs") || parent.endsWith("\\data\\logs")) {
            return "agent";
        }
        return "app";
    }

    static boolean isLogLikeFile(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        String lower = name.toLowerCase();
        if (lower.endsWith(".jar") || lower.endsWith(".zip") || lower.endsWith(".gz")) {
            return false;
        }
        return lower.endsWith(".log")
                || lower.endsWith(".out")
                || lower.contains(".log.")
                || "nohup.out".equals(lower)
                || lower.endsWith("-error")
                || (lower.contains("error") && (lower.endsWith(".log") || lower.endsWith(".txt")))
                || lower.startsWith("gc.log");
    }

    private List<String> dedupe(List<String> roots) {
        List<String> result = new ArrayList<String>();
        for (String root : roots) {
            if (root == null || root.trim().isEmpty()) {
                continue;
            }
            String norm = Paths.get(root.trim()).normalize().toString();
            if (!result.contains(norm)) {
                result.add(norm);
            }
        }
        return result;
    }

    private String join(String base, String relative) {
        if (base.endsWith("/")) {
            return base + relative;
        }
        return base + "/" + relative;
    }
}
