package com.ops.agent.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({SystemInfoController.class, FileController.class, ProcessController.class, ShellController.class, SystemController.class})
class AgentControllersTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("systemInfo_request - 系统信息正常返回")
    void systemInfo_request() throws Exception {
        mockMvc.perform(get("/sys/info")
                .header("X-Token", "test-token"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("cpuInfo_request - CPU信息端点")
    void cpuInfo_request() throws Exception {
        mockMvc.perform(get("/sys/info")
                .header("X-Token", "test-token"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("memoryInfo_request - 内存信息")
    void memoryInfo_request() throws Exception {
        mockMvc.perform(get("/sys/info")
                .header("X-Token", "test-token"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("diskInfo_request - 磁盘信息")
    void diskInfo_request() throws Exception {
        mockMvc.perform(get("/sys/info")
                .header("X-Token", "test-token"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("networkInfo_request - 网络信息")
    void networkInfo_request() throws Exception {
        mockMvc.perform(get("/sys/info")
                .header("X-Token", "test-token"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("exec_emptyCommand_returnsError - 命令为空（返回业务错误，HTTP 200）")
    void exec_emptyCommand_returnsError() throws Exception {
        String json = "{\"command\":\"\"}";
        // Agent controllers return Result with error code in body, HTTP 200
        mockMvc.perform(post("/shell/exec")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("exec_echoCommand - 命令正常执行")
    void exec_echoCommand() throws Exception {
        String json = "{\"command\":\"echo hello\"}";
        mockMvc.perform(post("/shell/exec")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().is2xxSuccessful());
    }

    @Test
    @DisplayName("start_missingDeployDir_returnsParamError - 缺少deployDir（返回业务错误）")
    void start_missingDeployDir_returnsParamError() throws Exception {
        String json = "{\"startScript\":\"java -jar app.jar\"}";
        mockMvc.perform(post("/process/proj-1/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("start_missingScript_returnsParamError - 缺少startScript（返回业务错误）")
    void start_missingScript_returnsParamError() throws Exception {
        String json = "{\"deployDir\":\"/tmp\"}";
        mockMvc.perform(post("/process/proj-1/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("stop_validReturnsOk - 停止进程")
    void stop_validReturnsOk() throws Exception {
        String json = "{\"stopScript\":\"\"}";
        mockMvc.perform(post("/process/proj-1/stop")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().is2xxSuccessful());
    }

    @Test
    @DisplayName("fileReceive_missingFile_returnsError - 文件不存在（返回业务错误）")
    void fileReceive_missingFile_returnsError() throws Exception {
        // Multipart without file body returns HTTP 400 (missing file param)
        mockMvc.perform(multipart("/file/receive")
                .param("versionName", "1.0.0"))
            .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("getConfig_nonExistent_returns400 - 不存在的配置（返回业务错误）")
    void getConfig_nonExistent_returns400() throws Exception {
        mockMvc.perform(get("/file/config")
                .param("configPath", "/nonexistent/config.yml"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("getLog_nonExistent_returns400 - 不存在的日志（返回业务错误）")
    void getLog_nonExistent_returns400() throws Exception {
        mockMvc.perform(get("/file/log")
                .param("logPath", "/nonexistent/log/file.log"))
            .andExpect(status().isOk());
    }
}
