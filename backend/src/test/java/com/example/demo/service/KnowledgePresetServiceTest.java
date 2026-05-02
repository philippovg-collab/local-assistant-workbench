package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.demo.api.ApiException;
import com.example.demo.model.CreateKnowledgePresetRequest;
import com.example.demo.model.DocumentStatus;
import com.example.demo.model.DocumentType;
import com.example.demo.model.KnowledgePresetRevisionDiff;
import com.example.demo.model.KnowledgeScope;
import com.example.demo.model.MaterialLanguageCode;
import com.example.demo.model.SavedKnowledgeFilterKind;
import com.example.demo.service.knowledge.port.KnowledgePresetRepository;
import com.example.demo.service.knowledge.port.StoredKnowledgePresetRecord;
import com.example.demo.service.knowledge.port.StoredKnowledgePresetRevisionRecord;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KnowledgePresetServiceTest {

    @Test
    void rejectsInactivePresetsDuringScopeResolution() {
        KnowledgePresetRepository repository = mock(KnowledgePresetRepository.class);
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
        KnowledgePresetRepository repository = mock(KnowledgePresetRepository.class);
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
        KnowledgePresetRepository repository = mock(KnowledgePresetRepository.class);
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

    @Test
    void resolvesPresetAndFacetIntoCanonicalScopeInsideActiveWorkspace() {
        KnowledgePresetRepository repository = mock(KnowledgePresetRepository.class);
        KnowledgePresetService service = new KnowledgePresetService(repository);
        String presetId = UUID.randomUUID().toString();
        String facetId = UUID.randomUUID().toString();
        Instant now = Instant.parse("2026-04-18T10:00:00Z");

        when(repository.findById(presetId)).thenReturn(Optional.of(new StoredKnowledgePresetRecord(
            presetId,
            SavedKnowledgeFilterKind.PRESET,
            "Contracts",
            "Only contracts",
            new KnowledgeScope(
                List.of(),
                List.of(),
                List.of(),
                List.of(DocumentType.CONTRACT),
                List.of(),
                List.of(),
                null,
                List.of(),
                List.of("finance"),
                "legal-workspace",
                null,
                null,
                null,
                null,
                false
            ),
            2,
            true,
            now,
            now
        )));
        when(repository.findById(facetId)).thenReturn(Optional.of(new StoredKnowledgePresetRecord(
            facetId,
            SavedKnowledgeFilterKind.FACET,
            "Draft RU North",
            "Draft RU docs",
            new KnowledgeScope(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(DocumentStatus.DRAFT),
                List.of("north"),
                "DOC-17",
                List.of(MaterialLanguageCode.RU),
                List.of("urgent"),
                "legacy-shared-workspace",
                LocalDate.parse("2026-01-01"),
                LocalDate.parse("2026-12-31"),
                null,
                null,
                false
            ),
            4,
            true,
            now,
            now
        )));

        KnowledgePresetService.ResolvedKnowledgeScopeContext resolved = service.resolveScope(new KnowledgeScope(
            List.of(presetId),
            List.of(facetId),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            List.of(),
            List.of(),
            "active-rag-project",
            null,
            null,
            null,
            null,
            false
        ));

        assertEquals("active-rag-project", resolved.effectiveScope().workspaceKey());
        assertEquals(List.of(DocumentType.CONTRACT), resolved.effectiveScope().documentTypes());
        assertEquals(List.of(DocumentStatus.DRAFT), resolved.effectiveScope().documentStatuses());
        assertEquals(List.of("north"), resolved.effectiveScope().projectKeys());
        assertEquals("DOC-17", resolved.effectiveScope().documentNumber());
        assertEquals(List.of(MaterialLanguageCode.RU), resolved.effectiveScope().languageCodes());
        assertEquals(List.of("finance", "urgent"), resolved.effectiveScope().tags());
        assertEquals(1, resolved.resolvedScope().presets().size());
        assertEquals(1, resolved.resolvedScope().facets().size());
    }

    @Test
    void rejectsFacetIdPassedAsPresetId() {
        KnowledgePresetRepository repository = mock(KnowledgePresetRepository.class);
        KnowledgePresetService service = new KnowledgePresetService(repository);
        String facetId = UUID.randomUUID().toString();
        Instant now = Instant.parse("2026-04-18T10:00:00Z");
        when(repository.findById(facetId)).thenReturn(Optional.of(new StoredKnowledgePresetRecord(
            facetId,
            SavedKnowledgeFilterKind.FACET,
            "Facet",
            null,
            KnowledgeScope.empty(),
            1,
            true,
            now,
            now
        )));

        ApiException exception = assertThrows(ApiException.class, () -> service.resolveScope(
            new KnowledgeScope(List.of(facetId), List.of(), List.of(), null, false)
        ));

        assertEquals("knowledge_preset.kind_mismatch", exception.getCode());
    }
}
