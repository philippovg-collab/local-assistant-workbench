package com.example.demo.service;

import com.example.demo.service.material.StoredMaterialChunk;
import com.example.demo.service.material.StoredMaterialRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.demo.error.ApplicationException;
import com.example.demo.error.ErrorType;
import com.example.demo.config.MaterialProperties;
import com.example.demo.embedding.EmbeddingClient;
import com.example.demo.model.MaterialIndexingStatus;
import com.example.demo.model.MaterialVersionState;
import com.example.demo.support.DeterministicEmbeddingClient;
import com.example.demo.support.InMemoryMaterialRepository;
import com.example.demo.support.TestMaterialServices;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;

class MaterialIndexingServiceTest {

    @Test
    void requestProcessingSchedulesUpToConfiguredWorkerCount() {
        InMemoryMaterialRepository repository = new InMemoryMaterialRepository();
        MaterialProperties properties = new MaterialProperties();
        properties.setIndexingWorkerCount(3);
        MaterialContentSupport contentSupport = new MaterialContentSupport(properties);
        MaterialSearchSyncLifecycleService lifecycleService = TestMaterialServices.lifecycleService(
            repository,
            repository,
            repository,
            repository,
            repository
        );
        CapturingExecutor executor = new CapturingExecutor();
        MaterialIndexingService service = new MaterialIndexingService(
            repository,
            repository,
            contentSupport,
            new DeterministicEmbeddingClient(),
            properties,
            lifecycleService,
            executor
        );

        savePendingRecord(repository);

        service.requestProcessing();
        service.requestProcessing();

        assertEquals(3, executor.tasks.size());
        assertEquals(3, service.activeWorkerCount());
    }

    @Test
    void rejectedExecutorLeavesPendingJobAndReleasesWorkerSlot() {
        InMemoryMaterialRepository repository = new InMemoryMaterialRepository();
        MaterialProperties properties = new MaterialProperties();
        MaterialContentSupport contentSupport = new MaterialContentSupport(properties);
        MaterialSearchSyncLifecycleService lifecycleService = TestMaterialServices.lifecycleService(
            repository,
            repository,
            repository,
            repository,
            repository
        );
        MaterialIndexingService service = new MaterialIndexingService(
            repository,
            repository,
            contentSupport,
            new DeterministicEmbeddingClient(),
            properties,
            lifecycleService,
            command -> {
                throw new RejectedExecutionException("full");
            }
        );

        StoredMaterialRecord record = savePendingRecord(repository);

        service.requestProcessing();

        assertEquals(0, service.activeWorkerCount());
        assertEquals(MaterialIndexingStatus.PENDING, repository.findById(record.id()).orElseThrow().status());
    }

    @Test
    void retriesPendingMaterialAndEventuallyMarksItReady() throws Exception {
        InMemoryMaterialRepository repository = new InMemoryMaterialRepository();
        MaterialProperties properties = new MaterialProperties();
        properties.setIndexingMaxAttempts(2);
        properties.setIndexingRetryBaseSeconds(1);
        properties.setIndexingRetryMaxSeconds(1);
        MaterialContentSupport contentSupport = new MaterialContentSupport(properties);
        MaterialSearchSyncLifecycleService lifecycleService = TestMaterialServices.lifecycleService(
            repository,
            repository,
            repository,
            repository,
            repository
        );
        MaterialIndexingService service = new MaterialIndexingService(
            repository,
            repository,
            contentSupport,
            new FlakyEmbeddingClient(),
            properties,
            lifecycleService,
            Runnable::run
        );

        StoredMaterialRecord record = savePendingRecord(repository);

        service.requestProcessing();
        assertEquals(MaterialIndexingStatus.PENDING, repository.findById(record.id()).orElseThrow().status());

        Thread.sleep(1_100L);

        service.requestProcessing();
        assertEquals(MaterialIndexingStatus.READY, repository.findById(record.id()).orElseThrow().status());
    }

    @Test
    void recoversExpiredInProgressClaimOnNextDrain() {
        InMemoryMaterialRepository repository = new InMemoryMaterialRepository();
        MaterialProperties properties = new MaterialProperties();
        properties.setIndexingLeaseSeconds(5);
        MaterialContentSupport contentSupport = new MaterialContentSupport(properties);
        MaterialSearchSyncLifecycleService lifecycleService = TestMaterialServices.lifecycleService(
            repository,
            repository,
            repository,
            repository,
            repository
        );
        MaterialIndexingService service = new MaterialIndexingService(
            repository,
            repository,
            contentSupport,
            new DeterministicEmbeddingClient(),
            properties,
            lifecycleService,
            Runnable::run
        );

        StoredMaterialRecord record = savePendingRecord(repository);
        repository.claimNextIndexing(Instant.now().minusSeconds(30)).orElseThrow();

        service.requestProcessing();

        assertEquals(MaterialIndexingStatus.READY, repository.findById(record.id()).orElseThrow().status());
    }

    private StoredMaterialRecord savePendingRecord(InMemoryMaterialRepository repository) {
        Instant now = Instant.parse("2026-04-16T10:00:00Z");
        String content = "Тариф Премиум стоит 12000 тенге.";
        StoredMaterialRecord record = new StoredMaterialRecord(
            UUID.randomUUID().toString(),
            "Pricing note",
            "text",
            null,
            "text/plain",
            content,
            content,
            UUID.randomUUID().toString(),
            "pricing-note",
            "direct-text",
            false,
            null,
            List.of(),
            MaterialIndexingStatus.PENDING,
            MaterialVersionState.ACTIVE,
            null,
            null,
            now,
            now
        );
        repository.save(record, List.of(new StoredMaterialChunk(
            0,
            content,
            List.of("тариф", "премиум", "12000"),
            null,
            "direct-text",
            false
        )));
        return record;
    }

    private static final class FlakyEmbeddingClient implements EmbeddingClient {

        private int calls = 0;

        @Override
        public float[] embed(String input) {
            throw new UnsupportedOperationException("embed() is not used by this test");
        }

        @Override
        public List<float[]> embedAll(List<String> inputs) {
            calls++;
            if (calls == 1) {
                throw new ApplicationException(
                    ErrorType.PROVIDER_UNAVAILABLE,
                    "embedding.provider_unavailable",
                    "Embedding provider is unavailable on the first attempt"
                );
            }
            return inputs.stream()
                .map(input -> new float[] { input.length(), 1.0f, 2.0f })
                .toList();
        }
    }

    private static final class CapturingExecutor implements Executor {

        private final List<Runnable> tasks = new ArrayList<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }
    }
}
