package com.ops.agent.file;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 日志文件列表、尾部读取与关键词搜索服务。
 */
public class LogFileService {

    /**
     * 列出日志目录下的文件（仅文件，按修改时间倒序）。
     */
    public List<Map<String, Object>> listLogs(String logDir) throws IOException {
        if (logDir == null || logDir.trim().isEmpty()) {
            throw new IOException("logDir 不能为空");
        }
        Path dir = Paths.get(logDir.trim()).toAbsolutePath().normalize();
        if (!Files.isDirectory(dir)) {
            throw new IOException("日志目录不存在: " + logDir);
        }

        File[] files = dir.toFile().listFiles();
        if (files == null) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }
            Map<String, Object> item = new HashMap<String, Object>();
            item.put("name", file.getName());
            item.put("path", file.getAbsolutePath());
            item.put("size", file.length());
            item.put("lastModified", file.lastModified());
            result.add(item);
        }
        Collections.sort(result, new Comparator<Map<String, Object>>() {
            @Override
            public int compare(Map<String, Object> a, Map<String, Object> b) {
                Long ta = (Long) a.get("lastModified");
                Long tb = (Long) b.get("lastModified");
                return tb.compareTo(ta);
            }
        });
        return result;
    }

    /**
     * 读取日志文件尾部 N 行。
     */
    public Map<String, Object> tail(String logPath, int lines) throws IOException {
        Path path = validateLogPath(logPath);
        if (!Files.exists(path)) {
            throw new IOException("日志文件不存在: " + logPath);
        }
        int lineCount = Math.max(lines, 1);
        List<String> allLines = Files.readAllLines(path, StandardCharsets.UTF_8);
        int from = Math.max(0, allLines.size() - lineCount);
        List<String> tailLines = allLines.subList(from, allLines.size());

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("logPath", path.toString());
        result.put("totalLines", allLines.size());
        result.put("lines", tailLines);
        result.put("content", joinLines(tailLines));
        return result;
    }

    /**
     * 在日志文件中搜索关键词，返回匹配行及上下文。
     */
    public Map<String, Object> search(String logPath, String keyword, int maxResults, int contextLines)
            throws IOException {
        if (keyword == null || keyword.trim().isEmpty()) {
            throw new IOException("keyword 不能为空");
        }
        Path path = validateLogPath(logPath);
        if (!Files.exists(path)) {
            throw new IOException("日志文件不存在: " + logPath);
        }

        int max = Math.max(maxResults, 1);
        int context = Math.max(contextLines, 0);
        List<String> allLines = readAllLines(path.toString());
        List<Map<String, Object>> matches = new ArrayList<Map<String, Object>>();

        for (int i = 0; i < allLines.size() && matches.size() < max; i++) {
            String line = allLines.get(i);
            if (!line.contains(keyword)) {
                continue;
            }
            Map<String, Object> match = new HashMap<String, Object>();
            match.put("lineNo", i + 1);
            match.put("matchedLine", line);
            match.put("contextBefore", subList(allLines, Math.max(0, i - context), i));
            match.put("contextAfter", subList(allLines, i + 1, Math.min(allLines.size(), i + 1 + context)));
            matches.add(match);
        }

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("logPath", path.toString());
        result.put("keyword", keyword);
        result.put("matchCount", matches.size());
        result.put("matches", matches);
        return result;
    }

    private List<String> subList(List<String> lines, int from, int to) {
        if (from >= to) {
            return Collections.emptyList();
        }
        return new ArrayList<String>(lines.subList(from, to));
    }

    private List<String> readAllLines(String filePath) throws IOException {
        List<String> lines = new ArrayList<String>();
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(filePath));
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                    // ignore
                }
            }
        }
        return lines;
    }

    private String joinLines(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(lines.get(i));
        }
        return sb.toString();
    }

    private Path validateLogPath(String logPath) throws IOException {
        if (logPath == null || logPath.trim().isEmpty()) {
            throw new IOException("logPath 不能为空");
        }
        return Paths.get(logPath.trim()).toAbsolutePath().normalize();
    }
}
