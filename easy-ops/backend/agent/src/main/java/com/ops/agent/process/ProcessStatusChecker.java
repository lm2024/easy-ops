package com.ops.agent.process;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 进程存活检测：优先 ps+grep，回退 jar 名匹配与 pid 文件。
 */
public class ProcessStatusChecker {

    private static final String CHECK_METHOD = "PS_GREP";
    private static final Pattern PID_PATTERN = Pattern.compile("^\\s*(\\d+)");

    /**
     * 检测指定部署目录与 jar 对应的进程是否存活。
     *
     * @param deployDir 部署目录
     * @param jarName   jar 文件名
     * @return alive、pid、checkMethod
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
     * 策略：deployDir+jarName → jarName(java) → deployDir/pid 文件。
     */
    public Long findPid(String deployDir, String jarName) {
        Long pid = findPidByPs(buildPsGrepCommand(deployDir, jarName));
        if (pid != null) {
            return pid;
        }

        pid = findPidByPs("ps aux | grep '[j]ava' | grep " + shellEscape(jarName));
        if (pid != null) {
            return pid;
        }

        return findPidFromFile(deployDir, jarName);
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
            // 检测失败视为未存活
        } finally {
            closeQuietly(reader);
            if (process != null) {
                process.destroy();
            }
        }
        return null;
    }

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
            Long matched = findPidByPs("ps -p " + pid + " -o args= 2>/dev/null | grep " + shellEscape(jarName));
            return matched != null ? pid : null;
        } catch (Exception ignored) {
            return null;
        } finally {
            closeQuietly(reader);
        }
    }

    private String buildPsGrepCommand(String deployDir, String jarName) {
        String safeDir = shellEscape(deployDir);
        String safeJar = shellEscape(jarName);
        return "ps aux | grep " + safeDir + " | grep " + safeJar + " | grep -v grep";
    }

    private Long parsePid(String psLine) {
        if (psLine == null || psLine.trim().isEmpty()) {
            return null;
        }
        // ps aux 格式：USER PID %CPU ...，PID 在第 2 列
        String[] parts = psLine.trim().split("\\s+");
        if (parts.length >= 2) {
            try {
                return Long.valueOf(parts[1]);
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        Matcher matcher = PID_PATTERN.matcher(psLine);
        if (matcher.find()) {
            try {
                return Long.valueOf(matcher.group(1));
            } catch (NumberFormatException ignored) {
                return null;
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
                // ignore
            }
        }
    }
}
