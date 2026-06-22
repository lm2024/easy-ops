package com.ops.agent.controller;

import com.ops.common.response.Result;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Agent进程管理接口
 * 通过执行 start.sh / stop.sh 脚本来管理应用生命周期
 * 所有脚本均在 deployDir 目录下执行
 */
@RestController
@RequestMapping("/process")
public class ProcessController {

    /**
     * POST /api/process/{projectId}/start - 启动应用
     * 流程：保存 start.sh → 复制 jar 到 deployDir → cd deployDir → 执行 start.sh
     */
    @PostMapping("/{projectId}/start")
    public Result<Map<String, Object>> start(@PathVariable String projectId,
                                              @RequestBody Map<String, String> body) {
        String startScript = body.get("startScript");
        String deployDir = body.get("deployDir");
        String jarPath = body.get("jarPath");
        String jarName = body.get("jarName");
        if (startScript == null || startScript.isEmpty()) {
            return Result.paramError("startScript 不能为空");
        }
        if (deployDir == null || deployDir.isEmpty()) {
            deployDir = "/app/data/versions/" + projectId;
        }

        try {
            // 确保部署目录存在
            File dir = new File(deployDir);
            if (!dir.exists()) dir.mkdirs();

            // 写入 start.sh
            writeScript(deployDir, "start.sh", startScript);

            // 将 jar 从存储路径复制到 deployDir，使用项目配置的 jarName
            if (jarPath != null && !jarPath.isEmpty() && jarName != null && !jarName.isEmpty()) {
                File srcJar = new File(jarPath);
                if (srcJar.exists()) {
                    File destJar = new File(deployDir, jarName);
                    copyFile(srcJar, destJar);
                }
            }

            // 用双重 nohup 完全脱离父进程
            // 用双引号包裹目录路径，防止空格导致 shell 解析错误
            Runtime.getRuntime().exec(new String[]{
                "/bin/sh", "-c",
                "cd \"" + deployDir + "\" && nohup sh start.sh > /dev/null 2>&1 &"
            });

            Map<String, Object> data = new HashMap<>();
            data.put("projectId", projectId);
            data.put("deployDir", deployDir);
            data.put("status", "STARTED");
            return Result.success(data);
        } catch (Exception e) {
            return Result.error(500, "启动失败: " + e.getMessage());
        }
    }

    /**
     * POST /api/process/{projectId}/stop - 停止应用
     * 流程：保存 stop.sh → cd deployDir → 执行 stop.sh
     */
    @PostMapping("/{projectId}/stop")
    public Result<Map<String, Object>> stop(@PathVariable String projectId,
                                             @RequestBody Map<String, String> body) {
        String stopScript = body.get("stopScript");
        String deployDir = body.get("deployDir");
        if (deployDir == null || deployDir.isEmpty()) {
            deployDir = "/app/data/versions/" + projectId;
        }

        try {
            // 如果有 stopScript 就执行
            if (stopScript != null && !stopScript.isEmpty()) {
                writeScript(deployDir, "stop.sh", stopScript);
                Process stopProcess = Runtime.getRuntime().exec(new String[]{
                    "/bin/sh", "-c",
                    "cd \"" + deployDir + "\" && sh stop.sh"
                });
                stopProcess.waitFor(); // 等待停止完成
            } else {
                // 兜底：根据 deployDir 找到并杀死进程
                String killCmd = "ps aux | grep " + deployDir + " | grep -v grep | awk '{print $2}' | xargs kill -9 2>/dev/null";
                Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", killCmd});
            }

            Map<String, Object> data = new HashMap<>();
            data.put("projectId", projectId);
            data.put("deployDir", deployDir);
            data.put("status", "STOPPED");
            return Result.success(data);
        } catch (Exception e) {
            return Result.error(500, "停止失败: " + e.getMessage());
        }
    }

    /**
     * POST /api/process/{projectId}/restart - 重启应用
     */
    @PostMapping("/{projectId}/restart")
    public Result<Map<String, Object>> restart(@PathVariable String projectId,
                                                @RequestBody Map<String, String> body) {
        // 先停再启
        stop(projectId, body);
        try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
        return start(projectId, body);
    }

    // ====== 私有方法 ======

    private void writeScript(String dir, String fileName, String content) throws IOException {
        File scriptFile = new File(dir, fileName);
        try (FileWriter fw = new FileWriter(scriptFile)) {
            fw.write(content);
        }
        // 设置可执行权限
        scriptFile.setExecutable(true);
    }

    /** 复制文件（用于将 jar 从 versions 目录复制到 deployDir） */
    private void copyFile(File src, File dest) throws IOException {
        if (dest.exists()) dest.delete();
        java.io.InputStream in = new java.io.FileInputStream(src);
        java.io.OutputStream out = new java.io.FileOutputStream(dest);
        byte[] buf = new byte[8192];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        in.close();
        out.close();
    }
}
