package com.ops.server.dump;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dump 文件分析服务
 * 支持 HPROF 和 Core 两种格式（简化版）
 */
@Service
public class DumpAnalyzerService {
    private static final Logger log = LoggerFactory.getLogger(DumpAnalyzerService.class);
    private final Map<String, DumpAnalysisResult> resultCache = new ConcurrentHashMap<>();

    /**
     * 分析 dump 文件
     */
    public DumpAnalysisResult analyze(String fileId, InputStream inputStream, String fileName) throws IOException {
        log.info("[Dump分析] 开始分析: fileId={}, fileName={}", fileId, fileName);
        DumpAnalysisResult result = new DumpAnalysisResult();
        result.setFileId(fileId);
        result.setFileName(fileName);
        result.setStartTime(System.currentTimeMillis());

        try {
            byte[] data = readAllBytes(inputStream);
            result.setFileSize(data.length);

            if (fileName != null && fileName.endsWith(".hprof")) {
                parseHprofSimple(data, result);
            } else if (fileName != null && fileName.endsWith(".core")) {
                parseCoreSimple(data, result);
            } else if (isHprofFormat(data)) {
                parseHprofSimple(data, result);
            } else if (isCoreFormat(data)) {
                parseCoreSimple(data, result);
            } else {
                throw new IllegalArgumentException("不支持的文件格式");
            }

            result.setSuccess(true);
            result.setStatus("COMPLETED");
        } catch (Exception e) {
            log.error("[Dump分析] 失败: {}", e.getMessage(), e);
            result.setSuccess(false);
            result.setStatus("FAILED");
            result.setErrorMsg(e.getMessage());
        } finally {
            result.setEndTime(System.currentTimeMillis());
            result.setDurationMs(result.getEndTime() - result.getStartTime());
        }
        resultCache.put(fileId, result);
        return result;
    }

    private boolean isHprofFormat(byte[] data) {
        if (data.length < 19) return false;
        return data[0] == 0x4A && data[1] == 0x41 && data[2] == 0x56 && data[3] == 0x41 &&
               data[4] == 0x20 && data[5] == 0x50 && data[6] == 0x52 && data[7] == 0x4F;
    }

    private boolean isCoreFormat(byte[] data) {
        if (data.length < 16) return false;
        if (data[0] == 0x7F && data[1] == 0x45 && data[2] == 0x4C && data[3] == 0x46) return true;
        return false;
    }

    /**
     * 简化版 HPROF 解析 - 只提取基本信息
     */
    private void parseHprofSimple(byte[] data, DumpAnalysisResult result) {
        log.info("[Dump分析] 解析 HPROF 文件: {} bytes", data.length);
        Map<String, ClassStats> classStatsMap = new HashMap<>();

        // 使用简化的方法：扫描文件查找类信息
        scanHprofForClassInfo(data, classStatsMap);

        // 生成结果
        List<ClassStats> classStatsList = new ArrayList<>(classStatsMap.values());
        classStatsList.sort((a, b) -> Long.compare(b.getTotalSize(), a.getTotalSize()));

        long totalInstances = 0, totalSize = 0;
        for (ClassStats stats : classStatsList) {
            totalInstances += stats.getInstanceCount();
            totalSize += stats.getTotalSize();
        }

        result.setClassStatsList(classStatsList.subList(0, Math.min(50, classStatsList.size())));
        result.setTotalInstances(totalInstances);
        result.setTotalSize(totalSize);
        result.setTotalSizeFormatted(formatBytes(totalSize));
        result.setClassCount(classStatsList.size());

        log.info("[Dump分析] HPROF 解析完成: 类数={}, 总实例={}, 总大小={}", classStatsList.size(), totalInstances, formatBytes(totalSize));
    }

    /**
     * 扫描 HPROF 文件中的类信息
     */
    private void scanHprofForClassInfo(byte[] data, Map<String, ClassStats> classStatsMap) {
        // 常见的 Java 类名模式
        String[] patterns = {
            "java/lang/String", "java/lang/Object", "java/util/HashMap",
            "java/util/ArrayList", "java/util/HashSet", "java/util/LinkedList",
            "java/util/concurrent/ConcurrentHashMap", "java/lang/Thread",
            "java/lang/ClassLoader", "java/lang/reflect/Method",
            "[B", "[C", "[I", "[J", "[Z", "[S", "[F", "[D",
            "[Ljava.lang.Object;", "[Ljava.lang.String;"
        };

        for (String pattern : patterns) {
            byte[] patternBytes = pattern.getBytes();
            int count = countOccurrences(data, patternBytes);
            if (count > 0) {
                String className = pattern.replace('/', '.');
                ClassStats stats = classStatsMap.computeIfAbsent(className, k -> new ClassStats());
                stats.setClassName(className);
                stats.setInstanceCount(count);
                stats.addTotalSize(count * estimateInstanceSize(className));
            }
        }
    }

    private int countOccurrences(byte[] data, byte[] pattern) {
        int count = 0, index = 0;
        while ((index = findBytes(data, pattern, index)) != -1) {
            count++;
            index++;
        }
        return count;
    }

    private int findBytes(byte[] data, byte[] pattern, int startIndex) {
        for (int i = startIndex; i <= data.length - pattern.length; i++) {
            boolean found = true;
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) { found = false; break; }
            }
            if (found) return i;
        }
        return -1;
    }

    private long estimateInstanceSize(String className) {
        switch (className) {
            case "[B": return 1;
            case "[C": case "[I": case "[Z": case "[S": return 2;
            case "[F": return 4;
            case "[J": case "[D": return 8;
            case "java/lang/String": return 40;
            case "java/lang/Object": return 16;
            case "java/util/HashMap": return 120;
            case "java/util/ArrayList": return 48;
            default: return 64;
        }
    }

    /**
     * 简化版 Core 文件解析
     */
    private void parseCoreSimple(byte[] data, DumpAnalysisResult result) {
        log.info("[Dump分析] 解析 Core 文件: {} bytes", data.length);
        List<ClassStats> classStatsList = new ArrayList<>();
        Map<String, ClassStats> classStatsMap = new HashMap<>();

        // 扫描 Java 类信息
        String[] patterns = {
            "java/lang/String", "java/lang/Object", "java/util/HashMap",
            "java/util/ArrayList", "[B", "[C", "[I", "[J"
        };

        for (String pattern : patterns) {
            byte[] patternBytes = pattern.getBytes();
            int count = countOccurrences(data, patternBytes);
            if (count > 0) {
                String className = pattern.replace('/', '.');
                ClassStats stats = classStatsMap.computeIfAbsent(className, k -> new ClassStats());
                stats.setClassName(className);
                stats.setInstanceCount(count);
                stats.addTotalSize(count * estimateInstanceSize(className));
            }
        }

        classStatsList.addAll(classStatsMap.values());
        classStatsList.sort((a, b) -> Long.compare(b.getTotalSize(), a.getTotalSize()));

        long totalInstances = 0, totalSize = 0;
        for (ClassStats stats : classStatsList) {
            totalInstances += stats.getInstanceCount();
            totalSize += stats.getTotalSize();
        }

        result.setClassStatsList(classStatsList.subList(0, Math.min(50, classStatsList.size())));
        result.setTotalInstances(totalInstances);
        result.setTotalSize(totalSize);
        result.setTotalSizeFormatted(formatBytes(totalSize));
        result.setClassCount(classStatsList.size());

        log.info("[Dump分析] Core 解析完成: 类数={}, 总实例={}, 总大小={}", classStatsList.size(), totalInstances, formatBytes(totalSize));
    }

    private byte[] readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] temp = new byte[8192];
        int bytesRead;
        while ((bytesRead = inputStream.read(temp, 0, temp.length)) != -1) {
            buffer.write(temp, 0, bytesRead);
        }
        return buffer.toByteArray();
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    public DumpAnalysisResult getResult(String fileId) { return resultCache.get(fileId); }
    public void deleteResult(String fileId) { resultCache.remove(fileId); }

    public static class DumpAnalysisResult {
        private String fileId, fileName, status, errorMsg, totalSizeFormatted;
        private boolean success;
        private long fileSize, totalInstances, totalSize, startTime, endTime, durationMs;
        private int idSize, classCount;
        private List<ClassStats> classStatsList;
        // Getters and Setters
        public String getFileId() { return fileId; }
        public void setFileId(String fileId) { this.fileId = fileId; }
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getErrorMsg() { return errorMsg; }
        public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }
        public long getFileSize() { return fileSize; }
        public void setFileSize(long fileSize) { this.fileSize = fileSize; }
        public int getIdSize() { return idSize; }
        public void setIdSize(int idSize) { this.idSize = idSize; }
        public long getTotalInstances() { return totalInstances; }
        public void setTotalInstances(long totalInstances) { this.totalInstances = totalInstances; }
        public long getTotalSize() { return totalSize; }
        public void setTotalSize(long totalSize) { this.totalSize = totalSize; }
        public String getTotalSizeFormatted() { return totalSizeFormatted; }
        public void setTotalSizeFormatted(String totalSizeFormatted) { this.totalSizeFormatted = totalSizeFormatted; }
        public int getClassCount() { return classCount; }
        public void setClassCount(int classCount) { this.classCount = classCount; }
        public List<ClassStats> getClassStatsList() { return classStatsList; }
        public void setClassStatsList(List<ClassStats> classStatsList) { this.classStatsList = classStatsList; }
        public long getStartTime() { return startTime; }
        public void setStartTime(long startTime) { this.startTime = startTime; }
        public long getEndTime() { return endTime; }
        public void setEndTime(long endTime) { this.endTime = endTime; }
        public long getDurationMs() { return durationMs; }
        public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    }

    public static class ClassStats {
        private String className;
        private long instanceCount, totalSize;
        private int instanceIdSize;
        public String getClassName() { return className; }
        public void setClassName(String className) { this.className = className; }
        public long getInstanceCount() { return instanceCount; }
        public void setInstanceCount(long instanceCount) { this.instanceCount = instanceCount; }
        public void incrementInstanceCount() { this.instanceCount++; }
        public long getTotalSize() { return totalSize; }
        public void setTotalSize(long totalSize) { this.totalSize = totalSize; }
        public void addTotalSize(long size) { this.totalSize += size; }
        public int getInstanceIdSize() { return instanceIdSize; }
        public void setInstanceIdSize(int instanceIdSize) { this.instanceIdSize = instanceIdSize; }
    }
}
