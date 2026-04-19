package com.example.demo.service;

import com.example.demo.infrastructure.material.MaterialSearchSyncQueueRepository;
import com.example.demo.infrastructure.material.MaterialSearchableSnapshotRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.search-sync", name = "enabled", havingValue = "true")
public class SearchSyncRecoveryService {

    private final MaterialSearchSyncQueueRepository queueRepository;
    private final MaterialSearchableSnapshotRepository searchableSnapshotRepository;
    private final ElasticsearchIndexAdminService indexAdminService;
    private final ElasticsearchIndexSyncService searchSyncService;
    private final ElasticsearchHealthService healthService;
    private final Clock clock;
    private final Sleeper sleeper;

    @Autowired
    public SearchSyncRecoveryService(
        MaterialSearchSyncQueueRepository queueRepository,
        MaterialSearchableSnapshotRepository searchableSnapshotRepository,
        ElasticsearchIndexAdminService indexAdminService,
        ElasticsearchIndexSyncService searchSyncService,
        ElasticsearchHealthService healthService
    ) {
        this(
            queueRepository,
            searchableSnapshotRepository,
            indexAdminService,
            searchSyncService,
            healthService,
            Clock.systemUTC(),
            duration -> Thread.sleep(duration.toMillis())
        );
    }

    SearchSyncRecoveryService(
        MaterialSearchSyncQueueRepository queueRepository,
        MaterialSearchableSnapshotRepository searchableSnapshotRepository,
        ElasticsearchIndexAdminService indexAdminService,
        ElasticsearchIndexSyncService searchSyncService,
        ElasticsearchHealthService healthService,
        Clock clock,
        Sleeper sleeper
    ) {
        this.queueRepository = queueRepository;
        this.searchableSnapshotRepository = searchableSnapshotRepository;
        this.indexAdminService = indexAdminService;
        this.searchSyncService = searchSyncService;
        this.healthService = healthService;
        this.clock = clock;
        this.sleeper = sleeper;
    }

    public RequeueSummary requeueFailedEvents() {
        return new RequeueSummary(queueRepository.requeueFailedSearchSyncEntries(clock.instant()));
    }

    public ReplaySummary enqueueReplayForAllSearchableActiveMaterials() {
        List<String> searchableMaterialIds = searchableSnapshotRepository.findAllSearchableMaterialIds();
        if (searchableMaterialIds.isEmpty()) {
            return new ReplaySummary(0);
        }

        Instant now = clock.instant();
        queueRepository.enqueueMaterialsForSync(searchableMaterialIds, now);
        return new ReplaySummary(searchableMaterialIds.size());
    }

    public RebuildSummary rebuildCurrentWriteIndex() {
        String writeIndexName = ensureWriteIndexAvailable();
        long clearedDocumentCount = indexAdminService.clearCurrentWriteIndex();
        RequeueSummary requeueSummary = requeueFailedEvents();
        ReplaySummary replaySummary = enqueueReplayForAllSearchableActiveMaterials();
        return new RebuildSummary(
            writeIndexName,
            clearedDocumentCount,
            requeueSummary.requeuedCount(),
            replaySummary.enqueuedCount()
        );
    }

    public RequeueRunSummary recoverFailedEventsAndWait(Duration timeout, Duration pollInterval) {
        ensureWriteIndexAvailable();
        RequeueSummary requeueSummary = requeueFailedEvents();
        searchSyncService.requestProcessing();
        WaitSummary waitSummary = waitForRecoveryCompletion(timeout, pollInterval);
        return new RequeueRunSummary(requeueSummary.requeuedCount(), waitSummary);
    }

    public RebuildRunSummary rebuildCurrentWriteIndexAndWait(Duration timeout, Duration pollInterval) {
        RebuildSummary rebuildSummary = rebuildCurrentWriteIndex();
        searchSyncService.requestProcessing();
        WaitSummary waitSummary = waitForRecoveryCompletion(timeout, pollInterval);
        return new RebuildRunSummary(
            rebuildSummary.writeIndexName(),
            rebuildSummary.clearedDocumentCount(),
            rebuildSummary.requeuedFailedCount(),
            rebuildSummary.replayEnqueuedCount(),
            waitSummary
        );
    }

    public WaitSummary waitForRecoveryCompletion(Duration timeout, Duration pollInterval) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Recovery timeout must be positive");
        }
        if (pollInterval == null || pollInterval.isZero() || pollInterval.isNegative()) {
            throw new IllegalArgumentException("Recovery poll interval must be positive");
        }

        Instant startedAt = clock.instant();
        Instant deadline = startedAt.plus(timeout);
        while (true) {
            MaterialSearchSyncQueueRepository.SearchSyncQueueSnapshot queueSnapshot =
                queueRepository.getSearchSyncQueueSnapshot();
            if (queueSnapshot.pendingCount() == 0
                && queueSnapshot.inProgressCount() == 0
                && queueSnapshot.failedCount() == 0) {
                return new WaitSummary(
                    Duration.between(startedAt, clock.instant()).toMillis(),
                    queueSnapshot.pendingCount(),
                    queueSnapshot.inProgressCount(),
                    queueSnapshot.failedCount(),
                    healthService.currentHealth().lastSuccessfulSyncAt()
                );
            }

            if (queueSnapshot.failedCount() > 0) {
                throw new IllegalStateException(
                    "Elasticsearch recovery did not converge because FAILED search-sync events remain: "
                        + queueSnapshot.failedCount()
                );
            }

            if (!clock.instant().isBefore(deadline)) {
                throw new IllegalStateException(
                    "Timed out waiting for Elasticsearch recovery completion: pending="
                        + queueSnapshot.pendingCount()
                        + ", inProgress="
                        + queueSnapshot.inProgressCount()
                        + ", failed="
                        + queueSnapshot.failedCount()
                );
            }

            sleep(pollInterval);
        }
    }

    private void sleep(Duration duration) {
        try {
            sleeper.sleep(duration);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for Elasticsearch recovery completion", exception);
        }
    }

    private String ensureWriteIndexAvailable() {
        try {
            return indexAdminService.currentWriteIndex();
        } catch (IllegalStateException exception) {
            if (!exception.getMessage().contains("is not configured")) {
                throw exception;
            }
            indexAdminService.prepareConfiguredWriteIndex();
            return indexAdminService.currentWriteIndex();
        }
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    public record RequeueSummary(int requeuedCount) {
    }

    public record ReplaySummary(int enqueuedCount) {
    }

    public record RebuildSummary(
        String writeIndexName,
        long clearedDocumentCount,
        int requeuedFailedCount,
        int replayEnqueuedCount
    ) {
    }

    public record WaitSummary(
        long waitedMillis,
        int pendingCount,
        int inProgressCount,
        int failedCount,
        Instant lastSuccessfulSyncAt
    ) {
    }

    public record RequeueRunSummary(int requeuedFailedCount, WaitSummary waitSummary) {
    }

    public record RebuildRunSummary(
        String writeIndexName,
        long clearedDocumentCount,
        int requeuedFailedCount,
        int replayEnqueuedCount,
        WaitSummary waitSummary
    ) {
    }
}
