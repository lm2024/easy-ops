package com.ops.server.dump;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dump 文件分析服务
 * 支持 HPROF 和 Core 两种格式
 */
@Service
public class DumpAnalyzerService {
    private static final Logger log = LoggerFactory.getLogger(DumpAnalyzerService.class);

    // HPROF 标识符
    private static final int HPROF_HEADER = 0x4A415641; // "JAVA"
    private static final byte HPROF_STRING = 0x01;
    private static final byte HPROF_LOAD_CLASS = 0x02;
    private static final byte HPROF_HEAP_DUMP = 0x0C;
    private static final byte HPROF_HEAP_DUMP_SEGMENT = 0x1C;

    // 解析结果缓存
    private final Map<String, DumpAnalysisResult> resultCache = new ConcurrentHashMap<>();

    /**
     * 分析 dump 文件（支持 HPROF 和 Core 两种格式）
     */
    public DumpAnalysisResult analyze(String fileId, InputStream inputStream, String fileName) throws IOException {
        log.info("[Dump分析] 开始分析文件: fileId={}, fileName={}", fileId, fileName);

        DumpAnalysisResult result = new DumpAnalysisResult();
        result.setFileId(fileId);
        result.setFileName(fileName);
        result.setStartTime(System.currentTimeMillis());

        try {
            byte[] data = readAllBytes(inputStream);
            result.setFileSize(data.length);

            // 根据文件类型或格式检测选择解析方式
            if (fileName != null && fileName.endsWith(".hprof")) {
                parseHprofFile(data, result);
            } else if (fileName != null && fileName.endsWith(".core")) {
                parseCoreFile(data, result);
            } else if (isHprofFormat(data)) {
                parseHprofFile(data, result);
            } else if (isCoreFormat(data)) {
                parseCoreFile(data, result);
            } else {
                throw new IllegalArgumentException("不支持的文件格式，请上传 .hprof 或 .core 文件");
            }

            result.setSuccess(true);
            result.setStatus("COMPLETED");
        } catch (Exception e) {
            log.error("[Dump分析] 解析失败: {}", e.getMessage(), e);
            result.setSuccess(false);
            result.setStatus("FAILED");
            result.setErrorMsg(e.getMessage());
        } finally {
            result.setEndTime(System.currentTimeMillis());
            result.setDurationMs(result.getEndTime() - result.getStartTime());
        }

        resultCache.put(fileId, result);
        log.info("[Dump分析] 分析完成: fileId={}, 耗时={}ms", fileId, result.getDurationMs());
        return result;
    }

    private boolean isHprofFormat(byte[] data) {
        if (data.length < 4) return false;
        return data[0] == 0x4A && data[1] == 0x41 && data[2] == 0x56 && data[3] == 0x41;
    }

    private boolean isCoreFormat(byte[] data) {
        if (data.length < 16) return false;
        if (data[0] == 0x7F && data[1] == 0x45 && data[2] == 0x4C && data[3] == 0x46) return true;
        if ((data[0] == (byte)0xFE && data[1] == (byte)0xED && data[2] == (byte)0xFA && data[3] == (byte)0xCF) ||
            (data[0] == (byte)0xFE && data[1] == (byte)0xED && data[2] == (byte)0xFA && data[3] == (byte)0xCD)) return true;
        return false;
    }

    // ==================== Core 文件解析 ====================

    private void parseCoreFile(byte[] data, DumpAnalysisResult result) {
        log.info("[Dump分析] 解析 Core 文件，大小: {} bytes", data.length);
        List<ClassStats> classStatsList = new ArrayList<>();
        Map<String, ClassStats> classStatsMap = new HashMap<>();

        // 从 core 文件中提取 Java 类信息
        scanCoreFileForJavaInfo(data, classStatsMap);

        if (classStatsMap.isEmpty()) {
            provideBasicCoreAnalysis(data, classStatsList, result);
        } else {
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
        }
    }

    private void scanCoreFileForJavaInfo(byte[] data, Map<String, ClassStats> classStatsMap) {
        String[] javaClassPatterns = {
            "java/lang/String", "java/lang/Object", "java/util/HashMap",
            "java/util/ArrayList", "java/util/HashSet", "java/util/LinkedList",
            "java/util/concurrent/ConcurrentHashMap", "java/lang/Thread",
            "java/lang/ClassLoader", "java/lang/reflect/Method",
            "[B", "[C", "[I", "[J", "[Z", "[S", "[F", "[D",
            "[Ljava.lang.Object;", "[Ljava.lang.String;"
        };

        for (String pattern : javaClassPatterns) {
            byte[] patternBytes = pattern.getBytes(StandardCharsets.UTF_8);
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
            case "java/util/HashSet": return 80;
            case "java/util/LinkedList": return 48;
            case "java/util/concurrent/ConcurrentHashMap": return 200;
            case "java/lang/Thread": return 512;
            case "java/lang/ClassLoader": return 256;
            case "java/lang/reflect/Method": return 96;
            default: return 64;
        }
    }

    private void provideBasicCoreAnalysis(byte[] data, List<ClassStats> classStatsList, DumpAnalysisResult result) {
        long totalSize = data.length;
        ClassStats basicStats = new ClassStats();
        basicStats.setClassName("Core Dump (完整进程内存)");
        basicStats.setInstanceCount(1);
        basicStats.setTotalSize(totalSize);
        classStatsList.add(basicStats);

        Map<String, Long> memorySegments = identifyMemorySegments(data);
        for (Map.Entry<String, Long> entry : memorySegments.entrySet()) {
            ClassStats segmentStats = new ClassStats();
            segmentStats.setClassName(entry.getKey());
            segmentStats.setInstanceCount(1);
            segmentStats.setTotalSize(entry.getValue());
            classStatsList.add(segmentStats);
        }

        result.setClassStatsList(classStatsList);
        result.setTotalInstances(1);
        result.setTotalSize(totalSize);
        result.setTotalSizeFormatted(formatBytes(totalSize));
        result.setClassCount(classStatsList.size());
    }

    private Map<String, Long> identifyMemorySegments(byte[] data) {
        Map<String, Long> segments = new HashMap<>();
        if (data.length > 1024 * 1024) {
            segments.put("Java Heap (估算)", data.length / 2L);
            segments.put("Native Memory (估算)", data.length / 4L);
            segments.put("Other (估算)", data.length / 4L);
        }
        return segments;
    }

    // ==================== HPROF 文件解析 ====================

    private byte[] readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] temp = new byte[8192];
        int bytesRead;
        while ((bytesRead = inputStream.read(temp, 0, temp.length)) != -1) {
            buffer.write(temp, 0, bytesRead);
        }
        return buffer.toByteArray();
    }

    private void parseHprofFile(byte[] data, DumpAnalysisResult result) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.BIG_ENDIAN);

        int header = buffer.getInt();
        if (header != HPROF_HEADER) {
            throw new IllegalArgumentException("无效的 HPROF 文件格式");
        }

        int idSize = buffer.getInt();
        result.setIdSize(idSize);
        buffer.getLong(); // 时间戳

        Map<Long, String> strings = new HashMap<>();
        Map<Long, Long> classSerialToNameId = new HashMap<>();
        Map<Long, String> classNames = new HashMap<>();
        Map<String, ClassStats> classStatsMap = new HashMap<>();

        // 第一遍：收集字符串和类信息
        while (buffer.hasRemaining()) {
            int startPos = buffer.position();
            if (startPos >= data.length - 9) break;
            byte tag = buffer.get();
            buffer.getInt();
            int length = buffer.getInt();
            int bodyStart = buffer.position();

            if (tag == HPROF_STRING) {
                long strId = readId(buffer, idSize);
                String str = readNullTerminatedString(buffer, bodyStart + length);
                strings.put(strId, str);
            } else if (tag == HPROF_LOAD_CLASS) {
                buffer.getInt();
                long classObjId = readId(buffer, idSize);
                buffer.getInt();
                long classNameId = readId(buffer, idSize);
                classSerialToNameId.put(classObjId, classNameId);
            }
            buffer.position(bodyStart + length);
        }

        for (Map.Entry<Long, Long> entry : classSerialToNameId.entrySet()) {
            String name = strings.get(entry.getValue());
            if (name != null) classNames.put(entry.getKey(), name.replace('/', '.'));
        }

        // 第二遍：分析堆数据
        buffer.position(0);
        buffer.getInt(); buffer.getInt(); buffer.getLong();

        while (buffer.hasRemaining()) {
            int startPos = buffer.position();
            if (startPos >= data.length - 9) break;
            byte tag = buffer.get();
            buffer.getInt();
            int length = buffer.getInt();
            int bodyStart = buffer.position();
            if (tag == HPROF_HEAP_DUMP || tag == HPROF_HEAP_DUMP_SEGMENT) {
                parseHeapDumpSegment(buffer, bodyStart, length, idSize, classNames, classStatsMap);
            }
            buffer.position(bodyStart + length);
        }

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
    }

    private void parseHeapDumpSegment(ByteBuffer buffer, int offset, int length, int idSize,
                                       Map<Long, String> classNames, Map<String, ClassStats> classStatsMap) {
        int endPos = offset + length;
        while (buffer.position() < endPos && buffer.hasRemaining()) {
            byte subTag = buffer.get();
            switch (subTag) {
                case 0x20: buffer.getInt(); readId(buffer, idSize); break;
                case 0x21: case 0x26: case 0x27: case 0x28: readId(buffer, idSize); break;
                case 0x22: readId(buffer, idSize); readId(buffer, idSize); break;
                case 0x23: case 0x24: readId(buffer, idSize); buffer.getInt(); buffer.getInt(); break;
                case 0x25: case 0x29: readId(buffer, idSize); buffer.getInt(); buffer.getInt(); break;
                case 0x2C: parseClassDump(buffer, idSize, classNames, classStatsMap); break;
                case 0x2D: parseInstanceDump(buffer, idSize, classNames, classStatsMap); break;
                case 0x2E: parseObjectArrayDump(buffer, idSize, classNames, classStatsMap); break;
                case 0x2F: parsePrimitiveArrayDump(buffer, idSize, classNames, classStatsMap); break;
                default: return;
            }
        }
    }

    private void parseClassDump(ByteBuffer buffer, int idSize, Map<Long, String> classNames, Map<String, ClassStats> classStatsMap) {
        long classObjId = readId(buffer, idSize);
        buffer.getInt();
        for (int i = 0; i < 5; i++) readId(buffer, idSize);
        readId(buffer, idSize);
        int instanceSize = buffer.getInt();
        int constPoolCount = buffer.getShort() & 0xFFFF;
        for (int i = 0; i < constPoolCount; i++) { buffer.getShort(); byte type = buffer.get(); skipValue(buffer, type, idSize); }
        int staticFieldCount = buffer.getShort() & 0xFFFF;
        for (int i = 0; i < staticFieldCount; i++) { readId(buffer, idSize); byte type = buffer.get(); skipValue(buffer, type, idSize); }
        int instanceFieldCount = buffer.getShort() & 0xFFFF;
        for (int i = 0; i < instanceFieldCount; i++) { readId(buffer, idSize); buffer.get(); }
        String className = classNames.get(classObjId);
        if (className != null) {
            ClassStats stats = classStatsMap.computeIfAbsent(className, k -> new ClassStats());
            stats.setClassName(className);
            stats.setInstanceIdSize(instanceSize);
        }
    }

    private void parseInstanceDump(ByteBuffer buffer, int idSize, Map<Long, String> classNames, Map<String, ClassStats> classStatsMap) {
        readId(buffer, idSize);
        buffer.getInt();
        long classObjId = readId(buffer, idSize);
        int numBytes = buffer.getInt();
        buffer.position(buffer.position() + numBytes);
        String className = classNames.get(classObjId);
        if (className != null) {
            ClassStats stats = classStatsMap.computeIfAbsent(className, k -> new ClassStats());
            stats.setClassName(className);
            stats.incrementInstanceCount();
            stats.addTotalSize(numBytes + 16);
        }
    }

    private void parseObjectArrayDump(ByteBuffer buffer, int idSize, Map<Long, String> classNames, Map<String, ClassStats> classStatsMap) {
        readId(buffer, idSize);
        buffer.getInt();
        int numElements = buffer.getInt();
        long arrayClassId = readId(buffer, idSize);
        buffer.position(buffer.position() + numElements * idSize);
        String className = classNames.get(arrayClassId);
        if (className == null) className = "Object[]";
        ClassStats stats = classStatsMap.computeIfAbsent(className, k -> new ClassStats());
        stats.setClassName(className);
        stats.incrementInstanceCount();
        stats.addTotalSize(numElements * idSize + 16);
    }

    private void parsePrimitiveArrayDump(ByteBuffer buffer, int idSize, Map<Long, String> classNames, Map<String, ClassStats> classStatsMap) {
        readId(buffer, idSize);
        buffer.getInt();
        int numElements = buffer.getInt();
        byte elementType = buffer.get();
        int elementSize = getElementSize(elementType);
        int dataSize = numElements * elementSize;
        buffer.position(buffer.position() + dataSize);
        String typeName;
        switch (elementType) {
            case 4: typeName = "boolean[]"; break;
            case 5: typeName = "char[]"; break;
            case 6: typeName = "float[]"; break;
            case 7: typeName = "double[]"; break;
            case 8: typeName = "byte[]"; break;
            case 9: typeName = "short[]"; break;
            case 10: typeName = "int[]"; break;
            case 11: typeName = "long[]"; break;
            default: typeName = "unknown[]";
        }
        ClassStats stats = classStatsMap.computeIfAbsent(typeName, k -> new ClassStats());
        stats.setClassName(typeName);
        stats.incrementInstanceCount();
        stats.addTotalSize(dataSize + 16);
    }

    private int getElementSize(byte type) {
        switch (type) {
            case 4: case 8: return 1;
            case 5: case 9: return 2;
            case 6: case 10: return 4;
            case 7: case 11: return 8;
            default: return 1;
        }
    }

    private void skipValue(ByteBuffer buffer, byte type, int idSize) {
        switch (type) {
            case 2: readId(buffer, idSize); break;
            case 4: case 8: buffer.get(); break;
            case 5: case 9: buffer.getShort(); break;
            case 6: case 10: buffer.getInt(); break;
            case 7: case 11: buffer.getLong(); break;
        }
    }

    private long readId(ByteBuffer buffer, int idSize) {
        return idSize == 4 ? (buffer.getInt() & 0xFFFFFFFFL) : buffer.getLong();
    }

    private String readNullTerminatedString(ByteBuffer buffer, int maxLength) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        while (buffer.hasRemaining() && count < maxLength) {
            byte b = buffer.get();
            if (b == 0) break;
            sb.append((char) b);
            count++;
        }
        return sb.toString();
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    public DumpAnalysisResult getResult(String fileId) { return resultCache.get(fileId); }
    public void deleteResult(String fileId) { resultCache.remove(fileId); }

    // ==================== 数据类 ====================

    public static class DumpAnalysisResult {
        private String fileId, fileName, status, errorMsg, totalSizeFormatted;
        private boolean success;
        private long fileSize, totalInstances, totalSize, startTime, endTime, durationMs;
        private int idSize, classCount;
        private List<ClassStats> classStatsList;

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
