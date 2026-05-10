package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.config.MaterialProperties;
import com.example.demo.config.RolloutProperties;
import com.example.demo.embedding.EmbeddingClient;
import com.example.demo.model.DocumentStatus;
import com.example.demo.model.DocumentType;
import com.example.demo.model.MaterialEnrichmentState;
import com.example.demo.model.MaterialLanguageCode;
import com.example.demo.model.MaterialMetadataInput;
import com.example.demo.model.MaterialSummary;
import com.example.demo.model.MaterialVersionState;
import com.example.demo.service.material.MaterialAutoTaggingLease;
import com.example.demo.service.material.MaterialAutoTaggingStatus;
import com.example.demo.service.material.MaterialAutoTaggingTask;
import com.example.demo.service.material.MaterialMetadataHints;
import com.example.demo.support.DeterministicEmbeddingClient;
import com.example.demo.support.InMemoryMaterialRepository;
import com.example.demo.support.NoopReferenceDataRepository;
import com.example.demo.support.TestMaterialServices;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;

class MaterialAutoTaggingLifecycleServiceTest {

    @Test
    void executorRejectionAfterEnqueueLeavesTaskPendingAndObservable() {
        StubAutoTaggingService autoTaggingService = new StubAutoTaggingService(List.of("relay protection"));
        TestRig rig = createRig(
            autoTaggingService,
            command -> {
                throw new RejectedExecutionException("queue full");
            },
            new MaterialProperties()
        );

        MaterialSummary summary = rig.ingestionService().saveText(
            "Relay note",
            "Материал описывает релейную защиту трансформатора.",
            null
        );

        assertEquals(MaterialEnrichmentState.PENDING, summary.enrichmentStatus().status());
        MaterialAutoTaggingTask task = rig.repository().findLatestByMaterialId(summary.id()).orElseThrow();
        assertEquals(MaterialAutoTaggingStatus.PENDING, task.status());
        assertTrue(autoTaggingService.requests().isEmpty());
    }

    @Test
    void workerAppliesAutoTagsWithoutOverwritingManualTags() {
        StubAutoTaggingService autoTaggingService = new StubAutoTaggingService(
            List.of("manual-grid", "relay protection", "transformer automation")
        );
        TestRig rig = createRig(autoTaggingService, Runnable::run, new MaterialProperties());

        MaterialSummary summary = rig.ingestionService().saveText(
            "Relay note",
            "Материал описывает релейную защиту трансформатора.",
            new MaterialMetadataInput(
                DocumentType.REPORT,
                "general",
                DocumentStatus.ACTIVE,
                null,
                null,
                MaterialLanguageCode.RU,
                List.of("manual-grid"),
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

        assertEquals(MaterialEnrichmentState.PENDING, summary.enrichmentStatus().status());
        MaterialAutoTaggingTask task = rig.repository().findLatestByMaterialId(summary.id()).orElseThrow();
        assertEquals(MaterialAutoTaggingStatus.DONE, task.status());
        assertEquals(MaterialAutoTaggingLifecycleService.RESULT_APPLIED, task.resultCode());
        assertEquals(List.of("manual-grid"), rig.repository().findById(summary.id()).orElseThrow().metadata().manualTags());
        assertEquals(
            List.of("relay protection", "transformer automation"),
            rig.repository().findById(summary.id()).orElseThrow().metadata().autoTags()
        );
    }

    @Test
    void providerFailureBecomesFailedTaskStateWhenAttemptsAreExhausted() {
        MaterialProperties properties = new MaterialProperties();
        properties.setAutoTaggingMaxAttempts(1);
        FailingAutoTaggingService autoTaggingService = new FailingAutoTaggingService();
        TestRig rig = createRig(autoTaggingService, Runnable::run, properties);

        MaterialSummary summary = rig.ingestionService().saveText(
            "Relay note",
            "Материал описывает релейную защиту трансформатора.",
            null
        );

        MaterialAutoTaggingTask task = rig.repository().findLatestByMaterialId(summary.id()).orElseThrow();
        assertEquals(MaterialAutoTaggingStatus.FAILED, task.status());
        assertEquals(MaterialAutoTaggingLifecycleService.FAILURE_PROVIDER, task.failureCode());
        assertEquals(MaterialEnrichmentState.FAILED, rig.autoTaggingLifecycleService().statusForMaterial(summary.id()).status());
    }

    @Test
    void providerFailureSchedulesRetryBeforeAttemptsAreExhausted() {
        FailingAutoTaggingService autoTaggingService = new FailingAutoTaggingService();
        TestRig rig = createRig(autoTaggingService, Runnable::run, new MaterialProperties());

        MaterialSummary summary = rig.ingestionService().saveText(
            "Relay note",
            "Материал описывает релейную защиту трансформатора.",
            null
        );

        MaterialAutoTaggingTask task = rig.repository().findLatestByMaterialId(summary.id()).orElseThrow();
        assertEquals(MaterialAutoTaggingStatus.PENDING, task.status());
        assertEquals(1, task.attemptCount());
        assertEquals(MaterialAutoTaggingLifecycleService.FAILURE_PROVIDER, task.failureCode());
        assertEquals(MaterialEnrichmentState.PENDING, rig.autoTaggingLifecycleService().statusForMaterial(summary.id()).status());
        assertTrue(task.nextRetryAt() != null);
    }

    @Test
    void staleContentHashTaskCompletesWithoutCallingLlmOrMutatingMetadata() {
        StubAutoTaggingService autoTaggingService = new StubAutoTaggingService(List.of("relay protection"));
        TestRig rig = createRig(
            autoTaggingService,
            command -> {
                throw new RejectedExecutionException("queue full");
            },
            new MaterialProperties()
        );
        MaterialSummary summary = rig.ingestionService().saveText(
            "Relay note",
            "Материал описывает релейную защиту трансформатора.",
            null
        );

        rig.repository().updateVersionState(
            summary.id(),
            MaterialVersionState.SUPERSEDED,
            "00000000-0000-0000-0000-000000000002",
            "test.superseded",
            Instant.now()
        );
        MaterialAutoTaggingLease lease = rig.repository().claimNext(Instant.now()).orElseThrow();
        rig.autoTaggingLifecycleService().processTask(lease.task());

        MaterialAutoTaggingTask task = rig.repository().findLatestByMaterialId(summary.id()).orElseThrow();
        assertEquals(MaterialAutoTaggingStatus.DONE, task.status());
        assertEquals(MaterialAutoTaggingLifecycleService.RESULT_SKIPPED_STALE, task.resultCode());
        assertTrue(autoTaggingService.requests().isEmpty());
        assertTrue(rig.repository().findById(summary.id()).orElseThrow().metadata().autoTags().isEmpty());
    }

    @Test
    void expiredRunningClaimReturnsToPendingForRestartRecovery() {
        InMemoryMaterialRepository repository = new InMemoryMaterialRepository();
        Instant now = Instant.parse("2026-05-09T10:00:00Z");
        MaterialAutoTaggingTask task = repository.enqueue(
            "00000000-0000-0000-0000-000000000001",
            "hash-1",
            now
        );

        MaterialAutoTaggingLease lease = repository.claimNext(now.plusSeconds(1)).orElseThrow();
        assertEquals(task.id(), lease.task().id());
        repository.resetExpiredClaims(now.plusSeconds(2), now.plusSeconds(3));

        MaterialAutoTaggingTask recovered = repository.findLatestByMaterialId(task.materialId()).orElseThrow();
        assertEquals(MaterialAutoTaggingStatus.PENDING, recovered.status());
        assertTrue(repository.hasPending(now.plusSeconds(3)));
    }

    private TestRig createRig(
        MaterialAutoTaggingService autoTaggingService,
        Executor autoTaggingExecutor,
        MaterialProperties properties
    ) {
        InMemoryMaterialRepository repository = new InMemoryMaterialRepository();
        MaterialContentSupport contentSupport = new MaterialContentSupport(properties);
        MaterialSearchSyncLifecycleService lifecycleService = TestMaterialServices.lifecycleService(
            repository,
            repository,
            repository,
            repository,
            repository
        );
        AfterCommitExecutor afterCommitExecutor = new AfterCommitExecutor();
        EmbeddingClient embeddingClient = new DeterministicEmbeddingClient();
        MaterialIndexingService indexingService = new MaterialIndexingService(
            repository,
            repository,
            contentSupport,
            embeddingClient,
            properties,
            lifecycleService,
            Runnable::run
        );
        RolloutProperties rolloutProperties = RolloutProperties.enabledForTests();
        MaterialAutoTaggingLifecycleService autoTaggingLifecycleService = new MaterialAutoTaggingLifecycleService(
            repository,
            repository,
            autoTaggingService,
            lifecycleService,
            indexingService,
            afterCommitExecutor,
            rolloutProperties
        );
        MaterialAutoTaggingWorkerService autoTaggingWorkerService = new MaterialAutoTaggingWorkerService(
            repository,
            autoTaggingLifecycleService,
            properties,
            autoTaggingExecutor
        );
        MaterialIngestionService ingestionService = new MaterialIngestionService(
            repository,
            repository,
            null,
            properties,
            contentSupport,
            new MaterialMetadataResolver(new NoopReferenceDataRepository()),
            lifecycleService,
            indexingService,
            afterCommitExecutor,
            rolloutProperties,
            autoTaggingLifecycleService,
            autoTaggingWorkerService
        );
        return new TestRig(repository, ingestionService, autoTaggingLifecycleService);
    }

    private record TestRig(
        InMemoryMaterialRepository repository,
        MaterialIngestionService ingestionService,
        MaterialAutoTaggingLifecycleService autoTaggingLifecycleService
    ) {
    }

    private static class StubAutoTaggingService extends MaterialAutoTaggingService {

        private final List<TaggingRequest> requests = new ArrayList<>();
        private final List<String> tags;

        StubAutoTaggingService(List<String> tags) {
            super(null, null, new MaterialProperties());
            this.tags = tags == null ? List.of() : List.copyOf(tags);
        }

        @Override
        public List<String> suggestTags(TaggingRequest request) {
            requests.add(request);
            return tags;
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        List<TaggingRequest> requests() {
            return requests;
        }
    }

    private static final class FailingAutoTaggingService extends StubAutoTaggingService {

        FailingAutoTaggingService() {
            super(List.of());
        }

        @Override
        public List<String> suggestTags(TaggingRequest request) {
            requests().add(request);
            throw new IllegalStateException("provider down");
        }
    }
}
