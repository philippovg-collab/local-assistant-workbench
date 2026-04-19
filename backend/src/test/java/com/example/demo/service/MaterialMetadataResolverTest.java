package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.infrastructure.material.MaterialMetadataHints;
import com.example.demo.model.DocumentType;
import com.example.demo.model.KnowledgeDocumentClass;
import com.example.demo.model.MaterialMetadataInput;
import com.example.demo.model.MaterialMetadataSnapshot;
import com.example.demo.model.MetadataValueOrigin;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MaterialMetadataResolverTest {

    private final MaterialMetadataResolver resolver = new MaterialMetadataResolver();

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
        assertNull(metadata.workspaceKey());
        assertEquals("ru", metadata.language());
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
        assertEquals("north-upgrade", metadata.workspaceKey());
        assertEquals("GridBuild LLP", metadata.counterparty());
        assertEquals("APPROVED", metadata.businessStatus());
        assertEquals(LocalDate.parse("2026-04-01"), metadata.periodStart());
        assertEquals(LocalDate.parse("2026-06-30"), metadata.periodEnd());
        assertEquals(MetadataValueOrigin.INFERRED, metadata.provenance().fieldOrigins().get("documentType"));
        assertEquals(
            MetadataValueOrigin.INFERRED,
            metadata.provenance().fieldOrigins().get("knowledgeDocumentClass")
        );
        assertEquals(
            MetadataValueOrigin.INFERRED,
            metadata.provenance().fieldOrigins().get("workspaceKey")
        );
        assertEquals(MetadataValueOrigin.INFERRED, metadata.provenance().fieldOrigins().get("author"));
        assertTrue(metadata.provenance().fieldConfidence().containsKey("documentType"));
        assertTrue(metadata.provenance().fieldConfidence().containsKey("author"));
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
        assertEquals(List.of("grid", "policy"), metadata.tags());
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
}
