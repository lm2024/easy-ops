package com.ops.agent.traffic;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * 持久化 Nginx 日志读取偏移，支持日志轮转检测。
 */
public class OffsetTracker {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final File storeFile;
    private final Map<String, OffsetState> states = new HashMap<String, OffsetState>();

    public OffsetTracker(String dataPath) {
        File dir = new File(dataPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        this.storeFile = new File(dir, "nginx-offset.json");
        load();
    }

    public synchronized OffsetState get(String logPath) {
        OffsetState state = states.get(logPath);
        if (state == null) {
            state = new OffsetState();
            state.logPath = logPath;
            states.put(logPath, state);
        }
        return state;
    }

    public synchronized void update(String logPath, long offset, long inode) {
        OffsetState state = get(logPath);
        state.offset = offset;
        state.inode = inode;
        state.updatedAt = System.currentTimeMillis();
        save();
    }

    public synchronized void markError(String logPath, String error) {
        OffsetState state = get(logPath);
        state.lastError = error;
        state.updatedAt = System.currentTimeMillis();
        save();
    }

    private void load() {
        if (!storeFile.exists()) {
            return;
        }
        try {
            String json = new String(Files.readAllBytes(storeFile.toPath()), StandardCharsets.UTF_8);
            Map<String, OffsetState> loaded = MAPPER.readValue(json, new TypeReference<Map<String, OffsetState>>() {});
            if (loaded != null) {
                states.putAll(loaded);
            }
        } catch (Exception ignored) {
        }
    }

    private void save() {
        try {
            Files.write(storeFile.toPath(), MAPPER.writeValueAsBytes(states));
        } catch (Exception ignored) {
        }
    }

    public static class OffsetState {
        public String logPath;
        public long offset;
        public long inode;
        public long updatedAt;
        public String lastError;
    }
}
