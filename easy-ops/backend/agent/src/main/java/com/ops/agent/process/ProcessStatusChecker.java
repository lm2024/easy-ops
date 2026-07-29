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
 * 核心策略：jps（候选）+ ps（交叉验证）+ /proc/pid/cwd（工作目录确认）三保险。
 * 服务器上 java 进程多、jar 可能重名，单靠一种方式容易误判，必须多重验证。
 */
public class ProcessStatusChecker {

    private static final String CHECK_METHOD = "JPS_PS_CWD";
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
     * 三保险策略（按可靠性排序）：
     * 1. jps -lm 获取所有 Java 进程 → 候选 PID 列表
     * 2. 对候选做交叉验证：ps -p <pid> 确认命令行含 jarName + /proc/<pid>/cwd 确认工作目录
     * 3. 回退 ps aux | grep（兼容 jps 不可用场景）
     * 4. 最后尝试 pid 文件
     */
    public Long findPid(String deployDir, String jarName) {
        // === 第一道保险：jps 候选 + ps + cwd 交叉验证 ===
        Long pid = findPidByJpsWithVerification(deployDir, jarName);
        if (pid != null) {
            return pid;
        }

        // === 第二道保险：ps aux | grep（回退，兼容 jps 不可用） ===
        pid = findPidByPs(buildPsCommand(deployDir, jarName));
        if (pid != null && verifyByWorkingDir(pid, deployDir)) {
            return pid;
        }

        // === 第三道保险：ps 只匹配 jarName（宽匹配）+ cwd 验证 ===
        pid = findPidByPs("ps aux | grep '[j]ava' | grep " + shellEscape(jarName));
        if (pid != null && verifyByWorkingDir(pid, deployDir)) {
            return pid;
        }

        // === 最后手段：pid 文件 ===
        return findPidFromFile(deployDir, jarName);
    }

    // ======================== jps 候选 + 交叉验证 ========================

    /**
     * jps -lm 候选 + 交叉验证。jps 不可用时静默跳过，回退到 ps。
     * 候选筛选只要求 jarName 匹配（jps 输出不包含 deployDir），
     * deployDir 精准确认交给后续 verifyByWorkingDir。
     */
    private Long findPidByJpsWithVerification(String deployDir, String jarName) {
        List<Long> candidates = getJpsCandidates(jarName);
        if (candidates.isEmpty()) {
            return null;
        }

        // 对每个候选 PID 做交叉验证
        for (Long pid : candidates) {
            // deployDir 通过 /proc/<pid>/cwd 确认，jarName 通过 ps -p <pid> 确认
            boolean psOk = verifyByPsArgs(pid, jarName);
            boolean cwdOk = verifyByWorkingDir(pid, deployDir);
            if (psOk && cwdOk) {
                return pid;
            }
            // 只有一个候选且 ps 验证通过（cwd 可能因权限失败），也接受
            if (candidates.size() == 1 && psOk) {
                return pid;
            }
        }
        return null;
    }

    /**
     * 用 jps -lm 获取所有 Java 进程，筛选命令行包含 jarName 的 PID。
     * 注意：jps 只输出 jar 文件名不含路径，所以不做 deployDir 匹配。
     */
    private List<Long> getJpsCandidates(String jarName) {
        List<Long> pids = new ArrayList<Long>();
        Process process = null;
        BufferedReader reader = null;
        try {
            process = Runtime.getRuntime().exec(new String[]{"jps", "-lm"});
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            String normJar = jarName.replace('\\', '/');
            while ((line = reader.readLine()) != null) {
                // jps 输出格式：PID 主类名 [参数...]
                // jps 只输出 jar 文件名不含路径，所以只匹配 jarName
                // deployDir 精准确认由后续 verifyByWorkingDir 完成
                if (line.contains(normJar) || line.contains(jarName)) {
                    Long pid = parsePid(line);
                    if (pid != null) {
                        pids.add(pid);
                    }
                }
            }
            process.waitFor();
        } catch (Exception ignored) {
            // jps 不可用
        } finally {
            closeQuietly(reader);
            if (process != null) {
                process.destroy();
            }
        }
        return pids;
    }

    // ======================== ps aux 回退 ========================

    private String buildPsCommand(String deployDir, String jarName) {
        return "ps aux | grep " + shellEscape(deployDir)
                + " | grep " + shellEscape(jarName)
                + " | grep -v grep";
    }

    private Long findPidByPs(String cmd) {
        Process process = null;
        BufferedReader reader = null;
        try {
            process = Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", cmd});
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                Long pid = parsePid(line);
                if (pid != null) {
                    process.waitFor();
                    return pid;
                }
            }
            process.waitFor();
        } catch (Exception ignored) {
        } finally {
            closeQuietly(reader);
            if (process != null) {
                process.destroy();
            }
        }
        return null;
    }

    // ======================== 交叉验证 ========================

    /**
     * 用 ps -p <pid> 验证该进程的命令行参数是否包含 jarName。
     * ps -p 不受列宽限制，可以拿到完整命令行。
     */
    private boolean verifyByPsArgs(Long pid, String jarName) {
        Process process = null;
        BufferedReader reader = null;
        try {
            // ps -p <pid> -o args= 直接输出该进程的完整命令行
            process = Runtime.getRuntime().exec(
                    new String[]{"/bin/sh", "-c", "ps -p " + pid + " -o args= 2>/dev/null"});
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String cmdline = reader.readLine();
            process.waitFor();
            return cmdline != null && cmdline.contains(jarName);
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
