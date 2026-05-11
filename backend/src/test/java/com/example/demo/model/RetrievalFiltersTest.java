package com.example.demo.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RetrievalFiltersTest {

    @Test
    void mergeMissingDoesNotCombineManualProjectKeysWithFreeTextProjectHint() {
        RetrievalFilters manual = new RetrievalFilters(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            List.of(),
            null,
            List.of(),
            List.of(),
            List.of("manual-project"),
            List.of(),
            null,
            null,
            null,
            null
        );
        RetrievalFilters hint = new RetrievalFilters(
            null,
            null,
            null,
            null,
            "North Upgrade",
            null,
            null,
            null,
            List.of(),
            null,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            null,
            null,
            null
        );

        RetrievalFilters merged = manual.mergeMissing(hint);

        assertEquals(List.of("manual-project"), merged.projectKeys());
        assertNull(merged.project());
    }

    @Test
    void mergeMissingDoesNotCombineManualLanguageCodesWithLegacyLanguageHint() {
        RetrievalFilters manual = new RetrievalFilters(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            List.of(),
            null,
            List.of(),
            List.of(),
            List.of(),
            List.of(MaterialLanguageCode.EN),
            null,
            null,
            null,
            null
        );
        RetrievalFilters hint = new RetrievalFilters(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "ru",
            List.of(),
            null
        );

        RetrievalFilters merged = manual.mergeMissing(hint);

        assertEquals(List.of(MaterialLanguageCode.EN), merged.languageCodes());
        assertNull(merged.language());
    }

    @Test
    void freeTextProjectMatchesProjectNameOrProjectKey() {
        RetrievalFilters projectNameFilter = new RetrievalFilters(
            null,
            null,
            null,
            null,
            "North Upgrade",
            null,
            null,
            null,
            List.of(),
            null
        );
        RetrievalFilters projectKeyFilter = new RetrievalFilters(
            null,
            null,
            null,
            null,
            "north-upgrade",
            null,
            null,
            null,
            List.of(),
            null
        );
        MaterialMetadataSnapshot metadataWithProjectName = metadata("North Upgrade", null);
        MaterialMetadataSnapshot metadataWithProjectKey = metadata(null, "north-upgrade");

        assertTrue(projectNameFilter.matches(metadataWithProjectName));
        assertTrue(projectKeyFilter.matches(metadataWithProjectKey));
    }

    @Test
    void businessStatusDoesNotBecomeCanonicalDocumentStatusFilter() {
        RetrievalFilters filters = new RetrievalFilters(
            null,
            null,
            null,
            null,
            null,
            null,
            "DRAFT",
            null,
            List.of(),
            null
        );

        assertEquals(List.of(), filters.documentStatuses());
        assertTrue(filters.matches(metadata(null, null, "DRAFT", DocumentStatus.ACTIVE)));
    }

    @Test
    void queryHintVersionLabelBecomesVersionLabelFilter() {
        RetrievalQueryHints hints = new RetrievalQueryHints(
            null,
            null,
            null,
            "v2",
            null,
            null,
            null,
            null,
            null
        );

        RetrievalFilters filters = hints.toRetrievalFilters();

        assertEquals("v2", filters.versionLabel());
        assertEquals(VersionSelectionMode.VERSION_LABEL, filters.versionSelectionMode());
        assertTrue(filters.matches(metadata(null, null, null, DocumentStatus.ACTIVE, "v2")));
    }

    @Test
    void isEmptyAndMergeMissingAccountForVersionAndReferenceTimeFilters() {
        RetrievalFilters manual = new RetrievalFilters(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            List.of(),
            null,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            null,
            null,
            null,
            null,
            LocalDate.parse("2026-04-19"),
            VersionSelectionMode.INCLUDE_HISTORY,
            null,
            Instant.parse("2026-04-19T00:00:00Z"),
            Instant.parse("2026-04-20T00:00:00Z")
        );
        RetrievalFilters fallback = new RetrievalFilters(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            List.of("ops"),
            null
        );

        RetrievalFilters merged = manual.mergeMissing(fallback);

        assertTrue(!manual.isEmpty());
        assertEquals(VersionSelectionMode.INCLUDE_HISTORY, merged.versionSelectionMode());
        assertEquals(LocalDate.parse("2026-04-19"), merged.effectiveDate());
        assertEquals(Instant.parse("2026-04-19T00:00:00Z"), merged.uploadedAfterInclusive());
        assertEquals(List.of("ops"), merged.tags());
    }

    private MaterialMetadataSnapshot metadata(String project, String projectKey) {
        return metadata(project, projectKey, null, DocumentStatus.ACTIVE);
    }

    private MaterialMetadataSnapshot metadata(
        String project,
        String projectKey,
        String businessStatus,
        DocumentStatus documentStatus
    ) {
        return new MaterialMetadataSnapshot(
            DocumentType.OTHER,
            KnowledgeDocumentClass.OTHER,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            SourceTrustLevel.UNKNOWN,
            project,
            projectKey,
            null,
            null,
            businessStatus,
            documentStatus,
            null,
            null,
            MaterialMetadataProvenance.empty()
        );
    }

    private MaterialMetadataSnapshot metadata(
        String project,
        String projectKey,
        String businessStatus,
        DocumentStatus documentStatus,
        String versionLabel
    ) {
        return new MaterialMetadataSnapshot(
            DocumentType.OTHER,
            KnowledgeDocumentClass.OTHER,
            null,
            null,
            null,
            null,
            versionLabel,
            null,
            null,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            SourceTrustLevel.UNKNOWN,
            project,
            projectKey,
            null,
            null,
            businessStatus,
            documentStatus,
            null,
            null,
            MaterialMetadataProvenance.empty()
        );
    }
}
