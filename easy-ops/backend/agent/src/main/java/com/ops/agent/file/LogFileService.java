package com.ops.agent.file;

import com.ops.common.util.LogLevelUtil;

import java.io.File;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * 日志文件列表、分页读取、尾部读取与关键词搜索服务。
 */
public class LogFileService {

    private static final int MAX_TAIL_LINES = 5000;
    private static final int MAX_PAGE_LINES = 2000;

    /**
     * 列出日志目录下的文件（可选 glob 过滤），按修改时间倒序。
     */
    public List<Map<String, Object>> listLogs(String logDir, String pattern) throws IOException {
        if (logDir == null || logDir.trim().isEmpty()) {
            throw new IOException("logDir 不能为空");
        }
        Path dir = Paths.get(logDir.trim()).toAbsolutePath().normalize();
        if (!Files.isDirectory(dir)) {
            throw new IOException("日志目录不存在: " + logDir);
        }

        String glob = normalizePattern(pattern);
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        File[] files = dir.toFile().listFiles();
        if (files == null) {
            return result;
        }
        java.nio.file.PathMatcher matcher = dir.getFileSystem().getPathMatcher("glob:" + glob);
        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }
            String name = file.getName();
            if (!matcher.matches(dir.getFileSystem().getPath(name))) {
                continue;
            }
            Map<String, Object> item = new HashMap<String, Object>();
            item.put("name", name);
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
     * 分页读取日志（流式，不加载全文件）。
     */
    public Map<String, Object> readPage(String logPath, int offset, int lines, String level)
            throws IOException {
        Path path = validateLogPath(logPath);
        if (!Files.exists(path)) {
            throw new IOException("日志文件不存在: " + logPath);
        }
        int lineCount = Math.min(Math.max(lines, 1), MAX_PAGE_LINES);
        int skip = Math.max(offset, 0);

        List<String> pageLines = new ArrayList<String>();
        int matched = 0;
        boolean hasMore = false;

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!LogLevelUtil.matches(line, level)) {
                    continue;
                }
                if (matched < skip) {
                    matched++;
                    continue;
                }
                if (pageLines.size() < lineCount) {
                    pageLines.add(line);
                } else {
                    hasMore = true;
                    break;
                }
                matched++;
            }
            if (pageLines.size() == lineCount) {
                String peek;
                while ((peek = reader.readLine()) != null) {
                    if (LogLevelUtil.matches(peek, level)) {
                        hasMore = true;
                        break;
                    }
                }
            }
        }

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("logPath", path.toString());
        result.put("offset", offset);
        result.put("lines", pageLines);
        result.put("content", joinLines(pageLines));
        result.put("hasMore", hasMore);
        return result;
    }

    /**
     * 从文件尾部高效读取 N 行（支持级别过滤）。
     */
    public Map<String, Object> tail(String logPath, int lines, String level) throws IOException {
        Path path = validateLogPath(logPath);
        if (!Files.exists(path)) {
            throw new IOException("日志文件不存在: " + logPath);
        }
        int lineCount = Math.min(Math.max(lines, 1), MAX_TAIL_LINES);
        List<String> rawTail = readTailLines(path, lineCount * 4);
        List<String> filtered = new ArrayList<String>();
        for (int i = rawTail.size() - 1; i >= 0 && filtered.size() < lineCount; i--) {
            String line = rawTail.get(i);
            if (LogLevelUtil.matches(line, level)) {
                filtered.add(0, line);
            }
        }
        if (filtered.size() < lineCount) {
            filtered = collectTailWithFilter(path, lineCount, level);
        }

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("logPath", path.toString());
        result.put("totalLines", filtered.size());
        result.put("lines", filtered);
        result.put("content", joinLines(filtered));
        result.put("mode", "tail");
        return result;
    }

    /**
     * 在日志文件中搜索关键词，返回匹配行及上下文。
     */
    public Map<String, Object> search(String logPath, String keyword, int maxResults,
                                      int contextLines, String level) throws IOException {
        if (keyword == null || keyword.trim().isEmpty()) {
            throw new IOException("keyword 不能为空");
        }
        Path path = validateLogPath(logPath);
        if (!Files.exists(path)) {
            throw new IOException("日志文件不存在: " + logPath);
        }

        int max = Math.max(maxResults, 1);
        int context = Math.max(contextLines, 0);
        List<Map<String, Object>> matches = new ArrayList<Map<String, Object>>();

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            java.util.ArrayDeque<String> before = new java.util.ArrayDeque<String>();
            String line;
            int lineNo = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                before.addLast(line);
                if (before.size() > context + 1) {
                    before.removeFirst();
                }
                if (!LogLevelUtil.matches(line, level) || !line.contains(keyword)) {
                    continue;
                }
                Map<String, Object> match = new HashMap<String, Object>();
                match.put("lineNo", lineNo);
                match.put("matchedLine", line);
                List<String> ctxBefore = new ArrayList<String>(before);
                if (!ctxBefore.isEmpty()) {
                    ctxBefore.remove(ctxBefore.size() - 1);
                }
                match.put("contextBefore", ctxBefore);

                List<String> ctxAfter = new ArrayList<String>();
                for (int j = 0; j < context; j++) {
                    String next = reader.readLine();
                    if (next == null) {
                        break;
                    }
                    lineNo++;
                    ctxAfter.add(next);
                    before.addLast(next);
                    if (before.size() > context + 1) {
                        before.removeFirst();
                    }
                }
                match.put("contextAfter", ctxAfter);
                matches.add(match);
                if (matches.size() >= max) {
                    break;
                }
            }
        }

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("logPath", path.toString());
        result.put("keyword", keyword);
        result.put("matchCount", matches.size());
        result.put("matches", matches);
        result.put("hits", matches);
        return result;
    }

    private List<String> collectTailWithFilter(Path path, int lineCount, String level)
            throws IOException {
        List<String> all = new ArrayList<String>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (LogLevelUtil.matches(line, level)) {
                    all.add(line);
                }
            }
        }
        int from = Math.max(0, all.size() - lineCount);
        return new ArrayList<String>(all.subList(from, all.size()));
    }

    private List<String> readTailLines(Path path, int maxLines) throws IOException {
        long fileSize = Files.size(path);
        if (fileSize == 0) {
            return Collections.emptyList();
        }
        int chunkSize = (int) Math.min(fileSize, 64 * 1024);
        LinkedList<String> lines = new LinkedList<String>();
        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
            long pointer = fileSize;
            StringBuilder fragment = new StringBuilder();
            while (pointer > 0 && lines.size() < maxLines) {
                long readPos = Math.max(0, pointer - chunkSize);
                int len = (int) (pointer - readPos);
                raf.seek(readPos);
                byte[] buffer = new byte[len];
                raf.readFully(buffer);
                String chunk = new String(buffer, StandardCharsets.UTF_8);
                fragment.insert(0, chunk);
                pointer = readPos;

                int idx = fragment.length();
                while (idx > 0 && lines.size() < maxLines) {
                    int lineEnd = fragment.lastIndexOf("\n", idx - 1);
                    if (lineEnd < 0) {
                        break;
                    }
                    String line = fragment.substring(lineEnd + 1, idx);
                    if (!line.isEmpty()) {
                        lines.addFirst(line);
                    }
                    idx = lineEnd;
                }
                if (pointer == 0 && idx > 0) {
                    String first = fragment.substring(0, idx);
                    if (!first.isEmpty()) {
                        lines.addFirst(first);
                    }
                }
                fragment = new StringBuilder(fragment.substring(0, Math.max(0, idx)));
            }
        }
        return new ArrayList<String>(lines);
    }

    private String normalizePattern(String pattern) {
        if (pattern == null || pattern.trim().isEmpty()) {
            return "*.log";
        }
        String p = pattern.trim();
        if (p.startsWith("glob:")) {
            return p.substring(5);
        }
        return p;
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
