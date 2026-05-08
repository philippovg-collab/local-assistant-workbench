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
    void doesNotAliasValidLegacyBusinessStatusToDocumentStatus() {
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
        assertEquals(DocumentStatus.ACTIVE, metadata.documentStatus());
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

    @Test
    void createsPreservationInputForCanonicalAndCompatibilityFields() {
        MaterialMetadataSnapshot metadata = MaterialMetadataSnapshot.fromInput(new MaterialMetadataInput(
            DocumentType.CONTRACT,
            "general",
            DocumentStatus.DRAFT,
            "line-a",
            "KZ-2026-0415-ENERGY",
            MaterialLanguageCode.RU,
            List.of("manual-grid"),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-06-30"),
            null,
            LocalDate.parse("2026-04-17"),
            "Legal lead",
            "Legal",
            "v2",
            "ru",
            List.of("manual-grid"),
            SourceTrustLevel.HIGH,
            "Line A Display",
            "KazEnergy Service",
            "APPROVED"
        ));

        MaterialMetadataInput input = metadata.toEditableInputPreservingStoredFields();

        assertEquals(DocumentType.CONTRACT, input.documentType());
        assertEquals("general", input.workspaceKey());
        assertEquals(DocumentStatus.DRAFT, input.documentStatus());
        assertEquals("line-a", input.projectKey());
        assertEquals("KZ-2026-0415-ENERGY", input.documentNumber());
        assertEquals(MaterialLanguageCode.RU, input.languageCode());
        assertEquals(List.of("manual-grid"), input.manualTags());
        assertEquals(LocalDate.parse("2026-04-01"), input.periodStart());
        assertEquals(LocalDate.parse("2026-06-30"), input.periodEnd());
        assertEquals(LocalDate.parse("2026-04-17"), input.documentDate());
        assertEquals("Legal lead", input.author());
        assertEquals("Legal", input.department());
        assertEquals("v2", input.versionLabel());
        assertEquals(SourceTrustLevel.HIGH, input.sourceTrust());
        assertEquals("Line A Display", input.project());
        assertEquals("KazEnergy Service", input.counterparty());
        assertEquals("APPROVED", input.businessStatus());
    }

    @Test
    void preservationInputKeepsManualTagsSeparateFromAutoTagsAndDoesNotAliasLegacyFields() {
        MaterialMetadataSnapshot metadata = MaterialMetadataSnapshot.fromInput(new MaterialMetadataInput(
            DocumentType.POLICY,
            "general",
            null,
            "line-a",
            null,
            null,
            List.of("manual-grid"),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            List.of("manual-grid"),
            null,
            "Line A Display",
            null,
            "DRAFT"
        )).withTagLayers(List.of("manual-grid"), List.of("auto-grid"));

        MaterialMetadataInput input = metadata.toEditableInputPreservingStoredFields();

        assertEquals(List.of("manual-grid"), input.manualTags());
        assertEquals(List.of("manual-grid"), input.tags());
        assertEquals("DRAFT", input.businessStatus());
        assertEquals(DocumentStatus.ACTIVE, input.effectiveDocumentStatus());
        assertEquals("Line A Display", input.project());
        assertEquals("line-a", input.projectKey());
    }
}
