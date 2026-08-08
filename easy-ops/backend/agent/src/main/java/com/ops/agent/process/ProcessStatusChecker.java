package com.ops.agent.process;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 进程存活检测。
 *
 * 核心策略：ps 查找 + /proc/pid/cwd（工作目录确认）双重验证。
 * 不依赖 jps（Docker 中不可靠），直接用 ps 命令查找 Java 进程。
 */
public class ProcessStatusChecker {

    private static final String CHECK_METHOD = "PS_CWD";
    private static final Pattern PID_PATTERN = Pattern.compile("^\\s*(\\d+)");

    /**
     * 检测指定部署目录与 jar 对应的进程是否存活。
     */
    public Map<String, Object> checkStatus(String deployDir, String jarName) {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("checkMethod", CHECK_METHOD);
        result.put("alive", false);
        result.put("pid", null);

        if (isBlank(deployDir) || isBlank(jarName)) {
            result.put("detail", "deployDir 与 jarName 不能为空");
            return result;
        }

        Long pid = findPid(deployDir.trim(), jarName.trim());
        if (pid != null) {
            result.put("alive", true);
            result.put("pid", pid);
        }
        return result;
    }

    /**
     * 查找匹配进程的 PID，未找到返回 null。
     *
     * 策略：列出所有 Java 进程 → 从 /proc/pid/cmdline 提取 jarName 精确匹配 → cwd 验证 deployDir。
     * 不再用 shell grep 子串匹配（demo.jar 会误匹配 demo-test-app.jar）。
     */
    public Long findPid(String deployDir, String jarName) {
        // 列出所有 Java 进程，在 Java 端按 jarName 精确匹配 + cwd 验证
        List<Long> javaPids = listJavaPids();
        for (Long pid : javaPids) {
            // 跳过僵尸（defunct）进程：已死但未回收，cmdline 已清空，不应匹配
            if (isZombie(pid)) continue;
            String cmdline = readProcCmdline(pid);
            String extractedJar = extractJarName(cmdline);
            if (jarName.equals(extractedJar) && verifyByWorkingDir(pid, deployDir)) {
                return pid;
            }
        }

        // === 最后手段：pid 文件 ===
        return findPidFromFile(deployDir, jarName);
    }

    /** 列出所有 Java 进程的 PID（排除 grep 自身） */
    private List<Long> listJavaPids() {
        List<Long> pids = new ArrayList<Long>();
        Process process = null;
        BufferedReader reader = null;
        try {
            process = Runtime.getRuntime().exec(
                    new String[]{"/bin/sh", "-c",
                            "ps -eo pid:10000,args:10000 2>/dev/null | grep '[j]ava' | awk '{print $1}'"});
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    long pid = Long.parseLong(line.trim());
                    // 跳过僵尸（defunct）进程：已死未回收，上报会显示错误 PID
                    if (isZombie(pid)) continue;
                    pids.add(pid);
                } catch (NumberFormatException ignored) {}
            }
            process.waitFor();
        } catch (Exception ignored) {
        } finally {
            closeQuietly(reader);
            if (process != null) process.destroy();
        }
        return pids;
    }

    /**
     * 判断进程是否为僵尸（defunct）。
     * /proc/<pid>/stat 第 3 个字段为状态，'Z' 表示僵尸。
     * 进程名 (comm) 可能含空格与括号，故取第一个 ')' 之后的首个字段。
     */
    private boolean isZombie(long pid) {
        try {
            java.nio.file.Path stat = java.nio.file.Paths.get("/proc", String.valueOf(pid), "stat");
            if (!java.nio.file.Files.exists(stat)) return false;
            String line = new String(java.nio.file.Files.readAllBytes(stat), "UTF-8");
            int end = line.indexOf(')');
            if (end > 0) {
                String state = line.substring(end + 1).trim().split("\\s+")[0];
                return "Z".equals(state);
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /** 读取 /proc/<pid>/cmdline */
    private String readProcCmdline(Long pid) {
        try {
            byte[] bytes = java.nio.file.Files.readAllBytes(new File("/proc/" + pid + "/cmdline").toPath());
            return new String(bytes, "UTF-8").replace('\0', ' ');
        } catch (Exception e) {
            return "";
        }
    }

    /** 从命令行中提取 jar 文件名（与 HeartbeatDaemon.extractJarName 一致） */
    private String extractJarName(String cmdline) {
        if (cmdline == null) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("-jar\\s+(\\S+\\.jar)").matcher(cmdline);
        if (m.find()) {
            String jar = m.group(1);
            int slash = jar.lastIndexOf('/');
            return slash >= 0 ? jar.substring(slash + 1) : jar;
        }
        return "";
    }

    // ======================== 交叉验证 ========================

    /**
     * 用 ps -p <pid> 验证该进程的命令行中提取的 jarName 是否精确匹配。
     * ps -p 不受列宽限制，可以拿到完整命令行。
     */
    private boolean verifyByPsArgs(Long pid, String jarName) {
        Process process = null;
        BufferedReader reader = null;
        try {
            process = Runtime.getRuntime().exec(
                    new String[]{"/bin/sh", "-c", "ps -p " + pid + " -o args= 2>/dev/null"});
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String cmdline = reader.readLine();
            process.waitFor();
            if (cmdline == null) return false;
            return jarName.equals(extractJarName(cmdline));
        } catch (Exception ignored) {
            return false;
        } finally {
            closeQuietly(reader);
            if (process != null) {
                process.destroy();
            }
        }
    }

    /**
     * 验证进程的工作目录是否匹配 deployDir。
     * Linux: 读取 /proc/<pid>/cwd 符号链接目标
     */
    private boolean verifyByWorkingDir(Long pid, String deployDir) {
        try {
            File cwd = new File("/proc/" + pid + "/cwd");
            if (cwd.exists()) {
                String cwdPath = cwd.getCanonicalPath();
                String normalizedDeployDir = new File(deployDir).getCanonicalPath();
                return cwdPath.equals(normalizedDeployDir)
                        || cwdPath.startsWith(normalizedDeployDir + File.separator)
                        || normalizedDeployDir.startsWith(cwdPath + File.separator)
                        || cwdPath.equals(normalizedDeployDir.replace('\\', '/'));
            }
        } catch (Exception ignored) {
            // Windows 或权限不足时跳过
        }
        // 无法验证时返回 true（不因权限问题误杀）
        return true;
    }

    // ======================== pid 文件回退 ========================

    private Long findPidFromFile(String deployDir, String jarName) {
        File pidFile = new File(deployDir, "pid");
        if (!pidFile.isFile()) {
            return null;
        }
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(pidFile));
            String line = reader.readLine();
            if (line == null || line.trim().isEmpty()) {
                return null;
            }
            long pid = Long.parseLong(line.trim());
            if (pid <= 0) {
                return null;
            }
            // 验证此 PID 确实在运行且命令行包含 jarName
            if (verifyByPsArgs(pid, jarName)) {
                return pid;
            }
        } catch (Exception ignored) {
        } finally {
            closeQuietly(reader);
        }
        return null;
    }

    // ======================== 工具方法 ========================

    /**
     * 从 ps aux 或 jps 输出行中提取 PID（行首第一个数字）。
     * ps aux 格式：USER PID %CPU ...  → PID 在第 2 列
     * jps 格式：   PID CLASSNAME ...
     * 统一用正则提取行首数字，兼容两种格式。
     */
    private Long parsePid(String line) {
        if (line == null || line.trim().isEmpty()) {
            return null;
        }
        Matcher matcher = PID_PATTERN.matcher(line.trim());
        if (matcher.find()) {
            try {
                return Long.valueOf(matcher.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private String shellEscape(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void closeQuietly(BufferedReader reader) {
        if (reader != null) {
            try {
                reader.close();
            } catch (Exception ignored) {
            }
        }
    }
}
