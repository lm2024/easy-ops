package com.ops.agent.traffic;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class NginxLogParserTest {

    private final NginxLogParser parser = new NginxLogParser();

    @Test
    public void parse_mainFormat() {
        String line = "\"2026-07-31T19:05:23+08:00\" \"192.168.1.10\" \"-\" \"-\" "
                + "\"GET /api/nodes?page=1 HTTP/1.1\" \"200\" \"4521\" \"-\" \"Mozilla/5.0\" "
                + "\"10.0.0.5:8080\" \"0.125\" \"0.118\" \"localhost\"";
        NginxLogParser.ParsedLine parsed = parser.parse(line);
        assertNotNull(parsed);
        assertEquals("192.168.1.10", parsed.clientIp);
        assertEquals("GET", parsed.method);
        assertEquals("/api/nodes", parsed.uri);
        assertEquals(200, parsed.status);
        assertEquals(125L, parsed.requestTimeMs);
    }

    @Test
    public void parse_xForwardedFor() {
        String line = "\"2026-07-31T19:05:23+08:00\" \"10.0.0.1\" \"192.168.1.99, 10.0.0.1\" \"-\" "
                + "\"POST /api/auth/login HTTP/1.1\" \"200\" \"100\" \"-\" \"-\" \"-\" \"0.050\" \"0.040\" \"-\"";
        NginxLogParser.ParsedLine parsed = parser.parse(line);
        assertNotNull(parsed);
        assertEquals("192.168.1.99", parsed.clientIp);
        assertEquals("/api/auth/login", parsed.uri);
    }
}
