package com.ops.agent.service;

import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Shell 命令与路径补全（基于 bash compgen，在 Agent 节点本地执行）。
 */
@Service
public class ShellCompletionService {

    /**
     * 根据当前行与光标位置返回补全候选。
     *
     * @param cwd    工作目录
     * @param line   当前输入行
     * @param cursor 光标位置（0-based）
     * @return 候选列表
     */
    public List<String> complete(String cwd, String line, int cursor) {
        if (line == null) {
            line = "";
        }
        if (cursor < 0 || cursor > line.length()) {
            cursor = line.length();
        }
        String safeCwd = cwd == null || cwd.trim().isEmpty() ? "/" : cwd.trim();
        String script = buildCompletionScript(safeCwd, line, cursor);
        try {
            String os = System.getProperty("os.name").toLowerCase();
            String[] cmd = os.contains("windows")
                    ? new String[]{"cmd.exe", "/c", script}
                    : new String[]{"/bin/bash", "-c", script};
            Process p = Runtime.getRuntime().exec(cmd);
            Set<String> unique = new LinkedHashSet<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String row;
                while ((row = reader.readLine()) != null) {
                    row = row.trim();
                    if (!row.isEmpty()) {
                        unique.add(row);
                    }
                }
            }
            p.waitFor();
            List<String> list = new ArrayList<>(unique);
            if (list.size() > 80) {
                return list.subList(0, 80);
            }
            return list;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private String buildCompletionScript(String cwd, String line, int cursor) {
        String qCwd = shellQuote(cwd);
        String qLine = shellQuote(line);
        return "CWD=" + qCwd + "; LINE=" + qLine + "; CURSOR=" + cursor + "; "
                + "cd \"$CWD\" 2>/dev/null || cd /; "
                + "PREFIX=\"${LINE:0:$CURSOR}\"; WORD=\"${PREFIX##* }\"; "
                + "if [ -z \"$WORD\" ]; then compgen -ac | head -80; "
                + "elif [[ \"$WORD\" == */* ]] || [[ \"$WORD\" == .* ]] || compgen -G \"$WORD\" >/dev/null 2>&1; then "
                + "compgen -f -- \"$WORD\" | head -80; "
                + "else { compgen -ac -- \"$WORD\"; compgen -f -- \"$WORD\"; } | sort -u | head -80; fi";
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }
}
