package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.demo.api.ApiException;
import com.example.demo.infrastructure.knowledge.PostgresKnowledgePresetRepository;
import com.example.demo.infrastructure.knowledge.StoredKnowledgePresetRecord;
import com.example.demo.infrastructure.knowledge.StoredKnowledgePresetRevisionRecord;
import com.example.demo.model.CreateKnowledgePresetRequest;
import com.example.demo.model.KnowledgePresetRevisionDiff;
import com.example.demo.model.KnowledgeScope;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KnowledgePresetServiceTest {

    @Test
    void rejectsInactivePresetsDuringScopeResolution() {
        PostgresKnowledgePresetRepository repository = mock(PostgresKnowledgePresetRepository.class);
        KnowledgePresetService service = new KnowledgePresetService(repository);
        String presetId = UUID.randomUUID().toString();
        when(repository.findById(presetId)).thenReturn(Optional.of(new StoredKnowledgePresetRecord(
            presetId,
            "Dormant preset",
            "inactive",
            new KnowledgeScope(List.of(), List.of(), List.of(), "legal-workspace", false),
            3,
            false,
            Instant.parse("2026-04-18T10:00:00Z"),
            Instant.parse("2026-04-18T10:00:00Z")
        )));

        ApiException exception = assertThrows(ApiException.class, () -> service.resolveScope(
            new KnowledgeScope(List.of(presetId), List.of(), List.of(), null, false)
        ));

        assertEquals("knowledge_preset.inactive", exception.getCode());
    }

    @Test
    void rejectsTooManyPresetTags() {
        PostgresKnowledgePresetRepository repository = mock(PostgresKnowledgePresetRepository.class);
        KnowledgePresetService service = new KnowledgePresetService(repository);
        List<String> tags = java.util.stream.IntStream.range(0, 33)
            .mapToObj(index -> "tag-" + index)
            .toList();

        ApiException exception = assertThrows(ApiException.class, () -> service.createPreset(
            new CreateKnowledgePresetRequest(
                "Large scope",
                "Too many tags",
                new KnowledgeScope(List.of(), List.of(), tags, null, false),
                true
            )
        ));

        assertEquals("request.too_many_items", exception.getCode());
    }

    @Test
    void computesKnowledgePresetRevisionDiff() {
        PostgresKnowledgePresetRepository repository = mock(PostgresKnowledgePresetRepository.class);
        KnowledgePresetService service = new KnowledgePresetService(repository);
        String presetId = UUID.randomUUID().toString();
        Instant now = Instant.parse("2026-04-18T10:00:00Z");

        when(repository.findRevision(presetId, 1)).thenReturn(Optional.of(new StoredKnowledgePresetRevisionRecord(
            presetId,
            1,
            "Contracts",
            "Only contracts",
            new KnowledgeScope(List.of(), List.of(), List.of("finance"), "legal-workspace", false),
            true,
            null,
            now,
            now
        )));
        when(repository.findRevision(presetId, 2)).thenReturn(Optional.of(new StoredKnowledgePresetRevisionRecord(
            presetId,
            2,
            "Contracts",
            "Contracts and today uploads",
            new KnowledgeScope(List.of(), List.of(), List.of("finance", "today"), "legal-workspace", true),
            true,
            null,
            now,
            now
        )));

        KnowledgePresetRevisionDiff diff = service.diffRevisions(presetId, 1, 2);

        assertEquals(1, diff.fromRevision());
        assertEquals(2, diff.toRevision());
        assertTrue(diff.changes().stream().anyMatch(change -> "uploadedTodayOnly".equals(change.field())));
        assertTrue(diff.changes().stream().anyMatch(change -> "tags".equals(change.field())));
    }
}
