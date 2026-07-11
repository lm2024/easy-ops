package com.ops.agent.upgrade;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AgentBareMetalRestarterTest {

    private final AgentBareMetalRestarter restarter = new AgentBareMetalRestarter();

    @Test
    @DisplayName("生成的重启脚本包含 setsid nohup 与 pid 文件")
    void buildRestartScript_containsDetachedLaunch(@TempDir File tempDir) throws Exception {
        File dataPath = new File(tempDir, "data");
        File jar = new File(tempDir, "agent.jar");
        assertTrue(jar.createNewFile());

        Map<String, String> env = new HashMap<String, String>();
        env.put("AGENT_TOKEN", "test-token");
        env.put("AGENT_SERVER_URL", "http://127.0.0.1:8081/api");
        env.put("AGENT_JAVA_BIN", "/usr/bin/java");
        env.put("AGENT_OLD_PID", "12345");

        File script = restarter.buildRestartScript(jar, dataPath.getAbsolutePath(), env);
        String content = new String(Files.readAllBytes(script.toPath()), StandardCharsets.UTF_8);

        assertTrue(script.canExecute());
        assertTrue(content.contains("setsid nohup"));
        assertTrue(content.contains("agent.pid"));
        assertTrue(content.contains("SUCCESS: 新 Agent 运行中"));
        assertTrue(content.contains("upgrade-restart.log") || content.contains("UPGRADE_LOG"));
        assertTrue(content.contains("OLD_PID"));
    }
}
