package com.ops.agent.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LogFileServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void tail_returnsLastLinesWithLevelFilter() throws Exception {
        Path log = tempDir.resolve("app.log");
        Files.write(log, (
                "2026-07-10 10:00:00.000  INFO 1 --- [main] a : one\n"
                        + "2026-07-10 10:00:01.000 ERROR 1 --- [main] a : two\n"
                        + "2026-07-10 10:00:02.000  INFO 1 --- [main] a : three\n"
        ).getBytes(StandardCharsets.UTF_8));

        LogFileService service = new LogFileService();
        Map<String, Object> all = service.tail(log.toString(), 10, null);
        @SuppressWarnings("unchecked")
        List<String> lines = (List<String>) all.get("lines");
        assertEquals(3, lines.size());

        Map<String, Object> errors = service.tail(log.toString(), 10, "ERROR");
        @SuppressWarnings("unchecked")
        List<String> errorLines = (List<String>) errors.get("lines");
        assertEquals(1, errorLines.size());
        assertTrue(errorLines.get(0).contains("ERROR"));
    }

    @Test
    void readPage_skipsWithoutLoadingEntireFileIntoMemoryField() throws Exception {
        Path log = tempDir.resolve("page.log");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            sb.append("2026-07-10 10:00:00.000  INFO 1 --- [main] a : line-").append(i).append('\n');
        }
        Files.write(log, sb.toString().getBytes(StandardCharsets.UTF_8));

        LogFileService service = new LogFileService();
        Map<String, Object> page = service.readPage(log.toString(), 2, 3, "INFO");
        @SuppressWarnings("unchecked")
        List<String> lines = (List<String>) page.get("lines");
        assertEquals(3, lines.size());
        assertTrue(lines.get(0).contains("line-2"));
        assertTrue((Boolean) page.get("hasMore"));
    }

    @Test
    void listLogs_filtersByPattern() throws Exception {
        Files.write(tempDir.resolve("app.log"), "x".getBytes(StandardCharsets.UTF_8));
        Files.write(tempDir.resolve("readme.txt"), "x".getBytes(StandardCharsets.UTF_8));

        LogFileService service = new LogFileService();
        List<Map<String, Object>> files = service.listLogs(tempDir.toString(), "*.log");
        assertEquals(1, files.size());
        assertEquals("app.log", files.get(0).get("name"));
    }
}
