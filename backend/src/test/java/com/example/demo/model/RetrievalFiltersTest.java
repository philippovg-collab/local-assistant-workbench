package com.example.demo.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
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
}
