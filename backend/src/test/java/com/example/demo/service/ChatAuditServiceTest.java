package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChatAuditServiceTest {

    @Test
    void delegatesAuditReadsToQueryService() {
        ChatRunQueryService queryService = mock(ChatRunQueryService.class);
        ChatAuditService service = new ChatAuditService(queryService, mockTraceService());
        String runId = UUID.randomUUID().toString();

        service.getRun(runId);
        service.listRuns();
        service.getTrace(runId);

        verify(queryService).getRun(runId);
        verify(queryService).listRuns();
        verify(queryService).getTrace(runId);
    }

    @Test
    void currentHealthMirrorsTraceHealth() {
        ChatRunTraceService traceService = mockTraceService();
        when(traceService.currentHealth()).thenReturn(new ChatRunTraceService.TraceHealth(
            "DOWN",
            "chat_trace.storage_failed",
            "trace unavailable",
            2,
            "2026-04-19T00:00:00Z"
        ));
        ChatAuditService service = new ChatAuditService(mock(ChatRunQueryService.class), traceService);

        assertEquals("DOWN", service.currentHealth().status());
        assertEquals("chat_trace.storage_failed", service.currentHealth().reasonCode());
        assertEquals(2, service.currentHealth().consecutiveFailureCount());
    }

    private ChatRunTraceService mockTraceService() {
        ChatRunTraceService traceService = mock(ChatRunTraceService.class);
        when(traceService.currentHealth()).thenReturn(new ChatRunTraceService.TraceHealth("UP", null, null, 0, null));
        return traceService;
    }
}
