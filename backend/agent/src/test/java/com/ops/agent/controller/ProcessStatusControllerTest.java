package com.ops.agent.controller;

import com.ops.agent.process.ProcessStatusController;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProcessStatusController.class)
class ProcessStatusControllerTest {

    private static HttpServer server;
    private static int port;

    @Autowired
    private MockMvc mockMvc;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
        server.createContext("/health", exchange -> {
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            OutputStream os = exchange.getResponseBody();
            os.write(body);
            os.close();
        });
        server.start();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("status 缺少 jarName 返回 400")
    void status_missingJarName() throws Exception {
        mockMvc.perform(get("/process/status")
                .param("deployDir", "/tmp"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("status 正常请求返回 alive 字段")
    void status_validRequest() throws Exception {
        mockMvc.perform(get("/process/status")
                .param("deployDir", "/nonexistent-" + System.nanoTime())
                .param("jarName", "missing.jar"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.alive").value(false))
            .andExpect(jsonPath("$.data.checkMethod").value("PS_GREP"));
    }

    @Test
    @DisplayName("probe 健康检查返回 UP")
    void probe_healthyEndpoint() throws Exception {
        String url = "http://127.0.0.1:" + port + "/health";
        mockMvc.perform(get("/process/probe")
                .param("url", url)
                .param("expectedStatus", "200"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("UP"))
            .andExpect(jsonPath("$.data.httpCode").value(200));
    }

    @Test
    @DisplayName("metrics 返回 found 字段")
    void metrics_returnsFoundField() throws Exception {
        mockMvc.perform(get("/process/metrics")
                .param("deployDir", "/tmp")
                .param("jarName", "app.jar"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.found").exists());
    }

    @Test
    @DisplayName("jvm 非法 pid 返回参数错误")
    void jvm_invalidPid() throws Exception {
        mockMvc.perform(get("/process/jvm")
                .param("pid", "0"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(400));
    }
}
