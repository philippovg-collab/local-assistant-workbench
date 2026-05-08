package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.demo.config.ChatAuditProperties;
import com.example.demo.service.audit.port.ChatAuditRepository;
import com.example.demo.service.audit.port.ChatRunTraceRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ChatAuditRetentionServiceTest {

    @Test
    void deletesOldTraceAndLegacyAuditRowsWhenRetentionIsPositive() {
        ChatAuditProperties properties = new ChatAuditProperties();
        properties.setRetentionDays(30);
        ChatRunTraceRepository traceRepository = mock(ChatRunTraceRepository.class);
        ChatAuditRepository legacyRepository = mock(ChatAuditRepository.class);
        when(traceRepository.deleteRunsOlderThan(org.mockito.Mockito.any())).thenReturn(2);
        when(legacyRepository.deleteRunsOlderThan(org.mockito.Mockito.any())).thenReturn(1);
        ChatAuditRetentionService service = new ChatAuditRetentionService(
            properties,
            traceRepository,
            legacyRepository
        );

        service.deleteExpiredRuns();

        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(traceRepository).deleteRunsOlderThan(cutoffCaptor.capture());
        verify(legacyRepository).deleteRunsOlderThan(org.mockito.Mockito.any());
        assertTrue(cutoffCaptor.getValue().isBefore(Instant.now()));
    }

    @Test
    void retentionIsDisabledWhenRetentionDaysIsZeroOrNegative() {
        ChatRunTraceRepository traceRepository = mock(ChatRunTraceRepository.class);
        ChatAuditRepository legacyRepository = mock(ChatAuditRepository.class);

        ChatAuditProperties zeroDays = new ChatAuditProperties();
        zeroDays.setRetentionDays(0);
        new ChatAuditRetentionService(zeroDays, traceRepository, legacyRepository).deleteExpiredRuns();

        ChatAuditProperties negativeDays = new ChatAuditProperties();
        negativeDays.setRetentionDays(-1);
        new ChatAuditRetentionService(negativeDays, traceRepository, legacyRepository).deleteExpiredRuns();

        verifyNoInteractions(traceRepository, legacyRepository);
    }
}
