package com.example.demo.service.material.port;

import com.example.demo.service.material.MaterialIndexingLease;
import com.example.demo.service.material.StoredEmbeddedMaterialChunk;
import com.example.demo.service.material.StoredMaterialRecord;

import com.example.demo.model.MaterialIndexingStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MaterialIndexingQueueRepository {

    StoredMaterialRecord markIndexingPending(String materialId, String reasonCode, String reasonMessage, Instant updatedAt);

    void markIndexingReady(
        String materialId,
        List<StoredEmbeddedMaterialChunk> chunks,
        MaterialIndexingStatus status,
        String reasonCode,
        String reasonMessage,
        Instant updatedAt
    );

    void markIndexingFailed(String materialId, String code, String message, Instant updatedAt);

    void rescheduleIndexing(String materialId, String code, String message, Instant updatedAt, Instant nextRetryAt);

    int markActiveMaterialsIndexingPending(String reasonCode, String reasonMessage, Instant updatedAt);

    void resetExpiredIndexingClaims(Instant staleBefore, Instant now);

    Optional<MaterialIndexingLease> claimNextIndexing(Instant now);

    boolean hasPendingIndexing(Instant now);

    IndexingQueueSnapshot getIndexingQueueSnapshot();

    record IndexingQueueSnapshot(
        int pendingCount,
        int inProgressCount,
        int failedCount,
        Instant nextRetryAt,
        Instant oldestPendingAt,
        Instant oldestInProgressAt
    ) {
        public IndexingQueueSnapshot(
            int pendingCount,
            int inProgressCount,
            int failedCount,
            Instant nextRetryAt
        ) {
            this(pendingCount, inProgressCount, failedCount, nextRetryAt, null, null);
        }
    }
}
