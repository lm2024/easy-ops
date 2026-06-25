package com.ops.server.monitorapp.service;

import com.ops.server.mapper.MonitorSnapshotMapper;
import com.ops.server.client.AgentClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContextBudgetServiceTest {

    @Mock
    private MonitorSnapshotMapper snapshotMapper;
    @Mock
    private AgentClient agentClient;
    @InjectMocks
    private ContextBudgetService contextBudgetService;

    @Test
    void estimateTokens() {
        assertEquals(5, contextBudgetService.estimateTokens("1234567890"));
        assertEquals(0, contextBudgetService.estimateTokens(""));
    }

    @Test
    void buildContext_emptySnapshots() {
        when(snapshotMapper.findRecent(1L, 2L, 5)).thenReturn(Collections.emptyList());

        Map<String, Object> meta = contextBudgetService.buildContext(1L, 2L, null);
        assertNotNull(meta.get("context"));
        assertEquals(0, meta.get("monitorSnapshots"));
        assertTrue((Integer) meta.get("tokenEstimated") >= 0);
    }

    @Test
    void buildContext_withErrorLog() {
        when(snapshotMapper.findRecent(1L, 2L, 5)).thenReturn(Collections.emptyList());
        when(agentClient.readLog(eq(2L), eq("/logs/app.log"), anyInt()))
                .thenReturn("INFO ok\nERROR something failed\n");

        Map<String, Object> meta = contextBudgetService.buildContext(1L, 2L, "/logs/app.log");
        assertTrue(meta.get("context").toString().contains("ERROR"));
        assertTrue((Integer) meta.get("errorLogLines") > 0);
    }
}
