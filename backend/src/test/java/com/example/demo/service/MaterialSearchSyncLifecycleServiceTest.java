package com.example.demo.service;

import com.example.demo.service.material.MaterialFormatRegistry;
import com.example.demo.service.material.MaterialSearchSyncQueueEntry;
import com.example.demo.service.material.OcrCapability;
import com.example.demo.service.material.StoredEmbeddedMaterialChunk;
import com.example.demo.service.material.StoredMaterialChunk;
import com.example.demo.service.material.StoredMaterialRecord;
import com.example.demo.service.material.port.DocumentTextExtractor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import com.example.demo.model.MaterialIndexingStatus;
import com.example.demo.model.MaterialVersionState;
import com.example.demo.support.DeterministicEmbeddingClient;
import com.example.demo.support.InMemoryMaterialRepository;
import com.example.demo.support.TestLexicalRoutingSupport;
import com.example.demo.support.TestMaterialServices;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MaterialSearchSyncLifecycleServiceTest {

    @Test
    void enqueuesAffectedMaterialIdsWhenNewVersionReplacesActiveReadyVersion() {
        TestRig rig = createRig();
        String previousActiveId = rig.materialService().saveText("Pricing FAQ", "Старая цена: 9000 тенге.").id();
        rig.repository().clearSearchSyncQueue();

        StoredMaterialRecord created = rig.repository().findById(
            rig.materialService().saveText("Pricing FAQ", "Новая цена: 12000 тенге.").id()
        ).orElseThrow();

        assertEquals(
            sortedIds(previousActiveId, created.id()),
            queuedMaterialIds(rig.repository().findAllSearchSyncEntries())
        );
    }

    @Test
    void enqueuesAffectedMaterialIdsWhenSupersededReadyVersionIsReactivated() {
        TestRig rig = createRig();
        String reactivatedId = rig.materialService().saveText("Pricing FAQ", "Старая цена: 9000 тенге.").id();
        String currentActiveId = rig.materialService().saveText("Pricing FAQ", "Текущая цена: 12000 тенге.").id();
        rig.repository().clearSearchSyncQueue();

        StoredMaterialRecord reactivated = rig.repository().findById(
            rig.materialService().saveText("Pricing FAQ", "Старая цена: 9000 тенге.").id()
        ).orElseThrow();

        assertEquals(
            sortedIds(currentActiveId, reactivatedId),
            queuedMaterialIds(rig.repository().findAllSearchSyncEntries())
        );
        assertEquals(reactivatedId, reactivated.id());
    }

    @Test
    void keepsSingleQueueEntryWhenPartialReadyMaterialIsReindexed() {
        TestRig rig = createRig();
        StoredMaterialRecord material = saveMaterial(
            rig,
            "Pricing FAQ",
            "Частично готовый индекс содержит цену 12000 тенге.",
            MaterialVersionState.ACTIVE,
            MaterialIndexingStatus.PARTIAL_READY,
            Instant.parse("2026-04-17T10:00:00Z")
        );

        rig.materialService().reindex(material.id());

        assertEquals(
            List.of(material.id()),
            queuedMaterialIds(rig.repository().findAllSearchSyncEntries())
        );
    }

    @Test
    void enqueuesDeletedAndPromotedMaterialIdsWhenDeletingActiveMaterial() {
        TestRig rig = createRig();
        StoredMaterialRecord promoted = saveMaterial(
            rig,
            "Pricing FAQ",
            "Старая цена: 9000 тенге.",
            MaterialVersionState.SUPERSEDED,
            MaterialIndexingStatus.READY,
            Instant.parse("2026-04-17T10:00:00Z")
        );
        StoredMaterialRecord active = saveMaterial(
            rig,
            "Pricing FAQ",
            "Новая цена: 12000 тенге.",
            MaterialVersionState.ACTIVE,
            MaterialIndexingStatus.READY,
            Instant.parse("2026-04-17T10:05:00Z")
        );

        rig.materialService().delete(active.id());

        assertEquals(
            sortedIds(active.id(), promoted.id()),
            queuedMaterialIds(rig.repository().findAllSearchSyncEntries())
        );
    }

    @Test
    void leavesQueueEmptyWhenDeletingSupersededVersion() {
        TestRig rig = createRig();
        saveMaterial(
            rig,
            "Pricing FAQ",
            "Старая цена: 9000 тенге.",
            MaterialVersionState.SUPERSEDED,
            MaterialIndexingStatus.READY,
            Instant.parse("2026-04-17T10:00:00Z")
        );
        StoredMaterialRecord active = saveMaterial(
            rig,
            "Pricing FAQ",
            "Новая цена: 12000 тенге.",
            MaterialVersionState.ACTIVE,
            MaterialIndexingStatus.READY,
            Instant.parse("2026-04-17T10:05:00Z")
        );

        rig.materialService().delete(rig.repository().findAll().stream()
            .filter(record -> record.versionState() == MaterialVersionState.SUPERSEDED)
            .findFirst()
            .orElseThrow()
            .id());

        assertTrue(rig.repository().findById(active.id()).isPresent());
        assertTrue(rig.repository().findAllSearchSyncEntries().isEmpty());
    }

    @Test
    void rereadsVersionStateAfterLockBeforeDecidingPromotionOnDelete() {
        LockMutatingRepository repository = new LockMutatingRepository();
        TestRig rig = createRig(repository);
        StoredMaterialRecord olderSuperseded = saveMaterial(
            rig,
            "Pricing FAQ",
            "Самая старая цена: 9000 тенге.",
            MaterialVersionState.SUPERSEDED,
            MaterialIndexingStatus.READY,
            Instant.parse("2026-04-17T09:00:00Z")
        );
        StoredMaterialRecord staleFormerActive = saveMaterial(
            rig,
            "Pricing FAQ",
            "Промежуточная цена: 11000 тенге.",
            MaterialVersionState.ACTIVE,
            MaterialIndexingStatus.READY,
            Instant.parse("2026-04-17T10:00:00Z")
        );
        StoredMaterialRecord newerActive = saveMaterial(
            rig,
            "Pricing FAQ",
            "Новая цена: 12000 тенге.",
            MaterialVersionState.SUPERSEDED,
            MaterialIndexingStatus.READY,
            Instant.parse("2026-04-17T10:30:00Z")
        );
        repository.clearSearchSyncQueue();
        repository.onNextLock(() -> {
            repository.updateVersionState(
                staleFormerActive.id(),
                MaterialVersionState.SUPERSEDED,
                newerActive.id(),
                "material.concurrent_newer_active_version",
                Instant.parse("2026-04-17T10:45:00Z")
            );
            repository.updateVersionState(
                newerActive.id(),
                MaterialVersionState.ACTIVE,
                null,
                null,
                Instant.parse("2026-04-17T10:45:00Z")
            );
        });

        rig.materialService().delete(staleFormerActive.id());

        assertTrue(repository.findById(staleFormerActive.id()).isEmpty());
        assertEquals(MaterialVersionState.SUPERSEDED, repository.findById(olderSuperseded.id()).orElseThrow().versionState());
        assertEquals(MaterialVersionState.ACTIVE, repository.findById(newerActive.id()).orElseThrow().versionState());
        assertEquals(1, repository.countActiveMaterials());
        assertTrue(repository.findAllSearchSyncEntries().isEmpty());
    }

    private TestRig createRig() {
        return createRig(new InMemoryMaterialRepository());
    }

    private TestRig createRig(InMemoryMaterialRepository repository) {
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
        DeterministicEmbeddingClient embeddingClient = new DeterministicEmbeddingClient();
        MaterialContentSupport contentSupport = new MaterialContentSupport(properties);
        MaterialSearchSyncLifecycleService lifecycleService = TestMaterialServices.lifecycleService(
            repository,
            repository,
            repository,
            repository,
            repository
        );
        MaterialIndexingService indexingService = new MaterialIndexingService(
            repository,
            repository,
            contentSupport,
            embeddingClient,
            properties,
            lifecycleService,
            Runnable::run
        );
        MaterialService materialService = new MaterialService(
            new MaterialQueryService(
                repository,
                repository,
                properties,
                formatRegistry,
                () -> OcrCapability.embeddedTextAndOcr(List.of("kaz", "rus", "eng"), 12),
                contentSupport,
                lifecycleService,
                indexingService,
                new AfterCommitExecutor(),
                new MaterialMetadataResolver(new com.example.demo.support.NoopReferenceDataRepository())
            ),
            new MaterialIngestionService(
                repository,
                repository,
                extractor,
                properties,
                contentSupport,
                new MaterialMetadataResolver(new com.example.demo.support.NoopReferenceDataRepository()),
                lifecycleService,
                indexingService,
                new AfterCommitExecutor()
            ),
            TestMaterialServices.retrievalService(
                repository,
                repository,
                repository,
                TestLexicalRoutingSupport.productionRouter(repository, new RagProperties(), List.of(repository)),
                embeddingClient,
                new RagProperties(),
                new HybridChunkRanker(),
                contentSupport
            )
        );
        return new TestRig(repository, materialService, contentSupport, embeddingClient);
    }

    private static StoredMaterialRecord saveMaterial(
        TestRig rig,
        String title,
        String content,
        MaterialVersionState versionState,
        MaterialIndexingStatus status,
        Instant timestamp
    ) {
        String normalizedContent = rig.contentSupport().normalizeForHash(content);
        String sourceKey = rig.contentSupport().buildLineageIdentity("text", title, null, content).sourceKey();
        StoredMaterialRecord record = new StoredMaterialRecord(
            UUID.randomUUID().toString(),
            title,
            "text",
            null,
            "text/plain",
            content,
            normalizedContent,
            rig.contentSupport().sha256(normalizedContent),
            sourceKey,
            "direct-text",
            false,
            null,
            List.of(new StoredMaterialChunk(0, content, List.of(), null, "direct-text", false)),
            status,
            versionState,
            status == MaterialIndexingStatus.PARTIAL_READY ? "material.partial_ocr" : null,
            status == MaterialIndexingStatus.PARTIAL_READY ? "Partial OCR warning" : null,
            timestamp,
            timestamp
        );
        rig.repository().save(record, record.chunks());
        if (status == MaterialIndexingStatus.READY || status == MaterialIndexingStatus.PARTIAL_READY) {
            rig.repository().markIndexingReady(
                record.id(),
                List.of(new StoredEmbeddedMaterialChunk(
                    0,
                    content,
                    null,
                    "direct-text",
                    false,
                    rig.embeddingClient().embed(content)
                )),
                status,
                record.statusReasonCode(),
                record.statusReasonMessage(),
                timestamp
            );
        }
        return record;
    }

    private List<String> queuedMaterialIds(List<MaterialSearchSyncQueueEntry> entries) {
        return entries.stream()
            .map(MaterialSearchSyncQueueEntry::materialId)
            .sorted()
            .toList();
    }

    private static List<String> sortedIds(String... materialIds) {
        return java.util.Arrays.stream(materialIds)
            .sorted()
            .toList();
    }

    private record TestRig(
        InMemoryMaterialRepository repository,
        MaterialService materialService,
        MaterialContentSupport contentSupport,
        EmbeddingClient embeddingClient
    ) {
    }

    private static final class LockMutatingRepository extends InMemoryMaterialRepository {

        private Runnable nextLockAction;

        void onNextLock(Runnable action) {
            this.nextLockAction = action;
        }

        @Override
        public synchronized void lockLineage(String sourceKey) {
            Runnable action = nextLockAction;
            nextLockAction = null;
            if (action != null) {
                action.run();
            }
            super.lockLineage(sourceKey);
        }
    }
}
