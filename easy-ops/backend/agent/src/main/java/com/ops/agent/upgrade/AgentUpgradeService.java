package com.ops.agent.upgrade;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Agent 自升级服务：接收新 Jar 包并触发安全重启。
 * 裸机模式：脚本等待旧进程退出后拉起新进程；失败则自动回滚 jar 并写 upgrade-restart.log。
 */
@Service
public class AgentUpgradeService {

    private static final int OLD_PROCESS_EXIT_DELAY_SEC = 2;

    @Value("${agent.data-path:/app/data}")
    private String dataPath;

    @Value("${agent.version:1.0.0-SNAPSHOT}")
    private String agentVersion;

    @Value("${agent.jar-path:/app/agent.jar}")
    private String jarPath;

    @Value("${agent.restart-mode:auto}")
    private String restartMode;

    @Value("${agent.restart-script:}")
    private String restartScriptPath;

    @Value("${agent.token:}")
    private String agentToken;

    @Value("${agent.server-url:}")
    private String serverUrl;

    @Value("${agent.node-name:}")
    private String nodeName;

    @Value("${agent.java-bin:}")
    private String javaBin;

    @Value("${server.port:2123}")
    private int agentPort;

    private final AgentBareMetalRestarter bareMetalRestarter = new AgentBareMetalRestarter();

    /**
     * 获取当前 Agent 版本信息。
     */
    public Map<String, Object> versionInfo() {
        Map<String, Object> info = new HashMap<String, Object>();
        info.put("version", agentVersion);
        info.put("jarPath", jarPath);
        info.put("javaVersion", System.getProperty("java.version"));
        info.put("osArch", System.getProperty("os.arch"));
        info.put("pid", currentPid());
        info.put("restartMode", resolveRestartModeLabel());
        info.put("deploymentType", isDockerRestartMode() ? "docker" : "bare-metal");
        info.put("upgradeLogPath", AgentUpgradeLog.resolveLogFile(dataPath).getAbsolutePath());
        return info;
    }

    private long currentPid() {
        try {
            String name = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
            int at = name.indexOf('@');
            if (at > 0) {
                return Long.parseLong(name.substring(0, at));
            }
        } catch (Exception ignored) {
            // ignore
        }
        return -1L;
    }

    /**
     * 接收升级包并异步重启。
     */
    public Map<String, Object> upgrade(MultipartFile file, String expectedSha256) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IOException("升级包不能为空");
        }
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase().endsWith(".jar")) {
            throw new IOException("仅支持 .jar 升级包");
        }

        File stagingDir = new File(dataPath, "staging");
        if (!stagingDir.exists() && !stagingDir.mkdirs()) {
            throw new IOException("无法创建 staging 目录");
        }
        File stagingJar = new File(stagingDir, "agent-upgrade.jar");
        String sha256 = writeWithSha256(file.getInputStream(), stagingJar);
        if (expectedSha256 != null && !expectedSha256.trim().isEmpty()
                && !expectedSha256.trim().equalsIgnoreCase(sha256)) {
            stagingJar.delete();
            throw new IOException("SHA-256 校验失败");
        }

        File targetJar = new File(jarPath);
        if (!targetJar.getParentFile().exists() && !targetJar.getParentFile().mkdirs()) {
            throw new IOException("无法创建目标目录: " + targetJar.getParent());
        }
        scheduleRestart(stagingJar, targetJar);

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("status", "UPGRADING");
        result.put("currentVersion", agentVersion);
        result.put("sha256", sha256);
        result.put("targetJar", targetJar.getAbsolutePath());
        result.put("restartMode", resolveRestartModeLabel());
        result.put("upgradeLogPath", AgentUpgradeLog.resolveLogFile(dataPath).getAbsolutePath());
        result.put("message", isDockerRestartMode()
                ? "升级包已接收，Agent 即将退出并由 Docker 重启"
                : "升级包已接收，将验证新进程后再退出旧进程；日志见 upgrade-restart.log");
        return result;
    }

    private String writeWithSha256(InputStream in, File dest) throws IOException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new IOException("SHA-256 不可用", e);
        }
        if (dest.exists() && !dest.delete()) {
            throw new IOException("无法覆盖临时文件");
        }
        try (OutputStream out = Files.newOutputStream(dest.toPath())) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
                md.update(buffer, 0, len);
            }
        }
        return bytesToHex(md.digest());
    }

    private void scheduleRestart(final File stagingJar, final File targetJar) {
        final long oldPid = currentPid();
        Thread restartThread = new Thread(new Runnable() {
            @Override
            public void run() {
                File backupJar = new File(new File(dataPath, "staging"), "agent-backup.jar");
                try {
                    AgentUpgradeLog.info(dataPath, "=== Agent 自升级开始，旧 PID=" + oldPid + " ===");
                    TimeUnit.SECONDS.sleep(1);

                    if (targetJar.exists()) {
                        copyFile(targetJar, backupJar);
                        AgentUpgradeLog.info(dataPath, "已备份当前 jar → " + backupJar.getAbsolutePath());
                    }

                    if (!stagingJar.renameTo(targetJar)) {
                        copyFile(stagingJar, targetJar);
                        stagingJar.delete();
                    }
                    AgentUpgradeLog.info(dataPath, "新 jar 已就位: " + targetJar.getAbsolutePath());

                    if (isDockerRestartMode()) {
                        AgentUpgradeLog.info(dataPath, "Docker 模式：进程即将退出，由容器 restart 拉起");
                        TimeUnit.MILLISECONDS.sleep(500);
                        System.exit(0);
                        return;
                    }

                    restartBareMetal(targetJar, oldPid);
                    AgentUpgradeLog.info(dataPath, "重启脚本已启动；旧进程 " + OLD_PROCESS_EXIT_DELAY_SEC
                            + " 秒后退出以释放端口 " + agentPort + "，后续由脚本拉起并验证新进程");
                    TimeUnit.SECONDS.sleep(OLD_PROCESS_EXIT_DELAY_SEC);
                    System.exit(0);
                } catch (Exception e) {
                    AgentUpgradeLog.fail(dataPath, "升级重启异常: " + e.getMessage());
                    try {
                        rollbackJar(backupJar, targetJar);
                    } catch (Exception rollbackEx) {
                        AgentUpgradeLog.fail(dataPath, "回滚失败: " + rollbackEx.getMessage());
                    }
                }
            }
        }, "agent-upgrade-restart");
        restartThread.setDaemon(false);
        restartThread.start();
    }

    private void restartBareMetal(File targetJar, long oldPid) throws IOException, InterruptedException {
        Map<String, String> envOverrides = buildEnvOverrides(targetJar);
        envOverrides.put("AGENT_OLD_PID", String.valueOf(oldPid));
        File backupJar = new File(new File(dataPath, "staging"), "agent-backup.jar");
        if (backupJar.exists()) {
            envOverrides.put("AGENT_BACKUP_JAR", backupJar.getAbsolutePath());
        }
        File externalScript = resolveExternalRestartScript();
        if (externalScript != null) {
            AgentUpgradeLog.info(dataPath, "使用外置重启脚本: " + externalScript.getAbsolutePath());
            bareMetalRestarter.launchExternalScript(externalScript, targetJar, envOverrides);
            return;
        }
        AgentUpgradeLog.info(dataPath, "未配置外置脚本，使用内置 setsid 重启");
        bareMetalRestarter.launchDetachedRestart(targetJar, dataPath, envOverrides);
    }

    private Map<String, String> buildEnvOverrides(File targetJar) {
        Map<String, String> env = new HashMap<String, String>();
        env.put("AGENT_DATA_PATH", dataPath);
        env.put("AGENT_JAR_PATH", targetJar.getAbsolutePath());
        env.put("AGENT_RESTART_MODE", "shell");
        env.put("AGENT_HOST_PORT", String.valueOf(agentPort));
        if (agentToken != null && !agentToken.trim().isEmpty()) {
            env.put("AGENT_TOKEN", agentToken.trim());
        }
        if (serverUrl != null && !serverUrl.trim().isEmpty()) {
            env.put("AGENT_SERVER_URL", serverUrl.trim());
        }
        if (nodeName != null && !nodeName.trim().isEmpty()) {
            env.put("AGENT_NODE_NAME", nodeName.trim());
        }
        if (agentVersion != null && !agentVersion.trim().isEmpty()) {
            env.put("AGENT_VERSION", agentVersion.trim());
        }
        if (javaBin != null && !javaBin.trim().isEmpty()) {
            env.put("AGENT_JAVA_BIN", javaBin.trim());
        }
        if (restartScriptPath != null && !restartScriptPath.trim().isEmpty()) {
            env.put("AGENT_RESTART_SCRIPT", restartScriptPath.trim());
        }
        return env;
    }

    private File resolveExternalRestartScript() {
        if (restartScriptPath == null || restartScriptPath.trim().isEmpty()) {
            AgentUpgradeLog.info(dataPath, "agent.restart-script 未配置，跳过外置脚本");
            return null;
        }
        File script = new File(restartScriptPath.trim());
        if (!script.isFile()) {
            AgentUpgradeLog.fail(dataPath, "外置重启脚本不存在: " + script.getAbsolutePath());
            return null;
        }
        if (!script.canRead()) {
            AgentUpgradeLog.fail(dataPath, "外置重启脚本不可读: " + script.getAbsolutePath());
            return null;
        }
        return script;
    }

    private void rollbackJar(File backupJar, File targetJar) throws IOException {
        if (backupJar.exists()) {
            copyFile(backupJar, targetJar);
            AgentUpgradeLog.info(dataPath, "已从备份回滚 jar: " + targetJar.getAbsolutePath());
        } else {
            AgentUpgradeLog.error(dataPath, "无备份 jar，无法自动回滚");
        }
    }

    boolean isDockerRestartMode() {
        if ("docker".equalsIgnoreCase(restartMode)) {
            return true;
        }
        if ("shell".equalsIgnoreCase(restartMode)) {
            return false;
        }
        return new File("/.dockerenv").exists();
    }

    private String resolveRestartModeLabel() {
        return isDockerRestartMode() ? "docker" : "shell";
    }

    private void copyFile(File src, File dest) throws IOException {
        try (InputStream in = Files.newInputStream(src.toPath());
             OutputStream out = Files.newOutputStream(dest.toPath())) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
