package com.ops.server;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;

@SpringBootApplication
@MapperScan("com.ops.server.mapper")
@EnableScheduling
@EnableAsync
public class ServerApplication {

    public static void main(String[] args) {
        // 启动前压缩 H2 数据库：回收删除数据留下的空洞，避免磁盘被数据库文件撑满
        compactBeforeStart();
        SpringApplication.run(ServerApplication.class, args);
    }

    /**
     * 启动前压缩 H2 数据库文件（必须在 Spring 初始化、打开数据库之前执行）。
     *
     * 原理：H2 的 MVStore 只在数据库"关闭"时自动压缩；运行中删除数据只是标记
     * 空闲页，文件只增不减。若等磁盘满再处理，服务可能已无法启动（死循环）。
     * 因此每次启动前主动执行 SHUTDOWN COMPACT，保证启动后文件是瘦的。
     *
     * 行为：数据库文件超过阈值才压缩；阈值可用环境变量 COMPACT_THRESHOLD_MB
     * 调整（默认 256MB，设 0 表示每次启动都强制压缩）。压缩失败不影响启动。
     */
    private static void compactBeforeStart() {
        try {
            Path dbFile = resolveDbFile();
            if (dbFile == null || !Files.exists(dbFile)) {
                return;
            }
            long sizeMb = Files.size(dbFile) / 1024 / 1024;
            long thresholdMb = parseThresholdMb();
            if (thresholdMb > 0 && sizeMb < thresholdMb) {
                System.out.println("[compact] 数据库 " + dbFile + " (" + sizeMb + "MB) 小于阈值 "
                        + thresholdMb + "MB，跳过压缩");
                return;
            }
            // 残留锁文件清理（能走到这里说明主服务未运行；残留=上次异常退出遗留的"使用中"挂牌）
            Path lock = Paths.get(dbFile.toString() + ".lock.db");
            if (Files.exists(lock)) {
                System.out.println("[compact] WARN 发现残留锁文件，删除: " + lock);
                Files.deleteIfExists(lock);
            }
            System.out.println("[compact] 开始压缩 " + dbFile + " (" + sizeMb + "MB) ...");
            String base = dbFile.toString();
            if (base.endsWith(".mv.db")) {
                base = base.substring(0, base.length() - 6);
            }
            Class.forName("org.h2.Driver");
            Connection conn = null;
            try {
                conn = DriverManager.getConnection("jdbc:h2:file:" + base + ";MODE=MySQL", "sa", "");
                conn.createStatement().execute("SHUTDOWN COMPACT");
                long afterMb = Files.size(dbFile) / 1024 / 1024;
                System.out.println("[compact] 压缩完成: " + sizeMb + "MB -> " + afterMb + "MB");
            } finally {
                if (conn != null) {
                    try {
                        conn.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[compact] WARN 压缩跳过（不影响启动）: " + e.getMessage());
        }
    }

    /**
     * 从 spring.datasource.url 系统属性解析数据库文件路径（含 .mv.db 后缀）。
     * 例：jdbc:h2:file:/opt/easyops/data/ops;MODE=MySQL... -> /opt/easyops/data/ops.mv.db
     */
    private static Path resolveDbFile() {
        String url = System.getProperty("spring.datasource.url");
        if (url == null || url.isEmpty()) {
            return null;
        }
        int idx = url.indexOf("file:");
        if (idx < 0) {
            return null;
        }
        String p = url.substring(idx + 5);
        int semi = p.indexOf(';');
        if (semi >= 0) {
            p = p.substring(0, semi);
        }
        if (!p.endsWith(".mv.db")) {
            p = p + ".mv.db";
        }
        return Paths.get(p).toAbsolutePath().normalize();
    }

    /** 压缩阈值：环境变量 COMPACT_THRESHOLD_MB，默认 256；0 表示每次启动都强制压缩 */
    private static long parseThresholdMb() {
        String v = System.getenv("COMPACT_THRESHOLD_MB");
        if (v == null || v.trim().isEmpty()) {
            return 256;
        }
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return 256;
        }
    }
}
