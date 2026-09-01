package com.ops.agent.filemgr;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 压缩下载任务（Agent 侧内存模型）。
 */
public class DownloadTask {

    public static final String PENDING = "PENDING";
    public static final String COMPRESSING = "COMPRESSING";
    public static final String READY = "READY";
    public static final String COMPLETED = "COMPLETED";
    public static final String CANCELLED = "CANCELLED";
    public static final String FAILED = "FAILED";

    private final String id;
    private final String name;
    private final List<String> paths;
    private final long createTime = System.currentTimeMillis();
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    private volatile String status = PENDING;
    private volatile long totalSize;
    private volatile long processedBytes;
    private volatile String message;
    private final List<File> parts = new ArrayList<>();

    public DownloadTask(String id, String name, List<String> paths) {
        this.id = id;
        this.name = name;
        this.paths = paths;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public List<String> getPaths() { return paths; }
    public long getCreateTime() { return createTime; }
    public AtomicBoolean getCancelled() { return cancelled; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public long getTotalSize() { return totalSize; }
    public void setTotalSize(long totalSize) { this.totalSize = totalSize; }
    public long getProcessedBytes() { return processedBytes; }
    public void setProcessedBytes(long processedBytes) { this.processedBytes = processedBytes; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public List<File> getParts() { return parts; }

    /** 估算（供前端展示）是否还有真实文件产物可下载 */
    public boolean hasArtifacts() {
        return READY.equals(status) || COMPLETED.equals(status) || PENDING.equals(status) || COMPRESSING.equals(status);
    }
}
