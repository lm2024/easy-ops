package com.ops.agent.arthas;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Arthas 启动器
 * 负责检查/释放 Arthas 包、构建启动命令、启动进程、等待就绪
 */
@Component
public class ArthasBootstrap {
    private static final Logger log = LoggerFactory.getLogger(ArthasBootstrap.class);

    @Value("${agent.data-path:/app/data}")
    private String agentDataPath;

    @Value("${agent.arthas.attach-timeout-seconds:15}")
    private int attachTimeoutSeconds;

    /**
     * 获取 Arthas 家目录
     */
    public String getArthasHome() {
        return agentDataPath + "/arthas";
    }

    /**
     * 获取 heapdump 目录
     */
    public String getHeapdumpDir() {
        return agentDataPath + "/arthas/heapdump";
    }

    /**
     * 确保 Arthas 完整包已就绪
     * 检查 arthas-core.jar 是否存在，不存在则从 classpath 解压 arthas-bin.zip
     */
    public void ensureArthasReady() {
        File arthasHome = new File(getArthasHome());
        File coreJar = new File(arthasHome, "arthas-core.jar");
        if (coreJar.exists()) {
            log.debug("Arthas 完整包已存在: {}", arthasHome.getAbsolutePath());
            return;
        }
        // 目录不存在则创建
        if (!arthasHome.exists()) {
            arthasHome.mkdirs();
        }
        // 从 classpath 解压完整包
        extractZipFromClasspath("arthas/arthas-bin.zip", arthasHome);
        log.info("Arthas 完整包已解压: {}", arthasHome.getAbsolutePath());
    }

    /**
     * 从 classpath 解压 zip 文件到目标目录
     */
    private void extractZipFromClasspath(String classpathResource, File targetDir) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(classpathResource)) {
            if (is == null) {
                log.warn("classpath 中未找到 Arthas 完整包: {}", classpathResource);
                return;
            }
            java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(is);
            java.util.zip.ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                File entryFile = new File(targetDir, entry.getName());
                if (entry.isDirectory()) {
                    entryFile.mkdirs();
                } else {
                    entryFile.getParentFile().mkdirs();
                    try (java.io.FileOutputStream fos = new java.io.FileOutputStream(entryFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
            zis.close();
        } catch (Exception e) {
            log.error("解压 Arthas 完整包失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 启动 Arthas 并 attach 到目标 PID
     * @param pid 目标进程 PID
     * @param port Arthas HTTP API 端口
     * @return 启动的进程
     */
    public Process start(long pid, int port) throws Exception {
        ensureArthasReady();
        ensureHeapdumpDir();

        String bootJar = getArthasHome() + "/arthas-boot.jar";
        String arthasHome = getArthasHome();

        // 构建启动命令
        // Arthas 4.x 参数：PID 作为位置参数，--target-ip 指定监听 IP
        // --attach-only: 只 attach，不启动 Telnet 交互
        // --http-port: HTTP API 端口
        // --target-ip: 只监听本地
        // --tunnel-server: 不使用 Tunnel Server
        String[] cmd = new String[]{
                "java",
                "-jar", bootJar,
                "--attach-only",
                "--http-port", String.valueOf(port),
                "--target-ip", "127.0.0.1",
                "--tunnel-server", "",
                "--arthas-home", arthasHome,
                String.valueOf(pid)
        };

        log.info("启动 Arthas: pid={}, port={}, cmd={}", pid, port, String.join(" ", cmd));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        pb.directory(new File(arthasHome));
        Process process = pb.start();

        // 消费进程输出，防止 buffer 满导致阻塞
        consumeProcessOutput(process);

        return process;
    }

    /**
     * 消费进程输出（异步，防止 buffer 满）
     */
    private void consumeProcessOutput(Process process) {
        Thread drain = new Thread(() -> {
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.debug("[arthas] {}", line);
                }
            } catch (Exception ignored) {
            }
        });
        drain.setDaemon(true);
        drain.start();
    }

    /**
     * 等待 Arthas HTTP API 就绪
     * @param port 端口
     * @return 是否就绪
     */
    public boolean waitUntilReady(ArthasHttpClient httpClient, int port) {
        long deadline = System.currentTimeMillis() + attachTimeoutSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            try {
                ArthasHttpClient.ArthasResult result = httpClient.exec(port, "version", 3000);
                if (result.isSuccess() && result.getResults() != null && !result.getResults().isEmpty()) {
                    log.info("Arthas HTTP API 就绪: port={}", port);
                    return true;
                }
            } catch (Exception ignored) {
                // 还没就绪，继续等
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        log.error("Arthas HTTP API 就绪超时: port={}, timeout={}s", port, attachTimeoutSeconds);
        return false;
    }

    /**
     * 确保 heapdump 目录存在
     */
    private void ensureHeapdumpDir() {
        File dir = new File(getHeapdumpDir());
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    /**
     * 停止 Arthas 进程
     */
    public void stop(Process process) {
        if (process == null) {
            return;
        }
        try {
            process.destroy();
            boolean exited = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            if (!exited) {
                process.destroyForcibly();
                log.warn("Arthas 进程强制终止");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }
}
