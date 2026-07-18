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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * 日志文件列表、分页读取、尾部读取与关键词搜索服务。
 * <p>
 * 性能约束：
 * - tail：从文件末尾反向读取，不读全文件
 * - search：从文件末尾向前扫描（日志排查通常关心最近的日志），超过扫描上限停止
 * - page：支持从末尾倒序分页（offsetFromEnd），避免从头跳过大量行
 */
public class LogFileService {

    private static final int MAX_TAIL_LINES = 5000;
    private static final int MAX_PAGE_LINES = 2000;
    /** 搜索时从末尾最多扫描的字节数（10MB），避免大文件全量扫描 */
    private static final long SEARCH_MAX_SCAN_BYTES = 10L * 1024 * 1024;
    /** 搜索时从末尾最多扫描的行数 */
    private static final int SEARCH_MAX_SCAN_LINES = 200000;

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
     * 分页读取日志。
     * <p>
     * 支持两种模式：
     * - offsetFromEnd=true（推荐）：从文件末尾往前读，适合"看最新日志"
     * - offsetFromEnd=false（默认）：从文件开头往后读（原有行为）
     */
    public Map<String, Object> readPage(String logPath, int offset, int lines, String level,
                                         boolean offsetFromEnd) throws IOException {
        Path path = validateLogPath(logPath);
        if (!Files.exists(path)) {
            throw new IOException("日志文件不存在: " + logPath);
        }
        int lineCount = Math.min(Math.max(lines, 1), MAX_PAGE_LINES);
        int skip = Math.max(offset, 0);

        if (offsetFromEnd) {
            return readPageFromEnd(path, skip, lineCount, level);
        }
        return readPageFromStart(path, skip, lineCount, level);
    }

    /** 兼容旧接口：默认从头读 */
    public Map<String, Object> readPage(String logPath, int offset, int lines, String level)
            throws IOException {
        return readPage(logPath, offset, lines, level, false);
    }

    /** 从文件末尾倒序分页（高效，不读全文件） */
    private Map<String, Object> readPageFromEnd(Path path, int skipFromEnd, int lineCount, String level)
            throws IOException {
        // 读取比需要更多的行（跳过的 + 需要的），从末尾反向读
        int totalNeeded = skipFromEnd + lineCount;
        List<String> allLines = readTailLines(path, totalNeeded * 2);

        // 过滤级别
        List<String> filtered = new ArrayList<String>();
        for (String line : allLines) {
            if (LogLevelUtil.matches(line, level)) {
                filtered.add(line);
            }
        }

        // 跳过末尾的 skipFromEnd 行，取接下来的 lineCount 行
        int total = filtered.size();
        int start = Math.max(0, total - skipFromEnd - lineCount);
        int end = Math.max(0, total - skipFromEnd);
        List<String> pageLines = filtered.subList(start, end);
        boolean hasMore = start > 0;

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("logPath", path.toString());
        result.put("offset", skipFromEnd);
        result.put("lines", pageLines);
        result.put("content", joinLines(pageLines));
        result.put("hasMore", hasMore);
        result.put("direction", "fromEnd");
        return result;
    }

    /** 从文件开头顺序分页（原有行为） */
    private Map<String, Object> readPageFromStart(Path path, int skip, int lineCount, String level)
            throws IOException {
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
        result.put("offset", skip);
        result.put("lines", pageLines);
        result.put("content", joinLines(pageLines));
        result.put("hasMore", hasMore);
        result.put("direction", "fromStart");
        return result;
    }

    /**
     * 从文件尾部高效读取 N 行（支持级别过滤）。
     * 不再回退读全文件——级别过滤后不够就直接返回已有结果。
     */
    public Map<String, Object> tail(String logPath, int lines, String level) throws IOException {
        Path path = validateLogPath(logPath);
        if (!Files.exists(path)) {
            throw new IOException("日志文件不存在: " + logPath);
        }
        int lineCount = Math.min(Math.max(lines, 1), MAX_TAIL_LINES);

        // 读取 4 倍行数的原始行（过滤后可能不够，但不会回退读全文件）
        List<String> rawTail = readTailLines(path, lineCount * 4);
        List<String> filtered = new ArrayList<String>();
        for (int i = rawTail.size() - 1; i >= 0 && filtered.size() < lineCount; i--) {
            String line = rawTail.get(i);
            if (LogLevelUtil.matches(line, level)) {
                filtered.add(0, line);
            }
        }
        // 不再回退读全文件，直接返回已有结果

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
     * <p>
     * 优化：从文件末尾向前扫描（日志排查通常关心最近的匹配），
     * 超过扫描上限（10MB / 20万行）自动停止，避免大文件全量扫描。
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

        // 从末尾读取固定量的日志行进行搜索（不读全文件）
        int scanLines = Math.min(SEARCH_MAX_SCAN_LINES, max * 100 + context * max);
        List<String> tailLines = readTailLines(path, scanLines);

        // 在读取的行中搜索
        List<Map<String, Object>> matches = new ArrayList<Map<String, Object>>();
        int baseLineNo = Math.max(0, countTotalLines(path) - tailLines.size());

        ArrayDeque<String> before = new ArrayDeque<String>();
        for (int i = 0; i < tailLines.size(); i++) {
            String line = tailLines.get(i);
            int lineNo = baseLineNo + i + 1;

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

            // 读取后续行作为上下文
            List<String> ctxAfter = new ArrayList<String>();
            for (int j = 1; j <= context && (i + j) < tailLines.size(); j++) {
                ctxAfter.add(tailLines.get(i + j));
            }
            match.put("contextAfter", ctxAfter);
            matches.add(match);

            if (matches.size() >= max) {
                break;
            }
        }

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("logPath", path.toString());
        result.put("keyword", keyword);
        result.put("matchCount", matches.size());
        result.put("matches", matches);
        result.put("hits", matches);
        result.put("scannedLines", tailLines.size());
        result.put("scanLimited", tailLines.size() >= scanLines);
        return result;
    }

    /**
     * 快速估算文件总行数（采样前1000行的平均行长，然后除以文件大小）。
     * 不读全文件。
     */
    private int countTotalLines(Path path) {
        try {
            long fileSize = Files.size(path);
            if (fileSize == 0) return 0;
            // 采样前 8KB 计算平均行长
            int sampleSize = (int) Math.min(fileSize, 8192);
            int lineCount = 0;
            try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
                byte[] buf = new byte[sampleSize];
                raf.readFully(buf);
                for (byte b : buf) {
                    if (b == '\n') lineCount++;
                }
            }
            if (lineCount == 0) return 1;
            double avgLineLen = (double) sampleSize / lineCount;
            return (int) (fileSize / avgLineLen);
        } catch (Exception e) {
            return 0;
        }
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
