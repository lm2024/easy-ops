package com.ops.agent.process;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HttpHealthProberTest {

    private static HttpServer server;
    private static int port;
    private final HttpHealthProber prober = new HttpHealthProber();

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
        server.createContext("/hello", exchange -> {
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            OutputStream os = exchange.getResponseBody();
            os.write(body);
            os.close();
        });
        server.createContext("/fail", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
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
    @DisplayName("probe GET 成功返回 UP")
    void probe_getSuccess_returnsUp() {
        String url = "http://127.0.0.1:" + port + "/hello";
        Map<String, Object> result = prober.probe("GET", url, 200, 3000, null, null);

        assertEquals("UP", result.get("status"));
        assertEquals(200, result.get("httpCode"));
        assertTrue(result.get("bodySnippet").toString().contains("ok"));
    }

    @Test
    @DisplayName("probe 状态码不匹配返回 DOWN")
    void probe_wrongStatus_returnsDown() {
        String url = "http://127.0.0.1:" + port + "/fail";
        Map<String, Object> result = prober.probe("GET", url, 200, 3000, null, null);

        assertEquals("DOWN", result.get("status"));
        assertEquals(500, result.get("httpCode"));
    }

    @Test
    @DisplayName("probe HEAD 请求可执行")
    void probe_headMethod_works() {
        String url = "http://127.0.0.1:" + port + "/hello";
        Map<String, Object> result = prober.probe("HEAD", url, 200, 3000, null, null);

        assertEquals("UP", result.get("status"));
        assertEquals(200, result.get("httpCode"));
    }

    @Test
    @DisplayName("probe 空 URL 返回 DOWN")
    void probe_emptyUrl_returnsDown() {
        Map<String, Object> result = prober.probe("GET", "", 200, 3000, null, null);

        assertEquals("DOWN", result.get("status"));
        assertNotNull(result.get("detail"));
    }
}
