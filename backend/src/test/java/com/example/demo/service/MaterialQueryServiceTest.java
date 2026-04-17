package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.api.ApiException;
import com.example.demo.config.MaterialProperties;
import com.example.demo.infrastructure.material.MaterialFormatRegistry;
import com.example.demo.infrastructure.material.OcrCapability;
import com.example.demo.infrastructure.material.OcrCapabilityProvider;
import com.example.demo.infrastructure.material.StoredMaterialRecord;
import com.example.demo.model.MaterialLineageResponse;
import com.example.demo.model.MaterialIndexingStatus;
import com.example.demo.model.MaterialUploadPolicyResponse;
import com.example.demo.model.MaterialVersionState;
import com.example.demo.support.InMemoryMaterialRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MaterialQueryServiceTest {

    @Test
    void returnsUploadPolicyWhenOcrIsHealthy() {
        MaterialProperties properties = new MaterialProperties();
        MaterialQueryService service = new MaterialQueryService(
            new InMemoryMaterialRepository(),
            new InMemoryMaterialRepository(),
            properties,
            new MaterialFormatRegistry(),
            () -> OcrCapability.embeddedTextAndOcr(List.of("kaz", "rus", "eng"), 12),
            new MaterialContentSupport(properties),
            org.mockito.Mockito.mock(MaterialIndexingService.class)
        );

        MaterialUploadPolicyResponse response = service.getUploadPolicy();

        assertEquals(2_000_000, response.maxUploadBytes());
        assertTrue(response.acceptedExtensions().contains("pdf"));
        assertTrue(response.pdf().scannedPdfSupport());
        assertEquals("embedded_text_and_ocr", response.pdf().mode());
        assertEquals(List.of("kaz", "rus", "eng"), response.pdf().ocrLanguages());
    }

    @Test
    void returnsDegradedPdfPolicyWhenOcrIsUnavailable() {
        MaterialProperties properties = new MaterialProperties();
        OcrCapabilityProvider capabilityProvider = () -> OcrCapability.embeddedTextOnly(
            "material.ocr_unavailable",
            "Tesseract OCR binary is unavailable at 'tesseract'.",
            List.of("kaz", "rus", "eng"),
            12
        );
        MaterialQueryService service = new MaterialQueryService(
            new InMemoryMaterialRepository(),
            new InMemoryMaterialRepository(),
            properties,
            new MaterialFormatRegistry(),
            capabilityProvider,
            new MaterialContentSupport(properties),
            org.mockito.Mockito.mock(MaterialIndexingService.class)
        );

        MaterialUploadPolicyResponse response = service.getUploadPolicy();

        assertFalse(response.pdf().scannedPdfSupport());
        assertEquals("embedded_text_only", response.pdf().mode());
        assertEquals("material.ocr_unavailable", response.pdf().ocrReasonCode());
    }

    @Test
    void deletingActiveVersionPromotesMostRecentlyUpdatedSupersededVersion() {
        MaterialProperties properties = new MaterialProperties();
        InMemoryMaterialRepository repository = new InMemoryMaterialRepository();
        MaterialQueryService service = createService(repository, properties);
        String sourceKey = "pricing-lineage";

        StoredMaterialRecord olderSuperseded = materialRecord(
            "Pricing FAQ",
            "Старая цена 9000 тенге.",
            sourceKey,
            MaterialVersionState.SUPERSEDED,
            Instant.parse("2026-04-17T09:00:00Z"),
            Instant.parse("2026-04-17T10:00:00Z")
        );
        StoredMaterialRecord newestSuperseded = materialRecord(
            "Pricing FAQ",
            "Прошлая цена 11000 тенге.",
            sourceKey,
            MaterialVersionState.SUPERSEDED,
            Instant.parse("2026-04-17T09:30:00Z"),
            Instant.parse("2026-04-17T10:10:00Z")
        );
        StoredMaterialRecord active = materialRecord(
            "Pricing FAQ",
            "Текущая цена 12000 тенге.",
            sourceKey,
            MaterialVersionState.ACTIVE,
            Instant.parse("2026-04-17T10:15:00Z"),
            Instant.parse("2026-04-17T10:15:00Z")
        );

        repository.save(olderSuperseded, List.of());
        repository.save(newestSuperseded, List.of());
        repository.save(active, List.of());

        service.delete(active.id());

        assertTrue(repository.findById(active.id()).isEmpty());
        assertEquals(MaterialVersionState.SUPERSEDED, repository.findById(olderSuperseded.id()).orElseThrow().versionState());
        assertEquals(MaterialVersionState.ACTIVE, repository.findById(newestSuperseded.id()).orElseThrow().versionState());
        assertEquals(1, repository.countActiveMaterials());
    }

    @Test
    void deletingActiveVersionBreaksPromotionTiesByCreatedAt() {
        MaterialProperties properties = new MaterialProperties();
        InMemoryMaterialRepository repository = new InMemoryMaterialRepository();
        MaterialQueryService service = createService(repository, properties);
        String sourceKey = "pricing-lineage";
        Instant sharedUpdatedAt = Instant.parse("2026-04-17T10:10:00Z");

        StoredMaterialRecord olderCreated = materialRecord(
            "Pricing FAQ",
            "Редакция 1.",
            sourceKey,
            MaterialVersionState.SUPERSEDED,
            Instant.parse("2026-04-17T09:00:00Z"),
            sharedUpdatedAt
        );
        StoredMaterialRecord newerCreated = materialRecord(
            "Pricing FAQ",
            "Редакция 2.",
            sourceKey,
            MaterialVersionState.SUPERSEDED,
            Instant.parse("2026-04-17T09:30:00Z"),
            sharedUpdatedAt
        );
        StoredMaterialRecord active = materialRecord(
            "Pricing FAQ",
            "Редакция 3.",
            sourceKey,
            MaterialVersionState.ACTIVE,
            Instant.parse("2026-04-17T10:20:00Z"),
            Instant.parse("2026-04-17T10:20:00Z")
        );

        repository.save(olderCreated, List.of());
        repository.save(newerCreated, List.of());
        repository.save(active, List.of());

        service.delete(active.id());

        assertEquals(MaterialVersionState.SUPERSEDED, repository.findById(olderCreated.id()).orElseThrow().versionState());
        assertEquals(MaterialVersionState.ACTIVE, repository.findById(newerCreated.id()).orElseThrow().versionState());
    }

    @Test
    void deletingSupersededVersionDoesNotAffectActiveLineage() {
        MaterialProperties properties = new MaterialProperties();
        InMemoryMaterialRepository repository = new InMemoryMaterialRepository();
        MaterialQueryService service = createService(repository, properties);
        String sourceKey = "pricing-lineage";

        StoredMaterialRecord superseded = materialRecord(
            "Pricing FAQ",
            "Старая цена 9000 тенге.",
            sourceKey,
            MaterialVersionState.SUPERSEDED,
            Instant.parse("2026-04-17T09:00:00Z"),
            Instant.parse("2026-04-17T09:30:00Z")
        );
        StoredMaterialRecord active = materialRecord(
            "Pricing FAQ",
            "Текущая цена 12000 тенге.",
            sourceKey,
            MaterialVersionState.ACTIVE,
            Instant.parse("2026-04-17T10:00:00Z"),
            Instant.parse("2026-04-17T10:00:00Z")
        );

        repository.save(superseded, List.of());
        repository.save(active, List.of());

        service.delete(superseded.id());

        assertTrue(repository.findById(superseded.id()).isEmpty());
        assertEquals(MaterialVersionState.ACTIVE, repository.findById(active.id()).orElseThrow().versionState());
        assertEquals(1, repository.countActiveMaterials());
    }

    @Test
    void deletingUnknownIdIsSafeAndLeavesStateUntouched() {
        MaterialProperties properties = new MaterialProperties();
        InMemoryMaterialRepository repository = new InMemoryMaterialRepository();
        MaterialQueryService service = createService(repository, properties);
        StoredMaterialRecord active = materialRecord(
            "Pricing FAQ",
            "Текущая цена 12000 тенге.",
            "pricing-lineage",
            MaterialVersionState.ACTIVE,
            Instant.parse("2026-04-17T10:00:00Z"),
            Instant.parse("2026-04-17T10:00:00Z")
        );
        repository.save(active, List.of());

        assertDoesNotThrow(() -> service.delete(UUID.randomUUID().toString()));

        assertEquals(1, repository.countMaterials());
        assertEquals(MaterialVersionState.ACTIVE, repository.findById(active.id()).orElseThrow().versionState());
    }

    @Test
    void reindexsFailedActiveMaterialBackToPending() {
        MaterialProperties properties = new MaterialProperties();
        InMemoryMaterialRepository repository = new InMemoryMaterialRepository();
        MaterialIndexingService indexingService = org.mockito.Mockito.mock(MaterialIndexingService.class);
        MaterialQueryService service = createService(repository, properties, indexingService);
        StoredMaterialRecord failed = materialRecord(
            "Pricing FAQ",
            "Ошибка индексации.",
            "pricing-lineage",
            MaterialVersionState.ACTIVE,
            Instant.parse("2026-04-17T10:00:00Z"),
            Instant.parse("2026-04-17T10:00:00Z"),
            MaterialIndexingStatus.FAILED,
            "embedding.provider_unavailable",
            "Embedding недоступен"
        );
        repository.save(failed, List.of());

        service.reindex(failed.id());

        StoredMaterialRecord updated = repository.findById(failed.id()).orElseThrow();
        assertEquals(MaterialIndexingStatus.PENDING, updated.status());
        assertEquals(null, updated.statusReasonCode());
        org.mockito.Mockito.verify(indexingService).requestProcessing();
    }

    @Test
    void preservesPartialWarningWhenReindexingPartialReadyMaterial() {
        MaterialProperties properties = new MaterialProperties();
        InMemoryMaterialRepository repository = new InMemoryMaterialRepository();
        MaterialIndexingService indexingService = org.mockito.Mockito.mock(MaterialIndexingService.class);
        MaterialQueryService service = createService(repository, properties, indexingService);
        StoredMaterialRecord partialReady = materialRecord(
            "Pricing FAQ",
            "Частично распознанный документ.",
            "pricing-lineage",
            MaterialVersionState.ACTIVE,
            Instant.parse("2026-04-17T10:00:00Z"),
            Instant.parse("2026-04-17T10:00:00Z"),
            MaterialIndexingStatus.PARTIAL_READY,
            "material.partial_ocr",
            "Распознан не весь документ"
        );
        repository.save(partialReady, List.of());

        service.reindex(partialReady.id());

        StoredMaterialRecord updated = repository.findById(partialReady.id()).orElseThrow();
        assertEquals(MaterialIndexingStatus.PENDING, updated.status());
        assertEquals("material.partial_ocr", updated.statusReasonCode());
    }

    @Test
    void rejectsReindexForSupersededVersions() {
        MaterialProperties properties = new MaterialProperties();
        InMemoryMaterialRepository repository = new InMemoryMaterialRepository();
        MaterialQueryService service = createService(
            repository,
            properties,
            org.mockito.Mockito.mock(MaterialIndexingService.class)
        );
        StoredMaterialRecord superseded = materialRecord(
            "Pricing FAQ",
            "Историческая версия.",
            "pricing-lineage",
            MaterialVersionState.SUPERSEDED,
            Instant.parse("2026-04-17T10:00:00Z"),
            Instant.parse("2026-04-17T10:00:00Z")
        );
        repository.save(superseded, List.of());

        ApiException exception = assertThrows(ApiException.class, () -> service.reindex(superseded.id()));

        assertEquals("material.reindex_requires_active_version", exception.getCode());
    }

    @Test
    void returnsOrderedLineageWithLegacyFallbackReason() {
        MaterialProperties properties = new MaterialProperties();
        InMemoryMaterialRepository repository = new InMemoryMaterialRepository();
        MaterialQueryService service = createService(
            repository,
            properties,
            org.mockito.Mockito.mock(MaterialIndexingService.class)
        );
        String sourceKey = "pricing-lineage";
        StoredMaterialRecord older = materialRecord(
            "Pricing FAQ",
            "Старая версия",
            sourceKey,
            MaterialVersionState.SUPERSEDED,
            Instant.parse("2026-04-17T09:00:00Z"),
            Instant.parse("2026-04-17T09:30:00Z")
        );
        StoredMaterialRecord active = materialRecord(
            "Pricing FAQ",
            "Новая версия",
            sourceKey,
            MaterialVersionState.ACTIVE,
            Instant.parse("2026-04-17T10:00:00Z"),
            Instant.parse("2026-04-17T10:00:00Z")
        );
        repository.save(older, List.of());
        repository.save(active, List.of());

        MaterialLineageResponse response = service.getLineage(older.id());

        assertEquals(active.id(), response.activeMaterialId());
        assertEquals(active.id(), response.versions().getFirst().id());
        assertEquals("material.supersede_reason_legacy_unknown", response.versions().get(1).supersedeReason());
    }

    private MaterialQueryService createService(
        InMemoryMaterialRepository repository,
        MaterialProperties properties,
        MaterialIndexingService indexingService
    ) {
        return new MaterialQueryService(
            repository,
            repository,
            properties,
            new MaterialFormatRegistry(),
            () -> OcrCapability.embeddedTextAndOcr(List.of("kaz", "rus", "eng"), 12),
            new MaterialContentSupport(properties),
            indexingService
        );
    }

    private MaterialQueryService createService(InMemoryMaterialRepository repository, MaterialProperties properties) {
        return createService(repository, properties, org.mockito.Mockito.mock(MaterialIndexingService.class));
    }

    private StoredMaterialRecord materialRecord(
        String title,
        String content,
        String sourceKey,
        MaterialVersionState versionState,
        Instant createdAt,
        Instant updatedAt
    ) {
        return materialRecord(
            title,
            content,
            sourceKey,
            versionState,
            createdAt,
            updatedAt,
            MaterialIndexingStatus.READY,
            null,
            null
        );
    }

    private StoredMaterialRecord materialRecord(
        String title,
        String content,
        String sourceKey,
        MaterialVersionState versionState,
        Instant createdAt,
        Instant updatedAt,
        MaterialIndexingStatus status,
        String statusReasonCode,
        String statusReasonMessage
    ) {
        return new StoredMaterialRecord(
            UUID.randomUUID().toString(),
            title,
            "text",
            null,
            "text/plain",
            content,
            content,
            UUID.randomUUID().toString(),
            sourceKey,
            "direct-text",
            false,
            null,
            List.of(),
            status,
            versionState,
            statusReasonCode,
            statusReasonMessage,
            createdAt,
            updatedAt
        );
    }
}
