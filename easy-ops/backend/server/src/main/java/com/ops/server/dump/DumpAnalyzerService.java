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
        if (data.length < 19) return false;
        // HPROF 文件头: "JAVA PROFILE 1.0.2\0" (19 字节)
        // 前 4 字节: "JAVA" (0x4A415641)
        // 接着是 " PROFILE 1.0.2" + null
        return data[0] == 0x4A && data[1] == 0x41 && data[2] == 0x56 && data[3] == 0x41 &&
               data[4] == 0x20 && data[5] == 0x50 && data[6] == 0x52 && data[7] == 0x4F;
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

        log.info("[Dump分析] === 开始解析 HPROF 文件 ===");
        log.info("[Dump分析] 文件大小: {} bytes", data.length);

        if (data.length < 28) {
            throw new IllegalArgumentException("文件太小，不是有效的 HPROF 文件");
        }

        int header = buffer.getInt();
        log.info("[Dump分析] 文件头: 0x{}", String.format("%08X", header));
        if (header != HPROF_HEADER) {
            throw new IllegalArgumentException("无效的 HPROF 文件格式：缺少 JAVA 标识");
        }

        buffer.position(18);
        byte nullByte = buffer.get();
        if (nullByte != 0) {
            throw new IllegalArgumentException("无效的 HPROF 文件格式：字符串未正确终止");
        }

        int idSize = buffer.getInt();
        log.info("[Dump分析] ID 大小: {} 字节", idSize);
        if (idSize != 4 && idSize != 8) {
            throw new IllegalArgumentException("无效的 ID 大小: " + idSize);
        }
        result.setIdSize(idSize);

        long timestamp = buffer.getLong();
        log.info("[Dump分析] 时间戳: {}", timestamp);

        Map<Long, String> strings = new HashMap<>();
        Map<Long, Long> classSerialToNameId = new HashMap<>();
        Map<Long, String> classNames = new HashMap<>();
        Map<String, ClassStats> classStatsMap = new HashMap<>();

        log.info("[Dump分析] === 单遍扫描：收集字符串、类信息、分析堆数据 ===");

        int recordCount = 0, stringCount = 0, classCount = 0, heapSegmentCount = 0;

        try {
            while (buffer.hasRemaining()) {
                int startPos = buffer.position();
                if (startPos >= data.length - 9) break;

                byte tag = buffer.get();
                buffer.getInt();
                int length = buffer.getInt();
                int bodyStart = buffer.position();
                recordCount++;

                if (recordCount <= 5 || recordCount % 50000 == 0) {
                    log.info("[Dump分析] 记录 {}: 位置={}, tag=0x{}, length={}, bodyStart={}",
                            recordCount, startPos, String.format("%02X", tag), length, bodyStart);
                }

                if (length < 0 || bodyStart + length > data.length) {
                    log.warn("[Dump分析] 记录长度异常: tag=0x{}, length={}, bodyStart={}, dataLength={}",
                            String.format("%02X", tag), length, bodyStart, data.length);
                    break;
                }

                // 处理字符串记录
                if (tag == HPROF_STRING) {
                    long strId = readId(buffer, idSize);
                    String str = readNullTerminatedString(buffer, bodyStart + length);
                    strings.put(strId, str);
                    stringCount++;
                }
                // 处理类加载记录
                else if (tag == HPROF_LOAD_CLASS) {
                    buffer.getInt();
                    long classObjId = readId(buffer, idSize);
                    buffer.getInt();
                    long classNameId = readId(buffer, idSize);
                    classSerialToNameId.put(classObjId, classNameId);
                    classCount++;
                }
                // 处理堆转储记录
                else if (tag == HPROF_HEAP_DUMP || tag == HPROF_HEAP_DUMP_SEGMENT) {
                    heapSegmentCount++;
                    log.info("[Dump分析] === 发现堆转储记录 {} ===", heapSegmentCount);
                    log.info("[Dump分析] 堆转储记录位置: startPos={}, length={}", startPos, length);
                    log.info("[Dump分析] 堆转储记录详情: tag=0x{}, bodyStart={}", String.format("%02X", tag), bodyStart);
                    log.info("[Dump分析] 当前 buffer 位置: {}", buffer.position());
                    log.info("[Dump分析] 堆转储段数据范围: {} 到 {}", bodyStart, bodyStart + length);

                    // 打印堆转储段的前 64 字节
                    int previewLength = Math.min(64, length);
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < previewLength; i++) {
                        if (bodyStart + i < data.length) {
                            sb.append(String.format("%02X ", data[bodyStart + i]));
                        }
                    }
                    log.info("[Dump分析] 堆转储段前 {} 字节: {}", previewLength, sb.toString());

                    parseHeapDumpSegment(buffer, bodyStart, length, idSize, classNames, classStatsMap);
                    log.info("[Dump分析] 堆转储段解析完成，当前类数: {}", classStatsMap.size());
                }

                buffer.position(bodyStart + length);
            }
        } catch (Exception e) {
            log.error("[Dump分析] 扫描异常:位置={}, 错误={}", buffer.position(), e.getMessage(), e);
        }

        log.info("[Dump分析] 扫描完成: 记录数={}, 字符串数={}, 类加载数={}, 堆转储段数={}",
                recordCount, stringCount, classCount, heapSegmentCount);

        // 构建类名映射
        for (Map.Entry<Long, Long> entry : classSerialToNameId.entrySet()) {
            String name = strings.get(entry.getValue());
            if (name != null) classNames.put(entry.getKey(), name.replace('/', '.'));
        }
        log.info("[Dump分析] 类名映射完成: {} 个类", classNames.size());

        // 生成最终结果
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

        log.info("[Dump分析] === 最终结果: 类数={}, 总实例数={}, 总大小={} ===",
                classStatsList.size(), totalInstances, formatBytes(totalSize));
        if (!classStatsList.isEmpty()) {
            classStatsList.stream().limit(5).forEach(s ->
                    log.info("[Dump分析] TOP类: {} 实例={} 大小={}", s.getClassName(), s.getInstanceCount(), formatBytes(s.getTotalSize())));
        }
    }

    private void parseHeapDumpSegment(ByteBuffer buffer, int offset, int length, int idSize,
                                       Map<Long, String> classNames, Map<String, ClassStats> classStatsMap) {
        int endPos = offset + length;
        if (endPos > buffer.capacity()) {
            endPos = buffer.capacity();
        }

        log.info("[Dump分析] 解析堆转储段: offset={}, length={}, endPos={}, bufferCapacity={}",
                offset, length, endPos, buffer.capacity());
        log.info("[Dump分析] buffer 当前位置: {}", buffer.position());

        try {
            int subTagCount = 0;
            while (buffer.position() < endPos && buffer.hasRemaining()) {
                int currentPos = buffer.position();
                if (currentPos >= endPos - 1) break;

                byte subTag = buffer.get();
                subTagCount++;

                if (subTagCount <= 10 || subTagCount % 100000 == 0) {
                    log.info("[Dump分析] 子标签 {}: 位置={}, tag=0x{}", subTagCount, currentPos, String.format("%02X", subTag));
                }

                switch (subTag) {
                    case 0x20: // HEAP_DUMP_INFO
                        if (currentPos + 4 + idSize > endPos) {
                            log.warn("[Dump分析] HEAP_DUMP_INFO 数据不足");
                            return;
                        }
                        buffer.getInt();
                        readId(buffer, idSize);
                        break;
                    case 0x21: // ROOT_UNKNOWN
                        if (currentPos + idSize > endPos) return;
                        readId(buffer, idSize);
                        break;
                    case 0x22: // ROOT_JNI_GLOBAL
                        if (currentPos + idSize * 2 > endPos) return;
                        readId(buffer, idSize);
                        readId(buffer, idSize);
                        break;
                    case 0x23: // ROOT_JNI_LOCAL
                    case 0x24: // ROOT_JAVA_FRAME
                        if (currentPos + idSize + 8 > endPos) return;
                        readId(buffer, idSize);
                        buffer.getInt();
                        buffer.getInt();
                        break;
                    case 0x25: // ROOT_NATIVE_STACK
                        if (currentPos + idSize + 4 > endPos) return;
                        readId(buffer, idSize);
                        buffer.getInt();
                        break;
                    case 0x26: // ROOT_STICKY_CLASS
                    case 0x28: // ROOT_MONITOR_USED
                        if (currentPos + idSize > endPos) return;
                        readId(buffer, idSize);
                        break;
                    case 0x27: // ROOT_THREAD_BLOCK
                        if (currentPos + idSize + 4 > endPos) return;
                        readId(buffer, idSize);
                        buffer.getInt();
                        break;
                    case 0x29: // ROOT_THREAD_OBJECT
                        if (currentPos + idSize + 8 > endPos) return;
                        readId(buffer, idSize);
                        buffer.getInt();
                        buffer.getInt();
                        break;
                    case 0x2C: // CLASS_DUMP
                        if (currentPos + idSize * 6 + 20 > endPos) return;
                        parseClassDump(buffer, idSize, endPos, classNames, classStatsMap);
                        break;
                    case 0x2D: // INSTANCE_DUMP
                        if (currentPos + idSize * 2 + 8 > endPos) return;
                        parseInstanceDump(buffer, idSize, endPos, classNames, classStatsMap);
                        break;
                    case 0x2E: // OBJECT_ARRAY_DUMP
                        if (currentPos + idSize * 2 + 4 > endPos) return;
                        parseObjectArrayDump(buffer, idSize, endPos, classNames, classStatsMap);
                        break;
                    case 0x2F: // PRIMITIVE_ARRAY_DUMP
                        if (currentPos + idSize + 5 > endPos) return;
                        parsePrimitiveArrayDump(buffer, idSize, endPos, classNames, classStatsMap);
                        break;
                    default:
                        log.warn("[Dump分析] 未知的堆转储子标签: 0x{}, 位置: {}, endPos={}", String.format("%02X", subTag), currentPos, endPos);
                        log.warn("[Dump分析] buffer位置={}, 剩余字节={}", buffer.position(), endPos - buffer.position());
                        return;
                }
            }
            log.info("[Dump分析] 堆转储段解析完成: 子标签数={}", subTagCount);
        } catch (Exception e) {
            log.error("[Dump分析] 解析堆转储段异常:位置={}, 错误={}", buffer.position(), e.getMessage(), e);
        }
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
