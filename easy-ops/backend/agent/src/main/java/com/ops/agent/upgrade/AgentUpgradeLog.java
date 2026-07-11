package com.ops.agent.upgrade;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Agent 升级/重启专用日志（与 agent.log 分离，便于快速排障）。
 * 路径：{data-path}/logs/upgrade-restart.log
 */
public final class AgentUpgradeLog {

    public static final String LOG_FILE_NAME = "upgrade-restart.log";

    private AgentUpgradeLog() {
    }

    public static File resolveLogFile(String dataPath) {
        File logDir = new File(dataPath, "logs");
        if (!logDir.exists()) {
            logDir.mkdirs();
        }
        return new File(logDir, LOG_FILE_NAME);
    }

    /**
     * 追加一行升级重启日志。
     */
    public static synchronized void append(String dataPath, String level, String message) {
        File logFile = resolveLogFile(dataPath);
        String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String line = "[" + ts + "] [" + level + "] " + message;
        System.out.println("[AgentUpgrade] " + line);
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(logFile, true), StandardCharsets.UTF_8))) {
            writer.println(line);
        } catch (Exception e) {
            System.err.println("[AgentUpgrade] 无法写入 " + logFile.getAbsolutePath() + ": " + e.getMessage());
        }
    }

    public static void info(String dataPath, String message) {
        append(dataPath, "INFO", message);
    }

    public static void error(String dataPath, String message) {
        append(dataPath, "ERROR", message);
    }

    public static void fail(String dataPath, String message) {
        append(dataPath, "FAIL", message);
    }
}
