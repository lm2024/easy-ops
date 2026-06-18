package com.ops.agent.handler;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 日志命令处理器
 * 读取和上报项目日志
 */
public class LogCommander {

    private final String logPath;

    public LogCommander(String logPath) {
        this.logPath = logPath;
    }

    /**
     * 获取日志内容
     *
     * @param fileName 日志文件名
     * @param offset   偏移量
     * @param lines    读取行数
     * @return 日志内容
     */
    public Map<String, Object> getLog(String fileName, int offset, int lines) {
        try {
            String filePath = logPath + "/" + fileName;
            List<String> logLines = readLines(filePath, offset, lines);

            return Map.of(
                    "status", "SUCCESS",
                    "lines", logLines,
                    "totalLines", countLines(filePath),
                    "fileName", fileName
            );
        } catch (Exception e) {
            return Map.of(
                    "status", "FAILED",
                    "message", "读取日志失败: " + e.getMessage()
            );
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
