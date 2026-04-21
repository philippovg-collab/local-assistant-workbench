package com.example.demo.service;

import com.example.demo.service.material.MaterialFormatRegistry;
import com.example.demo.service.material.StoredMaterialRecord;
import com.example.demo.service.material.port.DocumentTextExtractor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.api.ApiException;
import com.example.demo.config.MaterialProperties;
import com.example.demo.config.OcrProperties;
import com.example.demo.config.RagProperties;
import com.example.demo.embedding.EmbeddingClient;
import com.example.demo.infrastructure.material.OcrCapabilityService;
import com.example.demo.infrastructure.material.OcrClient;
import com.example.demo.infrastructure.material.PdfDocumentExtractionStrategy;
import com.example.demo.infrastructure.material.PlainTextDocumentExtractionStrategy;
import com.example.demo.infrastructure.material.RoutingDocumentTextExtractor;
import com.example.demo.infrastructure.material.TesseractRuntimeProbe;
import com.example.demo.infrastructure.material.TikaDocumentTextExtractor;
import com.example.demo.model.DocumentStatus;
import com.example.demo.model.DocumentType;
import com.example.demo.model.MaterialLanguageCode;
import com.example.demo.model.MaterialIndexingStatus;
import com.example.demo.model.MaterialLineageResponse;
import com.example.demo.model.MaterialMetadataInput;
import com.example.demo.model.MaterialSummary;
import com.example.demo.model.MaterialVersionState;
import com.example.demo.model.MetadataValueOrigin;
import com.example.demo.model.SourceTrustLevel;
import com.example.demo.support.DeterministicEmbeddingClient;
import com.example.demo.support.InMemoryMaterialRepository;
import com.example.demo.support.TestLexicalRoutingSupport;
import com.example.demo.support.TestMaterialServices;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class MaterialServiceTest {

    @Test
    void normalizesMetadataInputStringsAndTags() {
        MaterialMetadataInput input = new MaterialMetadataInput(
            DocumentType.REPORT,
            LocalDate.parse("2026-04-17"),
            "  REP-42  ",
            "  Analyst  ",
            "  Strategy  ",
            "  v1  ",
            "  ru  ",
            List.of(" finance ", "finance", " ops "),
            SourceTrustLevel.MEDIUM,
            "  Program Atlas  ",
            "  Contoso  ",
            "  DRAFT  ",
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-06-30")
        );

        assertEquals("REP-42", input.documentNumber());
        assertEquals("Analyst", input.author());
        assertEquals("Strategy", input.department());
        assertEquals("v1", input.versionLabel());
        assertEquals("ru", input.language());
        assertEquals(MaterialLanguageCode.RU, input.languageCode());
        assertEquals(List.of("finance", "ops"), input.tags());
        assertEquals(List.of("finance", "ops"), input.manualTags());
        assertEquals("Program Atlas", input.project());
        assertEquals("Contoso", input.counterparty());
        assertEquals("DRAFT", input.businessStatus());
        assertEquals(DocumentStatus.DRAFT, input.documentStatus());
    }

    @Test
    void allowsIdenticalContentAcrossDifferentLineagesDuringIngestion() {
        MaterialService service = createService(new DeterministicEmbeddingClient());

        MaterialSummary first = service.saveText("Pricing FAQ", "Тариф стоит 12000 тенге.");
        MaterialSummary duplicate = service.saveText("Operations memo", "Тариф стоит 12000 тенге.");

        assertTrue(!first.id().equals(duplicate.id()));
        assertEquals(2, service.listSummaries().size());
        assertEquals(2, service.listSummaries().stream()
            .filter(summary -> summary.versionState() == MaterialVersionState.ACTIVE)
            .count());
    }

    @Test
    void deduplicatesIdenticalContentWithinTheSameLineageDuringIngestion() {
        MaterialService service = createService(new DeterministicEmbeddingClient());

        MaterialSummary first = service.saveText("Pricing FAQ", "Тариф стоит 12000 тенге.");
        MaterialSummary duplicate = service.saveText("Pricing FAQ", "Тариф стоит 12000 тенге.");

        assertEquals(first.id(), duplicate.id());
        assertEquals(1, service.listSummaries().size());
    }

    @Test
    void concurrentWritesPersistUniqueMaterials() throws Exception {
        MaterialService service = createService(new DeterministicEmbeddingClient());
        ExecutorService executor = Executors.newFixedThreadPool(6);

        try {
            List<Future<MaterialSummary>> futures = IntStream.range(0, 12)
                .mapToObj(index -> executor.submit(() -> service.saveText(
                    "Material " + index,
                    "Контент " + index + " с уникальным значением."
                )))
                .toList();

            for (Future<MaterialSummary> future : futures) {
                future.get();
            }

            assertEquals(12, service.listSummaries().size());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void persistsMetadataOnTextMaterials() {
        MaterialService service = createService(new DeterministicEmbeddingClient());

        MaterialSummary summary = service.saveText(
            "Grid policy",
            "Регламент резервирования мощности действует до конца 2026 года.",
            new MaterialMetadataInput(
                DocumentType.POLICY,
                LocalDate.parse("2026-04-17"),
                "POL-2026-17",
                "Ops lead",
                "Grid operations",
                "v3.2",
                "ru",
                List.of("policy", "grid"),
                SourceTrustLevel.HIGH,
                "North Upgrade",
                "GridBuild LLP",
                "APPROVED",
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-12-31")
            )
        );

        MaterialSummary stored = service.listSummaries().stream()
            .filter(item -> item.id().equals(summary.id()))
            .findFirst()
            .orElseThrow();

        assertEquals(DocumentType.POLICY, stored.metadata().documentType());
        assertEquals("POL-2026-17", stored.metadata().documentNumber());
        assertEquals("general", stored.metadata().workspaceKey());
        assertEquals(DocumentStatus.ACTIVE, stored.metadata().documentStatus());
        assertEquals(MaterialLanguageCode.RU, stored.metadata().languageCode());
        assertEquals(List.of("policy", "grid"), stored.metadata().tags());
        assertEquals(List.of("policy", "grid"), stored.metadata().manualTags());
        assertTrue(stored.metadata().autoTags().isEmpty());
        assertEquals(SourceTrustLevel.UNKNOWN, stored.metadata().sourceTrust());
        assertEquals(MetadataValueOrigin.MANUAL, stored.metadata().provenance().fieldOrigins().get("documentType"));
        assertEquals(MetadataValueOrigin.MANUAL, stored.metadata().provenance().fieldOrigins().get("tags"));
        assertTrue(stored.metadata().provenance().fieldConfidence().isEmpty());
    }

    @Test
    void defaultsMetadataWhenInputIsOmitted() {
        MaterialService service = createService(new DeterministicEmbeddingClient());

        MaterialSummary summary = service.saveText("Plain note", "Краткий рабочий текст без metadata.");
        MaterialSummary stored = service.listSummaries().stream()
            .filter(item -> item.id().equals(summary.id()))
            .findFirst()
            .orElseThrow();

        assertEquals(DocumentType.OTHER, stored.metadata().documentType());
        assertEquals(SourceTrustLevel.UNKNOWN, stored.metadata().sourceTrust());
        assertTrue(stored.metadata().tags().isEmpty());
        assertEquals("general", stored.metadata().workspaceKey());
        assertEquals(DocumentStatus.ACTIVE, stored.metadata().documentStatus());
        assertEquals("ru", stored.metadata().language());
        assertEquals(MetadataValueOrigin.DEFAULT, stored.metadata().provenance().fieldOrigins().get("documentType"));
        assertEquals(MetadataValueOrigin.DEFAULT, stored.metadata().provenance().fieldOrigins().get("sourceTrust"));
        assertEquals(MetadataValueOrigin.INFERRED, stored.metadata().provenance().fieldOrigins().get("language"));
        assertTrue(stored.metadata().provenance().fieldConfidence().containsKey("language"));
    }

    @Test
    void infersMetadataFromTitleAndHeaderWhenManualMetadataIsMissing() {
        MaterialService service = createService(new DeterministicEmbeddingClient());

        MaterialSummary summary = service.saveText(
            "Contract KZ-2026-0415-ENERGY 2026-04-15 v2 ru",
            """
                Автор: Dana Sarsen
                Подразделение: Grid operations
                Проект: North Upgrade
                Контрагент: GridBuild LLP
                Статус: APPROVED
                Период: 01.04.2026 - 30.06.2026

                Основные условия договора и график поставки.
                """
        );

        MaterialSummary stored = service.listSummaries().stream()
            .filter(item -> item.id().equals(summary.id()))
            .findFirst()
            .orElseThrow();

        assertEquals(DocumentType.CONTRACT, stored.metadata().documentType());
        assertEquals("KZ-2026-0415-ENERGY", stored.metadata().documentNumber());
        assertEquals("Dana Sarsen", stored.metadata().author());
        assertEquals("Grid operations", stored.metadata().department());
        assertEquals("v2", stored.metadata().versionLabel());
        assertEquals("ru", stored.metadata().language());
        assertEquals("general", stored.metadata().workspaceKey());
        assertEquals(null, stored.metadata().project());
        assertEquals("GridBuild LLP", stored.metadata().counterparty());
        assertEquals("ACTIVE", stored.metadata().businessStatus());
        assertEquals(DocumentStatus.ACTIVE, stored.metadata().documentStatus());
        assertEquals(LocalDate.parse("2026-04-15"), stored.metadata().documentDate());
        assertEquals(LocalDate.parse("2026-04-01"), stored.metadata().periodStart());
        assertEquals(LocalDate.parse("2026-06-30"), stored.metadata().periodEnd());
        assertEquals(MetadataValueOrigin.INFERRED, stored.metadata().provenance().fieldOrigins().get("documentType"));
        assertEquals(MetadataValueOrigin.INFERRED, stored.metadata().provenance().fieldOrigins().get("author"));
        assertEquals(MetadataValueOrigin.DEFAULT, stored.metadata().provenance().fieldOrigins().get("sourceTrust"));
        assertTrue(stored.metadata().provenance().fieldConfidence().containsKey("documentType"));
        assertTrue(stored.metadata().provenance().fieldConfidence().containsKey("author"));
    }

    @Test
    void keepsManualMetadataOverInferredHintsOnFileUploads() {
        MaterialService service = createService(new DeterministicEmbeddingClient());

        MaterialSummary summary = service.saveUpload(
            "Grid policy",
            new MockMultipartFile(
                "file",
                "contract-KZ-2026-0415-ENERGY-v2-ru.txt",
                "text/plain",
                """
                    Автор: Dana Sarsen
                    Подразделение: Grid operations
                    Версия: 2
                    Основные условия договора и график поставки.
                    """.getBytes(StandardCharsets.UTF_8)
            ),
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
            )
        );

        MaterialSummary stored = service.listSummaries().stream()
            .filter(item -> item.id().equals(summary.id()))
            .findFirst()
            .orElseThrow();

        assertEquals(DocumentType.POLICY, stored.metadata().documentType());
        assertEquals("Dana Sarsen", stored.metadata().author());
        assertEquals(List.of("grid", "policy"), stored.metadata().manualTags());
        assertEquals(List.of("energy"), stored.metadata().autoTags());
        assertEquals(List.of("grid", "policy", "energy"), stored.metadata().effectiveTags());
        assertEquals(List.of("grid", "policy", "energy"), stored.metadata().tags());
        assertEquals("Grid operations", stored.metadata().department());
        assertEquals("v2", stored.metadata().versionLabel());
        assertEquals(MetadataValueOrigin.MANUAL, stored.metadata().provenance().fieldOrigins().get("documentType"));
        assertEquals(MetadataValueOrigin.INFERRED, stored.metadata().provenance().fieldOrigins().get("author"));
        assertEquals(MetadataValueOrigin.INFERRED, stored.metadata().provenance().fieldOrigins().get("department"));
        assertEquals(MetadataValueOrigin.INFERRED, stored.metadata().provenance().fieldOrigins().get("versionLabel"));
    }

    @Test
    void acceptsMaterialAndMarksItFailedWhenEmbeddingGenerationFails() {
        MaterialService service = createService(new FailingEmbeddingClient());

        MaterialSummary submission = service.saveText(
            "Broken material",
            "Контент, для которого embedding client падает."
        );

        MaterialSummary summary = service.listSummaries().stream()
            .filter(item -> item.id().equals(submission.id()))
            .findFirst()
            .orElseThrow();

        assertEquals(MaterialIndexingStatus.PENDING, summary.status());
        assertEquals("embedding.provider_unavailable", summary.statusReasonCode());
        assertEquals("Embedding provider is unavailable in the test", summary.statusReasonMessage());
        assertEquals(1, service.listSummaries().size());
    }

    @Test
    void keepsDifferentFilesWithSameNameAsSeparateActiveLineages() {
        MaterialService service = createService(new DeterministicEmbeddingClient());

        MaterialSummary first = service.saveUpload(
            null,
            new MockMultipartFile(
                "file",
                "brief.txt",
                "text/plain",
                "Первый документ описывает график профилактики подстанции.".getBytes(StandardCharsets.UTF_8)
            )
        );
        MaterialSummary second = service.saveUpload(
            null,
            new MockMultipartFile(
                "file",
                "brief.txt",
                "text/plain",
                "Второй документ описывает регламент закупки оборудования.".getBytes(StandardCharsets.UTF_8)
            )
        );

        List<MaterialSummary> summaries = service.listSummaries();

        assertEquals(2, summaries.size());
        assertEquals(2, summaries.stream().filter(summary -> summary.versionState() == MaterialVersionState.ACTIVE).count());
        assertTrue(summaries.stream().anyMatch(summary -> summary.id().equals(first.id())));
        assertTrue(summaries.stream().anyMatch(summary -> summary.id().equals(second.id())));
    }

    @Test
    void keepsSameFilenameAndSharedAnchorInsideSingleLineage() {
        MaterialService service = createService(new DeterministicEmbeddingClient());

        service.saveUpload(
            null,
            new MockMultipartFile(
                "file",
                "brief.txt",
                "text/plain",
                (
                    "План ремонта северной подстанции май июнь июль август сентябрь октябрь ноябрь декабрь версия один " +
                    "подробности первого файла."
                ).getBytes(StandardCharsets.UTF_8)
            )
        );
        service.saveUpload(
            null,
            new MockMultipartFile(
                "file",
                "brief.txt",
                "text/plain",
                (
                    "План ремонта северной подстанции май июнь июль август сентябрь октябрь ноябрь декабрь версия два " +
                    "подробности второго файла."
                ).getBytes(StandardCharsets.UTF_8)
            )
        );

        List<MaterialSummary> summaries = service.listSummaries();

        assertEquals(2, summaries.size());
        assertEquals(1, summaries.stream().filter(summary -> summary.versionState() == MaterialVersionState.ACTIVE).count());
        assertEquals(1, summaries.stream().filter(summary -> summary.versionState() == MaterialVersionState.SUPERSEDED).count());
    }

    @Test
    void newMaterialInSameLineageInheritsManualTagsWhenInputOmitsThem() {
        MaterialService service = createService(new DeterministicEmbeddingClient());

        service.saveText(
            "Grid policy",
            "Первая редакция документа.",
            new MaterialMetadataInput(
                DocumentType.POLICY,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of("manual-grid", "retained"),
                null,
                null,
                null,
                null,
                null,
                null
            )
        );
        MaterialSummary second = service.saveText("Grid policy", "Вторая редакция документа с другим текстом.");

        MaterialSummary stored = service.listSummaries().stream()
            .filter(summary -> summary.id().equals(second.id()))
            .findFirst()
            .orElseThrow();

        assertEquals(MaterialVersionState.ACTIVE, stored.versionState());
        assertEquals(List.of("manual-grid", "retained"), stored.metadata().manualTags());
        assertEquals(List.of("manual-grid", "retained"), stored.metadata().tags());
    }

    @Test
    void explicitManualTagsOverrideSameLineageInheritance() {
        MaterialService service = createService(new DeterministicEmbeddingClient());

        service.saveText(
            "Grid policy",
            "Первая редакция документа.",
            new MaterialMetadataInput(
                DocumentType.POLICY,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of("manual-grid", "retained"),
                null,
                null,
                null,
                null,
                null,
                null
            )
        );
        MaterialSummary second = service.saveText(
            "Grid policy",
            "Вторая редакция документа с другим текстом.",
            new MaterialMetadataInput(
                DocumentType.POLICY,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of("override"),
                null,
                null,
                null,
                null,
                null,
                null
            )
        );

        MaterialSummary stored = service.listSummaries().stream()
            .filter(summary -> summary.id().equals(second.id()))
            .findFirst()
            .orElseThrow();

        assertEquals(List.of("override"), stored.metadata().manualTags());
        assertEquals(List.of("override"), stored.metadata().tags());
    }

    @Test
    void controlledVersionUploadCreatesNewActiveVersionInSameLineage() {
        MaterialService service = createService(new DeterministicEmbeddingClient());

        MaterialSummary first = service.saveUpload(
            "Grid policy",
            new MockMultipartFile(
                "file",
                "grid-policy-v1.txt",
                "text/plain",
                "Первая редакция документа.".getBytes(StandardCharsets.UTF_8)
            )
        );
        MaterialSummary second = service.saveUploadVersion(
            first.id(),
            null,
            new MockMultipartFile(
                "file",
                "renamed-anything.txt",
                "text/plain",
                "Вторая редакция документа с другим содержимым.".getBytes(StandardCharsets.UTF_8)
            ),
            null
        );

        MaterialLineageResponse lineage = service.getLineage(first.id());
        MaterialSummary storedFirst = service.listSummaries().stream()
            .filter(summary -> summary.id().equals(first.id()))
            .findFirst()
            .orElseThrow();

        assertTrue(!first.id().equals(second.id()));
        assertEquals(first.title(), second.title());
        assertEquals(second.id(), lineage.activeMaterialId());
        assertEquals(2, lineage.versions().size());
        assertEquals(MaterialVersionState.SUPERSEDED, storedFirst.versionState());
        assertEquals(MaterialVersionState.ACTIVE, second.versionState());
    }

    @Test
    void controlledVersionUploadClonesEditableMetadataWhenMetadataIsOmitted() {
        MaterialService service = createService(new DeterministicEmbeddingClient());

        MaterialSummary first = service.saveText(
            "Grid policy",
            "Первая редакция документа.",
            new MaterialMetadataInput(
                DocumentType.POLICY,
                "general",
                DocumentStatus.DRAFT,
                null,
                "POL-42",
                MaterialLanguageCode.RU,
                List.of("manual-grid", "retained"),
                LocalDate.parse("2026-04-01"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                null,
                null,
                null,
                null
            )
        );

        MaterialSummary second = service.saveUploadVersion(
            first.id(),
            null,
            new MockMultipartFile(
                "file",
                "grid-policy-v2.txt",
                "text/plain",
                "Вторая редакция документа.".getBytes(StandardCharsets.UTF_8)
            ),
            null
        );

        MaterialSummary stored = service.listSummaries().stream()
            .filter(summary -> summary.id().equals(second.id()))
            .findFirst()
            .orElseThrow();

        assertEquals(DocumentType.POLICY, stored.metadata().documentType());
        assertEquals(DocumentStatus.DRAFT, stored.metadata().documentStatus());
        assertEquals("POL-42", stored.metadata().documentNumber());
        assertEquals(MaterialLanguageCode.RU, stored.metadata().languageCode());
        assertEquals(LocalDate.parse("2026-04-01"), stored.metadata().periodStart());
        assertEquals(List.of("manual-grid", "retained"), stored.metadata().manualTags());
    }

    @Test
    void controlledVersionUploadUsesProvidedMetadataAsFullReplacement() {
        MaterialService service = createService(new DeterministicEmbeddingClient());

        MaterialSummary first = service.saveText(
            "Grid policy",
            "Первая редакция документа.",
            new MaterialMetadataInput(
                DocumentType.POLICY,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of("manual-grid", "retained"),
                null,
                null,
                null,
                null,
                null,
                null
            )
        );

        MaterialSummary second = service.saveUploadVersion(
            first.id(),
            null,
            new MockMultipartFile(
                "file",
                "grid-policy-v2.txt",
                "text/plain",
                "Вторая редакция документа.".getBytes(StandardCharsets.UTF_8)
            ),
            new MaterialMetadataInput(
                DocumentType.REPORT,
                "general",
                DocumentStatus.ACTIVE,
                null,
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
                List.of(),
                null,
                null,
                null,
                null
            )
        );

        MaterialSummary stored = service.listSummaries().stream()
            .filter(summary -> summary.id().equals(second.id()))
            .findFirst()
            .orElseThrow();

        assertEquals(DocumentType.REPORT, stored.metadata().documentType());
        assertTrue(stored.metadata().manualTags().isEmpty());
    }

    @Test
    void controlledVersionUploadRejectsHistoricalTargets() {
        MaterialService service = createService(new DeterministicEmbeddingClient());

        MaterialSummary first = service.saveUpload(
            "Grid policy",
            new MockMultipartFile(
                "file",
                "grid-policy-v1.txt",
                "text/plain",
                "Первая редакция документа.".getBytes(StandardCharsets.UTF_8)
            )
        );
        service.saveUploadVersion(
            first.id(),
            null,
            new MockMultipartFile(
                "file",
                "grid-policy-v2.txt",
                "text/plain",
                "Вторая редакция документа.".getBytes(StandardCharsets.UTF_8)
            ),
            null
        );

        ApiException exception = assertThrows(ApiException.class, () -> service.saveUploadVersion(
            first.id(),
            null,
            new MockMultipartFile(
                "file",
                "grid-policy-v3.txt",
                "text/plain",
                "Третья редакция документа.".getBytes(StandardCharsets.UTF_8)
            ),
            null
        ));

        assertEquals("material.version_upload_requires_active_version", exception.getCode());
    }

    @Test
    void controlledVersionUploadRejectsDuplicateContentInsideLineage() {
        MaterialService service = createService(new DeterministicEmbeddingClient());

        MaterialSummary first = service.saveUpload(
            "Grid policy",
            new MockMultipartFile(
                "file",
                "grid-policy-v1.txt",
                "text/plain",
                "Одинаковое содержимое.".getBytes(StandardCharsets.UTF_8)
            )
        );

        ApiException exception = assertThrows(ApiException.class, () -> service.saveUploadVersion(
            first.id(),
            null,
            new MockMultipartFile(
                "file",
                "grid-policy-v2.txt",
                "text/plain",
                "Одинаковое содержимое.".getBytes(StandardCharsets.UTF_8)
            ),
            null
        ));

        assertEquals("material.version_duplicate_content", exception.getCode());
    }

    @Test
    void ingestionDoesNotUseCatalogFindAllForLineageResolution() {
        MaterialService service = createService(new DeterministicEmbeddingClient(), new NoFindAllRepository());

        MaterialSummary summary = service.saveText("Pricing FAQ", "Тариф стоит 12000 тенге.");

        assertEquals("Pricing FAQ", summary.title());
        assertEquals(MaterialVersionState.ACTIVE, summary.versionState());
    }

    @Test
    void deletingActiveVersionPromotesLatestSupersededVersion() {
        MaterialService service = createService(new DeterministicEmbeddingClient());

        MaterialSummary first = service.saveText("Pricing FAQ", "Старая цена: 9000 тенге.");
        MaterialSummary second = service.saveText("Pricing FAQ", "Новая цена: 12000 тенге.");

        service.delete(second.id());

        List<MaterialSummary> summaries = service.listSummaries();
        MaterialSummary remaining = summaries.getFirst();
        MaterialRetrievalResult retrievalResult = service.retrieveContext("9000");

        assertEquals(1, summaries.size());
        assertEquals(first.id(), remaining.id());
        assertEquals(MaterialVersionState.ACTIVE, remaining.versionState());
        assertEquals(1, retrievalResult.activeMaterialCount());
        assertEquals(1, retrievalResult.readyMaterialCount());
        assertFalse(retrievalResult.sources().isEmpty());
        assertTrue(retrievalResult.sources().getFirst().excerpt().contains("9000"));
    }

    @Test
    void rejectsBlankTextMaterials() {
        MaterialService service = createService(new DeterministicEmbeddingClient());

        ApiException exception = assertThrows(ApiException.class, () -> service.saveText("Pricing", "   "));

        assertEquals("material.empty_text", exception.getCode());
    }

    @Test
    void rejectsOversizedUploadsBeforeExtraction() {
        MaterialService service = createService(new DeterministicEmbeddingClient());
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "big.txt",
            "text/plain",
            new byte[8_388_609]
        );

        ApiException exception = assertThrows(ApiException.class, () -> service.saveUpload("Big", file));

        assertEquals("material.upload_too_large", exception.getCode());
    }

    @Test
    void rejectsUnsupportedUploadFormats() {
        MaterialService service = createService(new DeterministicEmbeddingClient());
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "diagram.png",
            "image/png",
            "not-supported".getBytes(StandardCharsets.UTF_8)
        );

        ApiException exception = assertThrows(ApiException.class, () -> service.saveUpload("Diagram", file));

        assertEquals("material.unsupported_format", exception.getCode());
    }

    @Test
    void rejectsBrokenDocxDocumentsWithExtractionError() throws Exception {
        MaterialService service = createService(new DeterministicEmbeddingClient());
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "broken.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            createBrokenDocx()
        );

        ApiException exception = assertThrows(ApiException.class, () -> service.saveUpload("Broken", file));

        assertEquals("material.extraction_failed", exception.getCode());
    }

    private MaterialService createService(EmbeddingClient embeddingClient) {
        return createService(embeddingClient, new InMemoryMaterialRepository());
    }

    private MaterialService createService(EmbeddingClient embeddingClient, InMemoryMaterialRepository repository) {
        MaterialProperties properties = new MaterialProperties();
        MaterialFormatRegistry formatRegistry = new MaterialFormatRegistry();
        OcrProperties ocrProperties = new OcrProperties();
        OcrClient ocrClient = (imagePath, pageNumber) -> "OCR fallback text for page " + pageNumber;
        com.example.demo.infrastructure.material.OcrCapabilityService ocrCapabilityService =
            new com.example.demo.infrastructure.material.OcrCapabilityService(
            ocrProperties,
            (binaryPath, timeoutSeconds) -> new TesseractRuntimeProbe.CommandResult(
                0,
                """
                List of available languages in "/tmp/tessdata" (3):
                kaz
                rus
                eng
                """,
                "",
                false
            )
        );
        DocumentTextExtractor extractor = new RoutingDocumentTextExtractor(List.of(
            new PlainTextDocumentExtractionStrategy(formatRegistry),
            new PdfDocumentExtractionStrategy(formatRegistry, ocrProperties, ocrClient, ocrCapabilityService),
            new TikaDocumentTextExtractor(properties, formatRegistry)
        ));
        MaterialContentSupport contentSupport = new MaterialContentSupport(properties);
        RagProperties ragProperties = new RagProperties();
        MaterialSearchSyncLifecycleService lifecycleService = TestMaterialServices.lifecycleService(
            repository,
            repository,
            repository,
            repository,
            repository
        );
        AfterCommitExecutor afterCommitExecutor = new AfterCommitExecutor();
        MaterialIndexingService indexingService = new MaterialIndexingService(
            repository,
            repository,
            contentSupport,
            embeddingClient,
            properties,
            lifecycleService,
            Runnable::run
        );
        return new MaterialService(
            new MaterialQueryService(
                repository,
                repository,
                properties,
                formatRegistry,
                ocrCapabilityService,
                contentSupport,
                lifecycleService,
                indexingService,
                afterCommitExecutor,
                new MaterialMetadataResolver()
            ),
            new MaterialIngestionService(
                repository,
                repository,
                extractor,
                properties,
                contentSupport,
                new MaterialMetadataResolver(),
                lifecycleService,
                indexingService,
                afterCommitExecutor
            ),
            TestMaterialServices.retrievalService(
                repository,
                repository,
                repository,
                TestLexicalRoutingSupport.productionRouter(repository, ragProperties, List.of(repository)),
                embeddingClient,
                ragProperties,
                new HybridChunkRanker(),
                contentSupport
            )
        );
    }

    private static final class FailingEmbeddingClient implements EmbeddingClient {

        @Override
        public float[] embed(String input) {
            throw failure();
        }

        @Override
        public List<float[]> embedAll(List<String> inputs) {
            throw failure();
        }

        private ApiException failure() {
            return new ApiException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "embedding.provider_unavailable",
                "Embedding provider is unavailable in the test"
            );
        }
    }

    private static final class NoFindAllRepository extends InMemoryMaterialRepository {

        @Override
        public synchronized List<StoredMaterialRecord> findAll() {
            throw new AssertionError("findAll should not be used for lineage resolution");
        }
    }

    private byte[] createBrokenDocx() throws Exception {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            zipOutputStream.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zipOutputStream.write("<Types></Types>".getBytes(StandardCharsets.UTF_8));
            zipOutputStream.closeEntry();
            return outputStream.toByteArray();
        }
    }
}
