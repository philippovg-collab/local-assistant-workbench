package com.example.demo.infrastructure.material;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface MaterialSearchSyncQueueRepository {

    void enqueueMaterialsForSync(Collection<String> materialIds, Instant requestedAt);

    void enqueueMaterialsForSync(
        Collection<String> materialIds,
        SearchSyncOperationType operationType,
        Instant requestedAt
    );

    List<MaterialSearchSyncQueueEntry> findAllSearchSyncEntries();

    int requeueFailedSearchSyncEntries(Instant now);

    void resetExpiredSearchSyncClaims(Instant staleBefore, Instant now);

    List<MaterialSearchSyncQueueEntry> claimNextSearchSyncBatch(Instant now, int limit);

    boolean hasPendingSearchSyncEvents(Instant now);

    void completeSearchSyncEntry(String materialId, Instant claimedAt, Instant now);

    void markSearchSyncEntryForRetry(
        String materialId,
        Instant claimedAt,
        String errorCode,
        String errorMessage,
        Instant now,
        Instant nextAttemptAt
    );

    void markSearchSyncEntryFailed(
        String materialId,
        Instant claimedAt,
        String errorCode,
        String errorMessage,
        Instant now
    );

    SearchSyncQueueSnapshot getSearchSyncQueueSnapshot();

    record SearchSyncQueueSnapshot(
        int pendingCount,
        int inProgressCount,
        int failedCount,
        Instant nextRetryAt,
        Instant oldestOutstandingAt
    ) {
    }
}
