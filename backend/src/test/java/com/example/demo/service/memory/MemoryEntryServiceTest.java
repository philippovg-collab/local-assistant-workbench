package com.example.demo.service.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.demo.config.ContextProperties;
import com.example.demo.error.ApplicationException;
import com.example.demo.model.MemoryEntryRequest;
import com.example.demo.model.MemoryEntryResponse;
import com.example.demo.model.MemoryEntryStatus;
import com.example.demo.model.MemoryEntryType;
import com.example.demo.service.memory.port.MemoryRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class MemoryEntryServiceTest {

    @Test
    void disabledLongTermMemoryBlocksReviewEndpointsBeforeRepositoryAccess() {
        MemoryRepository repository = Mockito.mock(MemoryRepository.class);
        MemoryEntryService service = new MemoryEntryService(
            new ContextProperties(),
            repository,
            new MemorySafetyPolicy()
        );

        ApplicationException exception = assertThrows(
            ApplicationException.class,
            () -> service.list(null, null, null, null)
        );

        assertEquals("memory.disabled", exception.getCode());
        verifyNoInteractions(repository);
    }

    @Test
    void manualCreateStartsPendingAndUnpinnedEvenWhenRequestAsksForPinned() {
        MemoryRepository repository = Mockito.mock(MemoryRepository.class);
        when(repository.createEntry(any(), any(), any(), any())).thenAnswer(invocation -> {
            MemoryEntryDraft draft = invocation.getArgument(0);
            Instant now = invocation.getArgument(3);
            return new MemoryEntryResponse(
                "memory-1",
                MemoryEntryStatus.PENDING_REVIEW,
                draft.entryType(),
                draft.contentText(),
                draft.normalizedKey(),
                draft.workspaceKey(),
                draft.projectKey(),
                draft.pinned(),
                draft.confidence(),
                draft.provenance(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                now,
                now
            );
        });
        MemoryEntryService service = new MemoryEntryService(enabledProperties(), repository, new MemorySafetyPolicy());

        MemoryEntryResponse created = service.create(new MemoryEntryRequest(
            MemoryEntryType.USER_PREFERENCE,
            "Пользователь предпочитает короткие ответы.",
            null,
            "workspace-a",
            null,
            true,
            BigDecimal.ONE,
            Map.of("source", "manual-test"),
            "manual"
        ));

        ArgumentCaptor<MemoryEntryDraft> draftCaptor = ArgumentCaptor.forClass(MemoryEntryDraft.class);
        Mockito.verify(repository).createEntry(draftCaptor.capture(), any(), any(), any());
        assertFalse(draftCaptor.getValue().pinned());
        assertFalse(Boolean.TRUE.equals(created.pinned()));
        assertEquals(MemoryEntryStatus.PENDING_REVIEW, created.status());
    }

    @Test
    void manualCreateRejectsKbFactsBeforeRepositoryAccess() {
        MemoryRepository repository = Mockito.mock(MemoryRepository.class);
        MemoryEntryService service = new MemoryEntryService(enabledProperties(), repository, new MemorySafetyPolicy());

        ApplicationException exception = assertThrows(
            ApplicationException.class,
            () -> service.create(new MemoryEntryRequest(
                MemoryEntryType.PROJECT_NOTE,
                "По документу тариф равен 100",
                null,
                "workspace-a",
                "project-a",
                false,
                null,
                Map.of(),
                null
            ))
        );

        assertEquals("memory.content_forbidden", exception.getCode());
        verifyNoInteractions(repository);
    }

    private static ContextProperties enabledProperties() {
        ContextProperties properties = new ContextProperties();
        properties.setEnabled(true);
        properties.setConversationsEnabled(true);
        properties.setHistoryEnabled(true);
        properties.setLongTermMemoryEnabled(true);
        return properties;
    }
}
