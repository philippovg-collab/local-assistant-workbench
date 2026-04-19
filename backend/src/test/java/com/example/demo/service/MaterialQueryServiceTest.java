package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.api.ApiException;
import com.example.demo.config.MaterialProperties;
import com.example.demo.infrastructure.material.ChunkProfile;
import com.example.demo.infrastructure.material.MaterialCatalogRepository;
import com.example.demo.infrastructure.material.MaterialChunkingRepository;
import com.example.demo.infrastructure.material.MaterialFormatRegistry;
import com.example.demo.infrastructure.material.OcrCapability;
import com.example.demo.infrastructure.material.OcrCapabilityProvider;
import com.example.demo.infrastructure.material.StoredMaterialChunk;
import com.example.demo.infrastructure.material.StoredMaterialRecord;
import com.example.demo.infrastructure.material.StoredMaterialSegment;
import com.example.demo.model.MaterialLineageResponse;
import com.example.demo.model.MaterialIndexingStatus;
import com.example.demo.model.MaterialListResponse;
import com.example.demo.model.MaterialSummary;
import com.example.demo.model.RechunkActiveMaterialsBatchRequest;
import com.example.demo.model.RechunkActiveMaterialsBatchResponse;
import com.example.demo.model.RechunkActiveMaterialsResponse;
import com.example.demo.model.MaterialUploadPolicyResponse;
import com.example.demo.model.MaterialVersionState;
import com.example.demo.support.InMemoryMaterialRepository;
import com.example.demo.support.TestMaterialServices;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MaterialQueryServiceTest {

    @Test
    void returnsUploadPolicyWhenOcrIsHealthy() {
        MaterialProperties properties = new MaterialProperties();
        InMemoryMaterialRepository repository = new InMemoryMaterialRepository();
        MaterialQueryService service = new MaterialQueryService(
            repository,
            repository,
            properties,
            new MaterialFormatRegistry(),
            () -> OcrCapability.embeddedTextAndOcr(List.of("kaz", "rus", "eng"), 12),
            new MaterialContentSupport(properties),
            TestMaterialServices.lifecycleService(repository, repository, repository, repository, repository),
            org.mockito.Mockito.mock(MaterialIndexingService.class),
            new AfterCommitExecutor()
        );

        MaterialUploadPolicyResponse response = service.getUploadPolicy();

        assertEquals(8_388_608, response.maxUploadBytes());
        assertTrue(response.acceptedExtensions().contains("pdf"));
        assertTrue(response.pdf().scannedPdfSupport());
        assertEquals("embedded_text_and_ocr", response.pdf().mode());
        assertEquals(List.of("kaz", "rus", "eng"), response.pdf().ocrLanguages());
    }

    @Test
    void returnsDegradedPdfPolicyWhenOcrIsUnavailable() {
        MaterialProperties properties = new MaterialProperties();
        InMemoryMaterialRepository repository = new InMemoryMaterialRepository();
        OcrCapabilityProvider capabilityProvider = () -> OcrCapability.embeddedTextOnly(
            "material.ocr_unavailable",
            "Tesseract OCR binary is unavailable at 'tesseract'.",
            List.of("kaz", "rus", "eng"),
            12
        );
        MaterialQueryService service = new MaterialQueryService(
            repository,
            repository,
            properties,
            new MaterialFormatRegistry(),
            capabilityProvider,
            new MaterialContentSupport(properties),
            TestMaterialServices.lifecycleService(repository, repository, repository, repository, repository),
            org.mockito.Mockito.mock(MaterialIndexingService.class),
            new AfterCommitExecutor()
        );

        MaterialUploadPolicyResponse response = service.getUploadPolicy();

        assertFalse(response.pdf().scannedPdfSupport());
        assertEquals("embedded_text_only", response.pdf().mode());
        assertEquals("material.ocr_unavailable", response.pdf().ocrReasonCode());
    }

    @Test
    void listSummariesUsesRepositoryProjectionInsteadOfLoadingFullRecords() {
        MaterialProperties properties = new MaterialProperties();
        MaterialCatalogRepository repository = org.mockito.Mockito.mock(MaterialCatalogRepository.class);
        org.mockito.Mockito.when(repository.findSummaries(0, 100)).thenReturn(List.of());
        MaterialQueryService service = new MaterialQueryService(
            repository,
            org.mockito.Mockito.mock(MaterialChunkingRepository.class),
            properties,
            new MaterialFormatRegistry(),
            () -> OcrCapability.embeddedTextOnly("unused", "unused", List.of("kaz"), 12),
            new MaterialContentSupport(properties),
            org.mockito.Mockito.mock(MaterialSearchSyncLifecycleService.class),
            org.mockito.Mockito.mock(MaterialIndexingService.class),
            new AfterCommitExecutor()
        );

        assertTrue(service.listSummaries().isEmpty());

        org.mockito.Mockito.verify(repository).findSummaries(0, 100);
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).findAll();
    }

    @Test
    void listSummariesPageReturnsCatalogTotalAndHasMore() {
        MaterialProperties properties = new MaterialProperties();
        MaterialCatalogRepository repository = org.mockito.Mockito.mock(MaterialCatalogRepository.class);
        MaterialSummary summary = new MaterialSummary(
            "00000000-0000-0000-0000-000000000001",
            "Pricing FAQ",
            "text",
            null,
            MaterialIndexingStatus.READY,
            MaterialVersionState.ACTIVE,
            null,
            null,
            Instant.parse("2026-04-17T10:00:00Z"),
            42,
            "Pricing preview"
        );
        org.mockito.Mockito.when(repository.findSummaries(100, 100)).thenReturn(List.of(summary));
        org.mockito.Mockito.when(repository.countMaterials()).thenReturn(201);
        MaterialQueryService service = new MaterialQueryService(
            repository,
            org.mockito.Mockito.mock(MaterialChunkingRepository.class),
            properties,
            new MaterialFormatRegistry(),
            () -> OcrCapability.embeddedTextOnly("unused", "unused", List.of("kaz"), 12),
            new MaterialContentSupport(properties),
            org.mockito.Mockito.mock(MaterialSearchSyncLifecycleService.class),
            org.mockito.Mockito.mock(MaterialIndexingService.class),
            new AfterCommitExecutor()
        );

        MaterialListResponse page = service.listSummariesPage(100, 100);

        assertEquals(1, page.items().size());
        assertEquals(201, page.total());
        assertEquals(100, page.offset());
        assertEquals(100, page.limit());
        assertTrue(page.hasMore());
    }

    @Test
    void deletingActiveVersionPromotesHighestLineageVersionEvenWhenOlderVersionHasNewerUpdatedAt() {
        MaterialProperties properties = new MaterialProperties();
        InMemoryMaterialRepository repository = new InMemoryMaterialRepository();
        MaterialQueryService service = createService(repository, properties);
        String sourceKey = "pricing-lineage";

        StoredMaterialRecord olderLineageVersion = materialRecord(
            "Pricing FAQ",
            "Старая цена 9000 тенге.",
            sourceKey,
            MaterialVersionState.SUPERSEDED,
            Instant.parse("2026-04-17T09:00:00Z"),
            Instant.parse("2026-04-17T10:20:00Z")
        );
        StoredMaterialRecord newerLineageVersion = materialRecord(
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

        repository.save(olderLineageVersion, List.of());
        repository.save(newerLineageVersion, List.of());
        repository.save(active, List.of());

        service.delete(active.id());

        assertTrue(repository.findById(active.id()).isEmpty());
        assertEquals(MaterialVersionState.SUPERSEDED, repository.findById(olderLineageVersion.id()).orElseThrow().versionState());
        assertEquals(MaterialVersionState.ACTIVE, repository.findById(newerLineageVersion.id()).orElseThrow().versionState());
        assertEquals(1, repository.countActiveMaterials());
    }

    @Test
    void returnsLineageOrderedByActiveThenLineageVersion() {
        MaterialProperties properties = new MaterialProperties();
        InMemoryMaterialRepository repository = new InMemoryMaterialRepository();
        MaterialQueryService service = createService(repository, properties);
        String sourceKey = "pricing-lineage";

        StoredMaterialRecord olderSuperseded = materialRecord(
            "Pricing FAQ",
            "Редакция 1.",
            sourceKey,
            MaterialVersionState.SUPERSEDED,
            Instant.parse("2026-04-17T09:00:00Z"),
            Instant.parse("2026-04-17T10:10:00Z")
        );
        StoredMaterialRecord newerSuperseded = materialRecord(
            "Pricing FAQ",
            "Редакция 2.",
            sourceKey,
            MaterialVersionState.SUPERSEDED,
            Instant.parse("2026-04-17T09:30:00Z"),
            Instant.parse("2026-04-17T10:05:00Z")
        );
        StoredMaterialRecord active = materialRecord(
            "Pricing FAQ",
            "Редакция 3.",
            sourceKey,
            MaterialVersionState.ACTIVE,
            Instant.parse("2026-04-17T10:20:00Z"),
            Instant.parse("2026-04-17T10:20:00Z")
        );

        repository.save(olderSuperseded, List.of());
        repository.save(newerSuperseded, List.of());
        repository.save(active, List.of());

        MaterialLineageResponse response = service.getLineage(active.id());

        assertEquals(List.of(active.id(), newerSuperseded.id(), olderSuperseded.id()), response.versions().stream()
            .map(version -> version.id())
            .toList());
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
    void rechunkActiveMaterialsSchedulesOnlyMismatchedProfilesAndBackfillsLegacyFiles() {
        MaterialProperties properties = new MaterialProperties();
        properties.setChunkProfile("structured-v1");
        InMemoryMaterialRepository repository = new InMemoryMaterialRepository();
        MaterialIndexingService indexingService = org.mockito.Mockito.mock(MaterialIndexingService.class);
        MaterialQueryService service = createService(repository, properties, indexingService);

        StoredMaterialRecord fixedText = materialRecord(
            "Pricing FAQ",
            "Alpha short sentence. Beta short sentence. Gamma short sentence.",
            "pricing-lineage",
            MaterialVersionState.ACTIVE,
            Instant.parse("2026-04-17T10:00:00Z"),
            Instant.parse("2026-04-17T10:00:00Z")
        );
        repository.save(
            fixedText,
            ChunkProfile.FIXED_V1.propertyValue(),
            List.of(new StoredMaterialChunk(0, fixedText.content(), List.of(), null, "direct-text", false)),
            List.of(new StoredMaterialSegment(0, fixedText.content(), null, "direct-text", false))
        );

        StoredMaterialRecord alreadySentence = materialRecord(
            "Policy",
            "Sentence aware policy content.",
            "policy-lineage",
            MaterialVersionState.ACTIVE,
            Instant.parse("2026-04-17T10:05:00Z"),
            Instant.parse("2026-04-17T10:05:00Z")
        );
        repository.save(
            alreadySentence,
            ChunkProfile.STRUCTURED_V1.propertyValue(),
            List.of(new StoredMaterialChunk(0, alreadySentence.content(), List.of(), null, "direct-text", false)),
            List.of(new StoredMaterialSegment(0, alreadySentence.content(), null, "direct-text", false))
        );

        StoredMaterialRecord legacyFile = new StoredMaterialRecord(
            UUID.randomUUID().toString(),
            "legacy.pdf",
            "file",
            "legacy.pdf",
            "application/pdf",
            "Страница один. Страница два.",
            "Страница один. Страница два.",
            UUID.randomUUID().toString(),
            "legacy-file-lineage",
            "ocr",
            true,
            2,
            List.of(),
            MaterialIndexingStatus.READY,
            MaterialVersionState.ACTIVE,
            null,
            null,
            Instant.parse("2026-04-17T10:10:00Z"),
            Instant.parse("2026-04-17T10:10:00Z")
        );
        repository.save(
            legacyFile,
            ChunkProfile.FIXED_V1.propertyValue(),
            List.of(
                new StoredMaterialChunk(0, "Страница один.", List.of(), 1, "ocr", true),
                new StoredMaterialChunk(1, "Страница два.", List.of(), 2, "ocr", true)
            ),
            List.of()
        );

        RechunkActiveMaterialsResponse response = service.rechunkActiveMaterials();

        assertEquals(3, response.activeCount());
        assertEquals(2, response.scheduledCount());
        assertEquals(1, response.alreadyCurrentCount());
        assertEquals(1, response.legacyBestEffortCount());
        assertEquals("structured-v1", repository.findChunkProfile(fixedText.id()));
        assertEquals("structured-v1", repository.findChunkProfile(legacyFile.id()));
        assertEquals(MaterialIndexingStatus.PENDING, repository.findById(fixedText.id()).orElseThrow().status());
        assertEquals(2, repository.findSegments(legacyFile.id()).size());
        assertEquals(1, repository.findSegments(legacyFile.id()).getFirst().page());
        assertEquals("ocr", repository.findSegments(legacyFile.id()).getFirst().extractor());
        assertTrue(repository.findSegments(legacyFile.id()).getFirst().ocrUsed());
        assertTrue(repository.findChunks(legacyFile.id()).stream().allMatch(chunk -> chunk.parserConfidence() != null));
        assertTrue(repository.findChunks(legacyFile.id()).stream().allMatch(chunk -> chunk.parserConfidence().name().equals("LOW")));
        org.mockito.Mockito.verify(indexingService).requestProcessing();
    }

    @Test
    void rechunkActiveMaterialsIsIdempotentAfterProfilesAreUpdated() {
        MaterialProperties properties = new MaterialProperties();
        properties.setChunkProfile("structured-v1");
        InMemoryMaterialRepository repository = new InMemoryMaterialRepository();
        MaterialIndexingService indexingService = org.mockito.Mockito.mock(MaterialIndexingService.class);
        MaterialQueryService service = createService(repository, properties, indexingService);

        StoredMaterialRecord active = materialRecord(
            "Pricing FAQ",
            "Alpha short sentence. Beta short sentence.",
            "pricing-lineage",
            MaterialVersionState.ACTIVE,
            Instant.parse("2026-04-17T10:00:00Z"),
            Instant.parse("2026-04-17T10:00:00Z")
        );
        repository.save(active, List.of(new StoredMaterialChunk(0, active.content(), List.of(), null, "direct-text", false)));

        RechunkActiveMaterialsResponse firstRun = service.rechunkActiveMaterials();
        RechunkActiveMaterialsResponse secondRun = service.rechunkActiveMaterials();

        assertEquals(1, firstRun.scheduledCount());
        assertEquals(0, secondRun.scheduledCount());
        assertEquals(1, secondRun.alreadyCurrentCount());
        org.mockito.Mockito.verify(indexingService).requestProcessing();
    }

    @Test
    void rechunkActiveMaterialsBatchContinuesInStableCreatedAtIdOrder() {
        MaterialProperties properties = new MaterialProperties();
        properties.setChunkProfile("structured-v1");
        InMemoryMaterialRepository repository = new InMemoryMaterialRepository();
        MaterialIndexingService indexingService = org.mockito.Mockito.mock(MaterialIndexingService.class);
        MaterialQueryService service = createService(repository, properties, indexingService);
        Instant sharedCreatedAt = Instant.parse("2026-04-17T10:00:00Z");

        StoredMaterialRecord first = materialRecord(
            "00000000-0000-0000-0000-000000000001",
            "Pricing FAQ",
            "Alpha short sentence. Beta short sentence.",
            "pricing-lineage-first",
            MaterialVersionState.ACTIVE,
            sharedCreatedAt,
            sharedCreatedAt
        );
        repository.save(
            first,
            ChunkProfile.FIXED_V1.propertyValue(),
            List.of(new StoredMaterialChunk(0, first.content(), List.of(), null, "direct-text", false)),
            List.of(new StoredMaterialSegment(0, first.content(), null, "direct-text", false))
        );

        StoredMaterialRecord second = materialRecord(
            "00000000-0000-0000-0000-000000000002",
            "Policy",
            "Already sentence aware content.",
            "pricing-lineage-second",
            MaterialVersionState.ACTIVE,
            sharedCreatedAt,
            sharedCreatedAt
        );
        repository.save(
            second,
            ChunkProfile.STRUCTURED_V1.propertyValue(),
            List.of(new StoredMaterialChunk(0, second.content(), List.of(), null, "direct-text", false)),
            List.of(new StoredMaterialSegment(0, second.content(), null, "direct-text", false))
        );

        StoredMaterialRecord third = materialRecord(
            "00000000-0000-0000-0000-000000000003",
            "Later policy",
            "Gamma short sentence. Delta short sentence.",
            "pricing-lineage-third",
            MaterialVersionState.ACTIVE,
            sharedCreatedAt.plusSeconds(60),
            sharedCreatedAt.plusSeconds(60)
        );
        repository.save(
            third,
            ChunkProfile.FIXED_V1.propertyValue(),
            List.of(new StoredMaterialChunk(0, third.content(), List.of(), null, "direct-text", false)),
            List.of(new StoredMaterialSegment(0, third.content(), null, "direct-text", false))
        );

        RechunkActiveMaterialsBatchResponse firstBatch = service.rechunkActiveMaterialsBatch(
            new RechunkActiveMaterialsBatchRequest(2, null, false)
        );

        assertEquals(3, firstBatch.totalActive());
        assertEquals(2, firstBatch.scanned());
        assertEquals(1, firstBatch.scheduled());
        assertEquals(1, firstBatch.alreadyCurrent());
        assertEquals(0, firstBatch.legacyBestEffort());
        assertEquals(List.of(first.id()), firstBatch.materialIds());
        assertTrue(firstBatch.nextCursor() != null && !firstBatch.nextCursor().isBlank());
        assertEquals("structured-v1", repository.findChunkProfile(first.id()));
        assertEquals("fixed-v1", repository.findChunkProfile(third.id()));

        RechunkActiveMaterialsBatchResponse secondBatch = service.rechunkActiveMaterialsBatch(
            new RechunkActiveMaterialsBatchRequest(2, firstBatch.nextCursor(), false)
        );

        assertEquals(3, secondBatch.totalActive());
        assertEquals(1, secondBatch.scanned());
        assertEquals(1, secondBatch.scheduled());
        assertEquals(0, secondBatch.alreadyCurrent());
        assertEquals(0, secondBatch.legacyBestEffort());
        assertEquals(List.of(third.id()), secondBatch.materialIds());
        assertEquals(null, secondBatch.nextCursor());
        assertEquals("structured-v1", repository.findChunkProfile(third.id()));
        org.mockito.Mockito.verify(indexingService, org.mockito.Mockito.times(2)).requestProcessing();
    }

    @Test
    void rechunkActiveMaterialsBatchDryRunDoesNotMutateStateOrTriggerIndexing() {
        MaterialProperties properties = new MaterialProperties();
        properties.setChunkProfile("structured-v1");
        InMemoryMaterialRepository repository = new InMemoryMaterialRepository();
        MaterialIndexingService indexingService = org.mockito.Mockito.mock(MaterialIndexingService.class);
        MaterialQueryService service = createService(repository, properties, indexingService);

        StoredMaterialRecord legacyFile = new StoredMaterialRecord(
            "00000000-0000-0000-0000-000000000010",
            "legacy.pdf",
            "file",
            "legacy.pdf",
            "application/pdf",
            "Страница один. Страница два.",
            "Страница один. Страница два.",
            UUID.randomUUID().toString(),
            "legacy-file-lineage",
            "ocr",
            true,
            2,
            List.of(),
            MaterialIndexingStatus.READY,
            MaterialVersionState.ACTIVE,
            null,
            null,
            Instant.parse("2026-04-17T10:10:00Z"),
            Instant.parse("2026-04-17T10:10:00Z")
        );
        repository.save(
            legacyFile,
            ChunkProfile.FIXED_V1.propertyValue(),
            List.of(
                new StoredMaterialChunk(0, "Страница один.", List.of(), 1, "ocr", true),
                new StoredMaterialChunk(1, "Страница два.", List.of(), 2, "ocr", true)
            ),
            List.of()
        );

        RechunkActiveMaterialsBatchResponse response = service.rechunkActiveMaterialsBatch(
            new RechunkActiveMaterialsBatchRequest(10, null, true)
        );

        assertEquals(1, response.totalActive());
        assertEquals(1, response.scanned());
        assertEquals(1, response.scheduled());
        assertEquals(0, response.alreadyCurrent());
        assertEquals(1, response.legacyBestEffort());
        assertEquals(List.of(legacyFile.id()), response.materialIds());
        assertEquals(null, response.nextCursor());
        assertEquals("fixed-v1", repository.findChunkProfile(legacyFile.id()));
        assertEquals(MaterialIndexingStatus.READY, repository.findById(legacyFile.id()).orElseThrow().status());
        assertTrue(repository.findSegments(legacyFile.id()).isEmpty());
        assertTrue(repository.findAllSearchSyncEntries().isEmpty());
        org.mockito.Mockito.verifyNoInteractions(indexingService);
    }

    @Test
    void reindexDoesNotChangeChunkProfileWhenGlobalTargetDiffers() {
        MaterialProperties properties = new MaterialProperties();
        properties.setChunkProfile("structured-v1");
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
        repository.save(
            failed,
            ChunkProfile.FIXED_V1.propertyValue(),
            List.of(new StoredMaterialChunk(0, failed.content(), List.of(), null, "direct-text", false)),
            List.of()
        );

        service.reindex(failed.id());

        assertEquals("fixed-v1", repository.findChunkProfile(failed.id()));
    }

    @Test
    void rechunkActiveMaterialsDoesNotTouchSupersededVersions() {
        MaterialProperties properties = new MaterialProperties();
        properties.setChunkProfile("structured-v1");
        InMemoryMaterialRepository repository = new InMemoryMaterialRepository();
        MaterialIndexingService indexingService = org.mockito.Mockito.mock(MaterialIndexingService.class);
        MaterialQueryService service = createService(repository, properties, indexingService);

        StoredMaterialRecord active = materialRecord(
            "Pricing FAQ",
            "2. Dispatch approval\nRegional dispatcher approves the outage window.",
            "pricing-lineage-active",
            MaterialVersionState.ACTIVE,
            Instant.parse("2026-04-17T10:00:00Z"),
            Instant.parse("2026-04-17T10:00:00Z")
        );
        StoredMaterialRecord superseded = materialRecord(
            "Pricing FAQ history",
            "2. Historical section\nOld archive entry.",
            "pricing-lineage-superseded",
            MaterialVersionState.SUPERSEDED,
            Instant.parse("2026-04-17T09:00:00Z"),
            Instant.parse("2026-04-17T09:00:00Z")
        );

        repository.save(
            active,
            ChunkProfile.FIXED_V1.propertyValue(),
            List.of(new StoredMaterialChunk(0, active.content(), List.of(), null, "direct-text", false)),
            List.of(new StoredMaterialSegment(0, active.content(), null, "direct-text", false))
        );
        repository.save(
            superseded,
            ChunkProfile.FIXED_V1.propertyValue(),
            List.of(new StoredMaterialChunk(0, superseded.content(), List.of(), null, "direct-text", false)),
            List.of(new StoredMaterialSegment(0, superseded.content(), null, "direct-text", false))
        );

        RechunkActiveMaterialsResponse response = service.rechunkActiveMaterials();

        assertEquals(1, response.activeCount());
        assertEquals(1, response.scheduledCount());
        assertEquals("structured-v1", repository.findChunkProfile(active.id()));
        assertEquals("fixed-v1", repository.findChunkProfile(superseded.id()));
        assertEquals(MaterialVersionState.SUPERSEDED, repository.findById(superseded.id()).orElseThrow().versionState());
        org.mockito.Mockito.verify(indexingService).requestProcessing();
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
        MaterialSearchSyncLifecycleService lifecycleService = TestMaterialServices.lifecycleService(
            repository,
            repository,
            repository,
            repository,
            repository
        );
        return new MaterialQueryService(
            repository,
            repository,
            properties,
            new MaterialFormatRegistry(),
            () -> OcrCapability.embeddedTextAndOcr(List.of("kaz", "rus", "eng"), 12),
            new MaterialContentSupport(properties),
            lifecycleService,
            indexingService,
            new AfterCommitExecutor()
        );
    }

    private MaterialQueryService createService(InMemoryMaterialRepository repository, MaterialProperties properties) {
        return createService(repository, properties, org.mockito.Mockito.mock(MaterialIndexingService.class));
    }

    private StoredMaterialRecord materialRecord(
        String id,
        String title,
        String content,
        String sourceKey,
        MaterialVersionState versionState,
        Instant createdAt,
        Instant updatedAt
    ) {
        return new StoredMaterialRecord(
            id,
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
            MaterialIndexingStatus.READY,
            versionState,
            null,
            null,
            createdAt,
            updatedAt
        );
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
