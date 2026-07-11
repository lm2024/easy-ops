package com.ops.agent.upgrade;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 裸机（无 Docker）环境下安全拉起新版 Agent。
 * 使用 setsid + nohup 脱离当前进程组；所有步骤写入 upgrade-restart.log。
 */
public class AgentBareMetalRestarter {

    private static final String[] PASSTHROUGH_ENV_KEYS = {
            "AGENT_TOKEN",
            "AGENT_SERVER_URL",
            "AGENT_NODE_NAME",
            "AGENT_JAR_PATH",
            "AGENT_DATA_PATH",
            "AGENT_RESTART_MODE",
            "AGENT_RESTART_SCRIPT",
            "AGENT_VERSION",
            "AGENT_HOST_IP",
            "AGENT_HOST_PORT",
            "JAVA_TOOL_OPTIONS",
            "JAVA_OPTS"
    };

    /**
     * 生成并后台执行重启脚本，输出写入 upgrade-restart.log。
     */
    public void launchDetachedRestart(File targetJar, String dataPath, Map<String, String> envOverrides)
            throws IOException, InterruptedException {
        File script = buildRestartScript(targetJar, dataPath, envOverrides);
        File upgradeLog = AgentUpgradeLog.resolveLogFile(dataPath);
        String cmd = "setsid /bin/sh '" + shellEscapePath(script.getAbsolutePath())
                + "' </dev/null >> '" + shellEscapePath(upgradeLog.getAbsolutePath()) + "' 2>&1 &";
        ProcessBuilder launcher = new ProcessBuilder("/bin/sh", "-c", cmd);
        launcher.start();
        AgentUpgradeLog.info(dataPath, "已后台执行内置重启脚本: " + script.getAbsolutePath());
    }

    /**
     * 调用外置重启脚本（如 scripts/start.sh），输出写入 upgrade-restart.log。
     */
    public void launchExternalScript(File externalScript, File targetJar, Map<String, String> envOverrides)
            throws IOException, InterruptedException {
        String dataPath = envOverrides != null && envOverrides.containsKey("AGENT_DATA_PATH")
                ? envOverrides.get("AGENT_DATA_PATH")
                : targetJar.getParent();
        File upgradeLog = AgentUpgradeLog.resolveLogFile(dataPath);

        StringBuilder cmd = new StringBuilder();
        cmd.append("export AGENT_UPGRADE=1; ");
        cmd.append("export AGENT_UPGRADE_JAR='").append(shellEscapeSingleQuoted(targetJar.getAbsolutePath())).append("'; ");
        if (envOverrides != null) {
            for (Map.Entry<String, String> entry : envOverrides.entrySet()) {
                if (entry.getValue() == null) {
                    continue;
                }
                cmd.append("export ").append(entry.getKey()).append("='")
                        .append(shellEscapeSingleQuoted(entry.getValue())).append("'; ");
            }
        }
        cmd.append("setsid /bin/sh '").append(shellEscapePath(externalScript.getAbsolutePath()))
                .append("' </dev/null >> '").append(shellEscapePath(upgradeLog.getAbsolutePath()))
                .append("' 2>&1 &");

        ProcessBuilder launcher = new ProcessBuilder("/bin/sh", "-c", cmd.toString());
        launcher.start();
        AgentUpgradeLog.info(dataPath, "已后台执行外置重启脚本: " + externalScript.getAbsolutePath());
    }

  /**
     * 生成带自检与结果验证的重启脚本。
     */
    File buildRestartScript(File targetJar, String dataPath, Map<String, String> envOverrides) throws IOException {
        File stagingDir = new File(dataPath, "staging");
        if (!stagingDir.exists() && !stagingDir.mkdirs()) {
            throw new IOException("无法创建 staging 目录: " + stagingDir.getAbsolutePath());
        }
        File script = new File(stagingDir, "restart-agent.sh");

        Map<String, String> env = new LinkedHashMap<String, String>();
        for (String key : PASSTHROUGH_ENV_KEYS) {
            String val = System.getenv(key);
            if (val != null && !val.trim().isEmpty()) {
                env.put(key, val.trim());
            }
        }
        if (envOverrides != null) {
            env.putAll(envOverrides);
        }
        env.put("AGENT_JAR_PATH", targetJar.getAbsolutePath());
        env.put("AGENT_DATA_PATH", dataPath);
        env.put("AGENT_RESTART_MODE", "shell");

        String javaBin = resolveJavaBin(env.remove("AGENT_JAVA_BIN"));
        String agentPort = env.containsKey("AGENT_HOST_PORT") ? env.get("AGENT_HOST_PORT") : "2123";
        String oldPid = env.containsKey("AGENT_OLD_PID") ? env.get("AGENT_OLD_PID") : "0";
        String backupJar = env.containsKey("AGENT_BACKUP_JAR") ? env.get("AGENT_BACKUP_JAR") : "";
        File installDir = targetJar.getParentFile() != null ? targetJar.getParentFile() : new File("/app");
        File logDir = new File(dataPath, "logs");
        File pidFile = new File(logDir, "agent.pid");
        File upgradeLog = new File(logDir, AgentUpgradeLog.LOG_FILE_NAME);
        File agentLog = new File(logDir, "agent.log");

        StringBuilder sb = new StringBuilder();
        sb.append("#!/bin/sh\n");
        sb.append("# EasyOps Agent bare-metal restart (auto-generated)\n");
        sb.append("set -u\n");
        sb.append("AGENT_JAR='").append(shellEscapeSingleQuoted(targetJar.getAbsolutePath())).append("'\n");
        sb.append("JAVA_BIN='").append(shellEscapeSingleQuoted(javaBin)).append("'\n");
        sb.append("AGENT_DIR='").append(shellEscapeSingleQuoted(installDir.getAbsolutePath())).append("'\n");
        sb.append("LOG_DIR='").append(shellEscapeSingleQuoted(logDir.getAbsolutePath())).append("'\n");
        sb.append("PID_FILE='").append(shellEscapeSingleQuoted(pidFile.getAbsolutePath())).append("'\n");
        sb.append("UPGRADE_LOG='").append(shellEscapeSingleQuoted(upgradeLog.getAbsolutePath())).append("'\n");
        sb.append("AGENT_LOG='").append(shellEscapeSingleQuoted(agentLog.getAbsolutePath())).append("'\n");
        sb.append("AGENT_PORT='").append(shellEscapeSingleQuoted(agentPort)).append("'\n");
        sb.append("OLD_PID='").append(shellEscapeSingleQuoted(oldPid)).append("'\n");
        sb.append("BACKUP_JAR='").append(shellEscapeSingleQuoted(backupJar)).append("'\n");
        for (Map.Entry<String, String> entry : env.entrySet()) {
            sb.append("export ").append(entry.getKey()).append("='")
                    .append(shellEscapeSingleQuoted(entry.getValue())).append("'\n");
        }
        sb.append("log() { echo \"[$(date '+%Y-%m-%d %H:%M:%S')] $*\" >> \"$UPGRADE_LOG\"; }\n");
        sb.append("fail() { log \"FAIL: $*\"; exit 1; }\n");
        sb.append("log '=== 内置重启脚本开始 ==='\n");
        sb.append("if [ -n \"$OLD_PID\" ] && [ \"$OLD_PID\" != \"0\" ]; then\n");
        sb.append("  log \"等待旧进程 OLD_PID=$OLD_PID 退出（释放端口）...\"\n");
        sb.append("  for _w in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30; do\n");
        sb.append("    kill -0 \"$OLD_PID\" 2>/dev/null || break\n");
        sb.append("    sleep 1\n");
        sb.append("  done\n");
        sb.append("  if kill -0 \"$OLD_PID\" 2>/dev/null; then\n");
        sb.append("    fail \"旧进程 $OLD_PID 在 30 秒内未退出，端口可能仍被占用\"\n");
        sb.append("  fi\n");
        sb.append("  log \"旧进程已退出\"\n");
        sb.append("fi\n");
        sb.append("mkdir -p \"$LOG_DIR\" || fail \"无法创建日志目录 $LOG_DIR\"\n");
        sb.append("[ -f \"$AGENT_JAR\" ] || fail \"Jar 不存在: $AGENT_JAR\"\n");
        sb.append("if [ ! -x \"$JAVA_BIN\" ] && [ ! -f \"$JAVA_BIN\" ]; then\n");
        sb.append("  fail \"Java 不可用: $JAVA_BIN（请设置 JAVA_HOME 或 AGENT_JAVA_BIN）\"\n");
        sb.append("fi\n");
        sb.append("cd \"$AGENT_DIR\" || fail \"无法进入目录 $AGENT_DIR\"\n");
        sb.append("OLD_PID=$(cat \"$PID_FILE\" 2>/dev/null || true)\n");
        sb.append("log \"旧 PID=${OLD_PID:-无}\"\n");
        sb.append("setsid nohup \"$JAVA_BIN\" -jar \"$AGENT_JAR\" \\\n");
        sb.append("  -Dagent.data-path='").append(shellEscapeSingleQuoted(dataPath)).append("' \\\n");
        sb.append("  -Dagent.jar-path=\"$AGENT_JAR\" \\\n");
        sb.append("  -Dserver.port=\"$AGENT_PORT\" >> \"$AGENT_LOG\" 2>&1 </dev/null &\n");
        sb.append("NEW_PID=$!\n");
        sb.append("echo \"$NEW_PID\" > \"$PID_FILE\"\n");
        sb.append("log \"已启动新进程 PID=$NEW_PID\"\n");
        sb.append("sleep 3\n");
        sb.append("if kill -0 \"$NEW_PID\" 2>/dev/null; then\n");
        sb.append("  log \"SUCCESS: 新 Agent 运行中 PID=$NEW_PID\"\n");
        sb.append("else\n");
        sb.append("  log \"FAIL: 新进程已退出，最近 agent.log:\"\n");
        sb.append("  tail -n 30 \"$AGENT_LOG\" >> \"$UPGRADE_LOG\" 2>/dev/null || true\n");
        sb.append("  if [ -n \"$BACKUP_JAR\" ] && [ -f \"$BACKUP_JAR\" ]; then\n");
        sb.append("    log \"尝试用备份 jar 回滚拉起: $BACKUP_JAR\"\n");
        sb.append("    cp \"$BACKUP_JAR\" \"$AGENT_JAR\"\n");
        sb.append("    setsid nohup \"$JAVA_BIN\" -jar \"$AGENT_JAR\" \\\n");
        sb.append("      -Dagent.data-path='").append(shellEscapeSingleQuoted(dataPath)).append("' \\\n");
        sb.append("      -Dagent.jar-path=\"$AGENT_JAR\" \\\n");
        sb.append("      -Dserver.port=\"$AGENT_PORT\" >> \"$AGENT_LOG\" 2>&1 </dev/null &\n");
        sb.append("    RB_PID=$!\n");
        sb.append("    echo \"$RB_PID\" > \"$PID_FILE\"\n");
        sb.append("    sleep 3\n");
        sb.append("    if kill -0 \"$RB_PID\" 2>/dev/null; then\n");
        sb.append("      log \"ROLLBACK OK: 已用备份 jar 恢复 PID=$RB_PID\"\n");
        sb.append("      exit 0\n");
        sb.append("    fi\n");
        sb.append("    fail \"备份 jar 回滚后仍无法启动，请人工检查 $UPGRADE_LOG 与 $AGENT_LOG\"\n");
        sb.append("  fi\n");
        sb.append("  fail \"新进程启动失败且无可用备份，请人工检查\"\n");
        sb.append("fi\n");

        Files.write(script.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
        script.setExecutable(true);
        return script;
    }

    private String resolveJavaBin(String configured) {
        if (configured != null && !configured.trim().isEmpty()) {
            return configured.trim();
        }
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null && !javaHome.trim().isEmpty()) {
            return javaHome.trim() + "/bin/java";
        }
        return System.getProperty("java.home") + "/bin/java";
    }

    private String shellEscapeSingleQuoted(String value) {
        return value.replace("'", "'\"'\"'");
    }

    private String shellEscapePath(String path) {
        return path.replace("'", "'\\''");
    }
}
