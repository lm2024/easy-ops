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
 * HPROF Dump 文件分析服务
 * 解析 Java heap dump 文件，提供内存分析能力
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
     * 分析 dump 文件
     */
    public DumpAnalysisResult analyze(String fileId, InputStream inputStream) throws IOException {
        log.info("[Dump分析] 开始分析文件: {}", fileId);

        DumpAnalysisResult result = new DumpAnalysisResult();
        result.setFileId(fileId);
        result.setStartTime(System.currentTimeMillis());

        try {
            // 读取整个文件到内存（对于大文件需要分块处理，这里简化处理）
            byte[] data = readAllBytes(inputStream);
            result.setFileSize(data.length);

            // 解析 HPROF 文件
            parseHprofFile(data, result);

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

        // 缓存结果
        resultCache.put(fileId, result);

        log.info("[Dump分析] 分析完成: fileId={}, 耗时={}ms", fileId, result.getDurationMs());
        return result;
    }

    /**
     * 读取所有字节
     */
    private byte[] readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] temp = new byte[8192];
        int bytesRead;
        while ((bytesRead = inputStream.read(temp, 0, temp.length)) != -1) {
            buffer.write(temp, 0, bytesRead);
        }
        return buffer.toByteArray();
    }

    /**
     * 解析 HPROF 文件
     */
    private void parseHprofFile(byte[] data, DumpAnalysisResult result) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.BIG_ENDIAN);

        // 读取文件头
        int header = buffer.getInt();
        if (header != HPROF_HEADER) {
            throw new IllegalArgumentException("无效的 HPROF 文件格式");
        }

        // 读取标识符大小
        int idSize = buffer.getInt();
        result.setIdSize(idSize);

        // 读取时间戳（忽略）
        buffer.getLong();

        // 存储字符串和类信息
        Map<Long, String> strings = new HashMap<>();
        Map<Long, Long> classSerialToNameId = new HashMap<>();
        Map<Long, String> classNames = new HashMap<>();
        Map<String, ClassInfo> classInfoMap = new HashMap<>();

        // 第一遍扫描：收集字符串和类信息
        while (buffer.hasRemaining()) {
            int startPos = buffer.position();
            if (startPos >= data.length - 9) break;

            byte tag = buffer.get();
            // 跳过时间戳
            buffer.getInt();
            int length = buffer.getInt();

            int bodyStart = buffer.position();

            switch (tag) {
                case HPROF_STRING:
                    long strId = readId(buffer, idSize);
                    String str = readNullTerminatedString(buffer, bodyStart + length);
                    strings.put(strId, str);
                    break;

                case HPROF_LOAD_CLASS:
                    int classSerial = buffer.getInt();
                    long classObjId = readId(buffer, idSize);
                    buffer.getInt(); // 跳过 stack trace serial
                    long classNameId = readId(buffer, idSize);
                    classSerialToNameId.put(classObjId, classNameId);
                    break;

                case HPROF_HEAP_DUMP:
                case HPROF_HEAP_DUMP_SEGMENT:
                    // 在第二遍处理
                    break;
            }

            // 移动到下一个记录
            buffer.position(bodyStart + length);
        }

        // 构建类名映射
        for (Map.Entry<Long, Long> entry : classSerialToNameId.entrySet()) {
            Long classObjId = entry.getKey();
            Long nameId = entry.getValue();
            String name = strings.get(nameId);
            if (name != null) {
                classNames.put(classObjId, name.replace('/', '.'));
            }
        }

        // 第二遍扫描：分析堆数据
        buffer.position(0);
        buffer.getInt(); // 跳过头
        buffer.getInt(); // 跳过 idSize
        buffer.getLong(); // 跳过时间戳

        Map<String, ClassStats> classStatsMap = new HashMap<>();
        long totalInstances = 0;
        long totalSize = 0;

        while (buffer.hasRemaining()) {
            int startPos = buffer.position();
            if (startPos >= data.length - 9) break;

            byte tag = buffer.get();
            buffer.getInt(); // 跳过时间戳
            int length = buffer.getInt();
            int bodyStart = buffer.position();

            if (tag == HPROF_HEAP_DUMP || tag == HPROF_HEAP_DUMP_SEGMENT) {
                // 分析堆转储段
                parseHeapDumpSegment(buffer, bodyStart, length, idSize, classNames, classStatsMap);
            }

            // 移动到下一个记录
            buffer.position(bodyStart + length);
        }

        // 计算统计信息
        List<ClassStats> classStatsList = new ArrayList<>(classStatsMap.values());
        classStatsList.sort((a, b) -> Long.compare(b.getTotalSize(), a.getTotalSize()));

        for (ClassStats stats : classStatsList) {
            totalInstances += stats.getInstanceCount();
            totalSize += stats.getTotalSize();
        }

        // 设置结果
        result.setClassStatsList(classStatsList.subList(0, Math.min(100, classStatsList.size())));
        result.setTotalInstances(totalInstances);
        result.setTotalSize(totalSize);
        result.setTotalSizeFormatted(formatBytes(totalSize));
        result.setClassCount(classStatsList.size());
    }

    /**
     * 解析堆转储段
     */
    private void parseHeapDumpSegment(ByteBuffer buffer, int offset, int length, int idSize,
                                       Map<Long, String> classNames, Map<String, ClassStats> classStatsMap) {
        int endPos = offset + length;

        while (buffer.position() < endPos && buffer.hasRemaining()) {
            byte subTag = buffer.get();

            switch (subTag) {
                case 0x20: // HEAP_DUMP_INFO
                    buffer.getInt(); // heap type
                    readId(buffer, idSize); // heap name
                    break;

                case 0x21: // ROOT_UNKNOWN
                    readId(buffer, idSize);
                    break;

                case 0x22: // ROOT_JNI_GLOBAL
                    readId(buffer, idSize);
                    readId(buffer, idSize);
                    break;

                case 0x23: // ROOT_JNI_LOCAL
                    readId(buffer, idSize);
                    buffer.getInt();
                    buffer.getInt();
                    break;

                case 0x24: // ROOT_JAVA_FRAME
                    readId(buffer, idSize);
                    buffer.getInt();
                    buffer.getInt();
                    break;

                case 0x25: // ROOT_NATIVE_STACK
                    readId(buffer, idSize);
                    buffer.getInt();
                    break;

                case 0x26: // ROOT_STICKY_CLASS
                    readId(buffer, idSize);
                    break;

                case 0x27: // ROOT_THREAD_BLOCK
                    readId(buffer, idSize);
                    buffer.getInt();
                    break;

                case 0x28: // ROOT_MONITOR_USED
                    readId(buffer, idSize);
                    break;

                case 0x29: // ROOT_THREAD_OBJECT
                    readId(buffer, idSize);
                    buffer.getInt();
                    buffer.getInt();
                    break;

                case 0x2C: // CLASS_DUMP
                    parseClassDump(buffer, idSize, classNames, classStatsMap);
                    break;

                case 0x2D: // INSTANCE_DUMP
                    parseInstanceDump(buffer, idSize, classNames, classStatsMap);
                    break;

                case 0x2E: // OBJECT_ARRAY_DUMP
                    parseObjectArrayDump(buffer, idSize, classNames, classStatsMap);
                    break;

                case 0x2F: // PRIMITIVE_ARRAY_DUMP
                    parsePrimitiveArrayDump(buffer, idSize, classNames, classStatsMap);
                    break;

                default:
                    // 未知子标签，跳过剩余部分
                    log.warn("[Dump分析] 未知的堆转储子标签: 0x{}", String.format("%02X", subTag));
                    return;
            }
        }
    }

    /**
     * 解析 CLASS_DUMP
     */
    private void parseClassDump(ByteBuffer buffer, int idSize, Map<Long, String> classNames,
                                 Map<String, ClassStats> classStatsMap) {
        long classObjId = readId(buffer, idSize);
        buffer.getInt(); // stack trace serial
        readId(buffer, idSize); // super class obj id
        readId(buffer, idSize); // class loader obj id
        readId(buffer, idSize); // signers obj id
        readId(buffer, idSize); // protection domain obj id
        readId(buffer, idSize); // reserved1
        readId(buffer, idSize); // reserved2
        int instanceSize = buffer.getInt();

        // 常量池
        int constPoolCount = buffer.getShort() & 0xFFFF;
        for (int i = 0; i < constPoolCount; i++) {
            buffer.getShort(); // index
            byte type = buffer.get();
            skipValue(buffer, type, idSize);
        }

        // 静态字段
        int staticFieldCount = buffer.getShort() & 0xFFFF;
        for (int i = 0; i < staticFieldCount; i++) {
            readId(buffer, idSize); // name
            byte type = buffer.get();
            skipValue(buffer, type, idSize);
        }

        // 实例字段
        int instanceFieldCount = buffer.getShort() & 0xFFFF;
        for (int i = 0; i < instanceFieldCount; i++) {
            readId(buffer, idSize); // name
            buffer.get(); // type
        }

        // 存储类信息
        String className = classNames.get(classObjId);
        if (className != null) {
            ClassStats stats = classStatsMap.computeIfAbsent(className, k -> new ClassStats());
            stats.setClassName(className);
            stats.setInstanceIdSize(instanceSize);
        }
    }

    /**
     * 解析 INSTANCE_DUMP
     */
    private void parseInstanceDump(ByteBuffer buffer, int idSize, Map<Long, String> classNames,
                                    Map<String, ClassStats> classStatsMap) {
        readId(buffer, idSize); // object id
        buffer.getInt(); // stack trace serial
        long classObjId = readId(buffer, idSize);
        int numBytes = buffer.getInt();

        // 跳过实例数据
        byte[] instanceData = new byte[numBytes];
        buffer.get(instanceData);

        // 统计类信息
        String className = classNames.get(classObjId);
        if (className != null) {
            ClassStats stats = classStatsMap.computeIfAbsent(className, k -> new ClassStats());
            stats.setClassName(className);
            stats.incrementInstanceCount();
            stats.addTotalSize(numBytes + idSize + 4 + idSize + 4); // 对象头 + 实例数据
        }
    }

    /**
     * 解析 OBJECT_ARRAY_DUMP
     */
    private void parseObjectArrayDump(ByteBuffer buffer, int idSize, Map<Long, String> classNames,
                                       Map<String, ClassStats> classStatsMap) {
        readId(buffer, idSize); // array object id
        buffer.getInt(); // stack trace serial
        int numElements = buffer.getInt();
        long arrayClassId = readId(buffer, idSize);

        // 跳过元素数据
        byte[] elementsData = new byte[numElements * idSize];
        buffer.get(elementsData);

        // 统计类信息
        String className = classNames.get(arrayClassId);
        if (className == null) className = "Object[]";
        ClassStats stats = classStatsMap.computeIfAbsent(className, k -> new ClassStats());
        stats.setClassName(className);
        stats.incrementInstanceCount();
        stats.addTotalSize(numElements * idSize + 16); // 对象头 + 元素数据
    }

    /**
     * 解析 PRIMITIVE_ARRAY_DUMP
     */
    private void parsePrimitiveArrayDump(ByteBuffer buffer, int idSize, Map<Long, String> classNames,
                                          Map<String, ClassStats> classStatsMap) {
        readId(buffer, idSize); // array object id
        buffer.getInt(); // stack trace serial
        int numElements = buffer.getInt();
        byte elementType = buffer.get();

        int elementSize = getElementSize(elementType);
        int dataSize = numElements * elementSize;

        // 跳过数组数据
        byte[] arrayData = new byte[dataSize];
        buffer.get(arrayData);

        // 确定数组类型名称
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
        stats.addTotalSize(dataSize + 16); // 对象头 + 数组数据
    }

    /**
     * 获取元素大小
     */
    private int getElementSize(byte type) {
        switch (type) {
            case 4: return 1;  // boolean
            case 5: return 2;  // char
            case 6: return 4;  // float
            case 7: return 8;  // double
            case 8: return 1;  // byte
            case 9: return 2;  // short
            case 10: return 4; // int
            case 11: return 8; // long
            default: return 1;
        }
    }

    /**
     * 跳过值
     */
    private void skipValue(ByteBuffer buffer, byte type, int idSize) {
        switch (type) {
            case 2: // object
                readId(buffer, idSize);
                break;
            case 4: // boolean
            case 8: // byte
                buffer.get();
                break;
            case 5: // char
            case 9: // short
                buffer.getShort();
                break;
            case 6: // float
            case 10: // int
                buffer.getInt();
                break;
            case 7: // double
            case 11: // long
                buffer.getLong();
                break;
        }
    }

    /**
     * 读取标识符
     */
    private long readId(ByteBuffer buffer, int idSize) {
        if (idSize == 4) {
            return buffer.getInt() & 0xFFFFFFFFL;
        } else {
            return buffer.getLong();
        }
    }

    /**
     * 读取以 null 结尾的字符串
     */
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

    /**
     * 格式化字节数
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    /**
     * 获取分析结果
     */
    public DumpAnalysisResult getResult(String fileId) {
        return resultCache.get(fileId);
    }

    /**
     * 删除分析结果
     */
    public void deleteResult(String fileId) {
        resultCache.remove(fileId);
    }

    /**
     * 分析结果类
     */
    public static class DumpAnalysisResult {
        private String fileId;
        private boolean success;
        private String status;
        private String errorMsg;
        private long fileSize;
        private int idSize;
        private long totalInstances;
        private long totalSize;
        private String totalSizeFormatted;
        private int classCount;
        private List<ClassStats> classStatsList;
        private long startTime;
        private long endTime;
        private long durationMs;

        // Getters and Setters
        public String getFileId() { return fileId; }
        public void setFileId(String fileId) { this.fileId = fileId; }
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

    /**
     * 类统计信息
     */
    public static class ClassStats {
        private String className;
        private long instanceCount;
        private long totalSize;
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
