package com.example.demo.service;

import com.example.demo.service.material.MaterialMetadataHints;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.demo.api.ApiException;
import com.example.demo.service.reference.port.ReferenceDataRepository;
import com.example.demo.service.reference.StoredReferenceProjectRecord;
import com.example.demo.model.DocumentStatus;
import com.example.demo.model.DocumentType;
import com.example.demo.model.KnowledgeDocumentClass;
import com.example.demo.model.MaterialLanguageCode;
import com.example.demo.model.MaterialMetadataInput;
import com.example.demo.model.MaterialMetadataSnapshot;
import com.example.demo.model.MetadataValueOrigin;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MaterialMetadataResolverTest {

    private final MaterialMetadataResolver resolver = new MaterialMetadataResolver(new com.example.demo.support.NoopReferenceDataRepository());

    @Test
    void resolvesDefaultsWhenNoManualOrInferredMetadataExist() {
        MaterialMetadataSnapshot metadata = resolver.resolve(
            null,
            "Plain note",
            "text",
            null,
            "text/plain",
            "Короткий рабочий текст."
        );

        assertEquals(DocumentType.OTHER, metadata.documentType());
        assertEquals(KnowledgeDocumentClass.OTHER, metadata.knowledgeDocumentClass());
        assertEquals("general", metadata.workspaceKey());
        assertEquals(DocumentStatus.ACTIVE, metadata.documentStatus());
        assertEquals("ru", metadata.language());
        assertEquals(MaterialLanguageCode.RU, metadata.languageCode());
        assertEquals(MetadataValueOrigin.DEFAULT, metadata.provenance().fieldOrigins().get("documentType"));
        assertEquals(
            MetadataValueOrigin.DEFAULT,
            metadata.provenance().fieldOrigins().get("knowledgeDocumentClass")
        );
        assertEquals(MetadataValueOrigin.DEFAULT, metadata.provenance().fieldOrigins().get("sourceTrust"));
        assertEquals(MetadataValueOrigin.INFERRED, metadata.provenance().fieldOrigins().get("language"));
        assertTrue(metadata.provenance().fieldConfidence().containsKey("language"));
    }

    @Test
    void infersMetadataFromTitleFilenameAndHeader() {
        MaterialMetadataSnapshot metadata = resolver.resolve(
            null,
            "Contract KZ-2026-0415-ENERGY 2026-04-15 v2 ru",
            "file",
            "contract-KZ-2026-0415-ENERGY-2026-04-15-v2-ru.txt",
            "text/plain",
            """
                Автор: Dana Sarsen
                Подразделение: Grid operations
                Проект: North Upgrade
                Контрагент: GridBuild LLP
                Статус: APPROVED
                Период: 01.04.2026 - 30.06.2026
                """
        );

        assertEquals(DocumentType.CONTRACT, metadata.documentType());
        assertEquals(KnowledgeDocumentClass.CONTRACTS, metadata.knowledgeDocumentClass());
        assertEquals(LocalDate.parse("2026-04-15"), metadata.documentDate());
        assertEquals("KZ-2026-0415-ENERGY", metadata.documentNumber());
        assertEquals("Dana Sarsen", metadata.author());
        assertEquals("Grid operations", metadata.department());
        assertEquals("v2", metadata.versionLabel());
        assertEquals("ru", metadata.language());
        assertEquals("North Upgrade", metadata.project());
        assertEquals("general", metadata.workspaceKey());
        assertEquals("GridBuild LLP", metadata.counterparty());
        assertEquals("APPROVED", metadata.businessStatus());
        assertEquals(DocumentStatus.ACTIVE, metadata.documentStatus());
        assertEquals(LocalDate.parse("2026-04-01"), metadata.periodStart());
        assertEquals(LocalDate.parse("2026-06-30"), metadata.periodEnd());
        assertTrue(metadata.tags().contains("energy"));
        assertEquals(MetadataValueOrigin.INFERRED, metadata.provenance().fieldOrigins().get("documentType"));
        assertEquals(
            MetadataValueOrigin.INFERRED,
            metadata.provenance().fieldOrigins().get("knowledgeDocumentClass")
        );
        assertEquals(MetadataValueOrigin.DEFAULT, metadata.provenance().fieldOrigins().get("workspaceKey"));
        assertEquals(MetadataValueOrigin.INFERRED, metadata.provenance().fieldOrigins().get("tags"));
        assertEquals(MetadataValueOrigin.INFERRED, metadata.provenance().fieldOrigins().get("author"));
        assertTrue(metadata.provenance().fieldConfidence().containsKey("documentType"));
        assertTrue(metadata.provenance().fieldConfidence().containsKey("author"));
        assertTrue(metadata.provenance().fieldConfidence().containsKey("tags"));
    }

    @Test
    void keepsManualValuesOverInferredHints() {
        MaterialMetadataSnapshot metadata = resolver.resolve(
            new MaterialMetadataInput(
                DocumentType.POLICY,
                null,
                null,
                "Manual owner",
                null,
                null,
                null,
                List.of("grid", "policy"),
                null,
                null,
                null,
                null,
                null,
                null
            ),
            "Contract KZ-2026-0415-ENERGY 2026-04-15 v2 ru",
            "file",
            "contract-KZ-2026-0415-ENERGY-2026-04-15-v2-ru.txt",
            "text/plain",
            """
                Автор: Dana Sarsen
                Подразделение: Grid operations
                Версия: 2
                """
        );

        assertEquals(DocumentType.POLICY, metadata.documentType());
        assertEquals(KnowledgeDocumentClass.REGULATIONS, metadata.knowledgeDocumentClass());
        assertEquals("Manual owner", metadata.author());
        assertEquals(List.of("grid", "policy"), metadata.manualTags());
        assertEquals(List.of("energy"), metadata.autoTags());
        assertEquals(List.of("grid", "policy", "energy"), metadata.effectiveTags());
        assertEquals(List.of("grid", "policy", "energy"), metadata.tags());
        assertEquals("Grid operations", metadata.department());
        assertEquals("v2", metadata.versionLabel());
        assertEquals(MetadataValueOrigin.MANUAL, metadata.provenance().fieldOrigins().get("documentType"));
        assertEquals(MetadataValueOrigin.MANUAL, metadata.provenance().fieldOrigins().get("author"));
        assertEquals(
            MetadataValueOrigin.INFERRED,
            metadata.provenance().fieldOrigins().get("knowledgeDocumentClass")
        );
        assertEquals(MetadataValueOrigin.INFERRED, metadata.provenance().fieldOrigins().get("department"));
        assertEquals(MetadataValueOrigin.DEFAULT, metadata.provenance().fieldOrigins().get("sourceTrust"));
        assertFalse(metadata.provenance().fieldConfidence().containsKey("documentType"));
        assertFalse(metadata.provenance().fieldConfidence().containsKey("author"));
        assertTrue(metadata.provenance().fieldConfidence().containsKey("department"));
    }

    @Test
    void combinesManualAndInferredTagsWithoutDuplicates() {
        MaterialMetadataSnapshot metadata = resolver.resolve(
            new MaterialMetadataInput(
                DocumentType.REPORT,
                "general",
                DocumentStatus.ACTIVE,
                null,
                null,
                null,
                List.of("grid", "manual"),
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
                null
            ),
            "Report",
            "text",
            null,
            "text/plain",
            "Plain text",
            new MaterialMetadataHints(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of("grid", "auto"),
                null,
                null,
                null,
                null,
                null,
                Map.of("tags", 0.8d)
            )
        );

        assertEquals(List.of("grid", "manual"), metadata.manualTags());
        assertEquals(List.of("auto"), metadata.autoTags());
        assertEquals(List.of("grid", "manual", "auto"), metadata.effectiveTags());
        assertEquals(List.of("grid", "manual", "auto"), metadata.tags());
        assertEquals(MetadataValueOrigin.MANUAL, metadata.provenance().fieldOrigins().get("manualTags"));
        assertEquals(MetadataValueOrigin.INFERRED, metadata.provenance().fieldOrigins().get("autoTags"));
        assertEquals(MetadataValueOrigin.MANUAL, metadata.provenance().fieldOrigins().get("tags"));
        assertEquals(0.8d, metadata.provenance().fieldConfidence().get("autoTags"));
    }

    @Test
    void normalizesBlankManualMetadataWithoutBlockingDefaultsAndInference() {
        MaterialMetadataSnapshot metadata = resolver.resolve(
            new MaterialMetadataInput(
                null,
                null,
                "   ",
                " ",
                "\t",
                "",
                " ",
                List.of("", "   "),
                null,
                " ",
                "",
                "\t",
                null,
                null
            ),
            "Plain note",
            "text",
            null,
            "text/plain",
            "Plain working note."
        );

        assertEquals(DocumentType.OTHER, metadata.documentType());
        assertEquals(KnowledgeDocumentClass.OTHER, metadata.knowledgeDocumentClass());
        assertNull(metadata.documentNumber());
        assertNull(metadata.author());
        assertNull(metadata.department());
        assertNull(metadata.project());
        assertEquals("general", metadata.workspaceKey());
        assertTrue(metadata.tags().isEmpty());
        assertEquals(MetadataValueOrigin.DEFAULT, metadata.provenance().fieldOrigins().get("documentType"));
        assertEquals(MetadataValueOrigin.DEFAULT, metadata.provenance().fieldOrigins().get("knowledgeDocumentClass"));
        assertEquals(MetadataValueOrigin.DEFAULT, metadata.provenance().fieldOrigins().get("sourceTrust"));
        assertFalse(metadata.provenance().fieldOrigins().containsKey("documentNumber"));
        assertFalse(metadata.provenance().fieldOrigins().containsKey("tags"));
    }

    @Test
    void ignoresInvalidInferredPeriodRange() {
        MaterialMetadataSnapshot metadata = resolver.resolve(
            null,
            "Procedure PROC-77",
            "text",
            null,
            "text/plain",
            """
                Период: 30.06.2026 - 01.04.2026
                Подразделение: Telecom ops
                """
        );

        assertNull(metadata.periodStart());
        assertNull(metadata.periodEnd());
        assertNull(metadata.provenance().fieldOrigins().get("periodStart"));
        assertNull(metadata.provenance().fieldOrigins().get("periodEnd"));
        assertEquals("Telecom ops", metadata.department());
    }

    @Test
    void prefersParserHintsOverRegexOnlyFallbackHints() {
        MaterialMetadataSnapshot metadata = resolver.resolve(
            null,
            "Plain note",
            "file",
            "plain-note.txt",
            "text/plain",
            """
                Автор: Regex Owner
                Номер документа: REGEX-17
                """,
            new MaterialMetadataHints(
                null,
                null,
                "PARSE-77",
                "Structured Owner",
                null,
                null,
                null,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                Map.of(
                    "author", 0.9d,
                    "documentNumber", 0.9d
                )
            )
        );

        assertEquals("Structured Owner", metadata.author());
        assertEquals("PARSE-77", metadata.documentNumber());
        assertEquals(MetadataValueOrigin.INFERRED, metadata.provenance().fieldOrigins().get("author"));
        assertEquals(0.9d, metadata.provenance().fieldConfidence().get("author"));
    }

    @Test
    void validatesExplicitWorkspaceAndProjectKeysAgainstReferences() {
        ReferenceDataRepository referenceRepository = mock(ReferenceDataRepository.class);
        when(referenceRepository.workspaceExists("north-upgrade")).thenReturn(true);
        when(referenceRepository.findProjectByKey("line-a")).thenReturn(Optional.of(new StoredReferenceProjectRecord(
            "line-a",
            "north-upgrade",
            "Line A",
            true,
            0,
            Instant.parse("2026-04-20T00:00:00Z"),
            Instant.parse("2026-04-20T00:00:00Z")
        )));
        MaterialMetadataResolver resolverWithReferences = new MaterialMetadataResolver(referenceRepository);

        MaterialMetadataSnapshot metadata = resolverWithReferences.resolve(
            new MaterialMetadataInput(
                DocumentType.REPORT,
                "north-upgrade",
                DocumentStatus.DRAFT,
                "line-a",
                null,
                MaterialLanguageCode.EN,
                List.of("manual"),
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
                null
            ),
            "Report",
            "text",
            null,
            "text/plain",
            "Plain English report text."
        );

        assertEquals("north-upgrade", metadata.workspaceKey());
        assertEquals("line-a", metadata.projectKey());
        assertEquals(DocumentStatus.DRAFT, metadata.documentStatus());
        assertEquals(MaterialLanguageCode.EN, metadata.languageCode());
        assertEquals(List.of("manual"), metadata.manualTags());
    }

    @Test
    void rejectsUnknownCanonicalProjectKeyWhenReferencesAreAvailable() {
        ReferenceDataRepository referenceRepository = mock(ReferenceDataRepository.class);
        when(referenceRepository.workspaceExists("general")).thenReturn(true);
        when(referenceRepository.findProjectByKey("missing-project")).thenReturn(Optional.empty());
        MaterialMetadataResolver resolverWithReferences = new MaterialMetadataResolver(referenceRepository);

        ApiException exception = assertThrows(ApiException.class, () -> resolverWithReferences.resolve(
            new MaterialMetadataInput(
                DocumentType.REPORT,
                "general",
                null,
                "missing-project",
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
                null
            ),
            "Report",
            "text",
            null,
            "text/plain",
            "Plain report text."
        ));

        assertEquals("material.metadata.project_not_found", exception.getCode());
    }
}
