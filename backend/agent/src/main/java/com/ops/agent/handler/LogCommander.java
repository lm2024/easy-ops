package com.ops.agent.handler;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 日志命令处理器
 */
public class LogCommander {

    private final String logPath;

    public LogCommander(String logPath) {
        this.logPath = logPath;
    }

    public Map<String, Object> getLog(String fileName, int offset, int lines) {
        try {
            String filePath = logPath + "/" + fileName;
            List<String> logLines = readLines(filePath, offset, lines);

            Map<String, Object> result = new HashMap<>();
            result.put("status", "SUCCESS");
            result.put("lines", logLines);
            result.put("totalLines", countLines(filePath));
            result.put("fileName", fileName);
            return result;
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("status", "FAILED");
            result.put("message", "读取日志失败: " + e.getMessage());
            return result;
        }
    }

    private List<String> readLines(String filePath, int offset, int lines) throws IOException {
        List<String> result = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            int current = 0;
            while ((line = reader.readLine()) != null) {
                if (current >= offset && result.size() < lines) {
                    result.add(line);
                }
                current++;
            }
        }
        return result;
    }

    private int countLines(String filePath) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            int count = 0;
            while (reader.readLine() != null) {
                count++;
            }
            return count;
        } catch (IOException e) {
            return 0;
        }
    }
}
