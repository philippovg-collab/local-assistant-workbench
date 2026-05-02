package com.example.demo.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class MaterialMetadataSnapshotTest {

    @Test
    void keepsLegacyProjectSeparateFromCanonicalProjectKey() {
        MaterialMetadataSnapshot metadata = MaterialMetadataSnapshot.fromInput(new MaterialMetadataInput(
            DocumentType.REPORT,
            "north-workspace",
            DocumentStatus.ACTIVE,
            "north-upgrade",
            "REP-42",
            MaterialLanguageCode.RU,
            List.of("grid"),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-06-30"),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "North Upgrade",
            null,
            null
        ));

        assertEquals("North Upgrade", metadata.project());
        assertEquals("north-upgrade", metadata.projectKey());
    }

    @Test
    void keepsLegacyBusinessStatusSeparateFromCanonicalDocumentStatus() {
        MaterialMetadataSnapshot metadata = MaterialMetadataSnapshot.fromInput(new MaterialMetadataInput(
            DocumentType.POLICY,
            "general",
            DocumentStatus.ACTIVE,
            "line-a",
            null,
            null,
            List.of(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "APPROVED"
        ));

        assertEquals("APPROVED", metadata.businessStatus());
        assertEquals(DocumentStatus.ACTIVE, metadata.documentStatus());
    }

    @Test
    void mapsValidLegacyBusinessStatusToDocumentStatusWithoutOverwritingLegacyValue() {
        MaterialMetadataSnapshot metadata = MaterialMetadataSnapshot.fromInput(new MaterialMetadataInput(
            DocumentType.POLICY,
            "general",
            null,
            "line-a",
            null,
            null,
            List.of(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "DRAFT"
        ));

        assertEquals("DRAFT", metadata.businessStatus());
        assertEquals(DocumentStatus.DRAFT, metadata.documentStatus());
    }

    @Test
    void defaultsInvalidLegacyBusinessStatusToActiveDocumentStatusWithoutOverwritingLegacyValue() {
        MaterialMetadataSnapshot metadata = MaterialMetadataSnapshot.fromInput(new MaterialMetadataInput(
            DocumentType.POLICY,
            "general",
            null,
            "line-a",
            null,
            null,
            List.of(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "APPROVED"
        ));

        assertEquals("APPROVED", metadata.businessStatus());
        assertEquals(DocumentStatus.ACTIVE, metadata.documentStatus());
    }
}
