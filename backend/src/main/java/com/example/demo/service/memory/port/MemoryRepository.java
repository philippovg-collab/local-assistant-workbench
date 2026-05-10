package com.example.demo.service.memory.port;

import com.example.demo.model.MemoryEntryAction;
import com.example.demo.model.MemoryEntryResponse;
import com.example.demo.model.MemoryEntryStatus;
import com.example.demo.service.memory.MemoryEntryDraft;
import com.example.demo.service.memory.MemoryEntryQuery;
import com.example.demo.service.memory.MemoryExtractionJob;
import com.example.demo.service.memory.MemoryExtractionLease;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MemoryRepository {

    List<MemoryEntryResponse> listEntries(MemoryEntryQuery query);

    Optional<MemoryEntryResponse> findEntry(String entryId);

    MemoryEntryResponse createEntry(MemoryEntryDraft draft, String actor, String reason, Instant now);

    MemoryEntryResponse createCandidate(MemoryEntryDraft draft, Instant now);

    default MemoryEntryResponse createCandidate(
        MemoryEntryDraft draft,
        Instant now,
        MemoryExtractionLease lease
    ) {
        return createCandidate(draft, now);
    }

    Optional<MemoryEntryResponse> findActiveByKey(MemoryEntryDraft draft);

    MemoryEntryResponse updateEntry(String entryId, MemoryEntryDraft draft, String actor, String reason, Instant now);

    MemoryEntryResponse changeStatus(
        String entryId,
        MemoryEntryStatus expectedStatus,
        MemoryEntryStatus nextStatus,
        MemoryEntryAction action,
        String actor,
        String reason,
        Instant now
    );

    MemoryEntryResponse setPinned(String entryId, boolean pinned, String actor, String reason, Instant now);

    MemoryEntryResponse softDelete(String entryId, String actor, String reason, Instant now);

    List<MemoryEntryResponse> selectApprovedForContext(String workspaceKey, String projectKey, int limit);

    MemoryExtractionJob enqueueExtractionJob(String conversationId, String runId, int turnNo, Instant now);

    Optional<MemoryExtractionLease> claimNextExtractionJob(String workerId, Instant now, Duration leaseDuration);

    void resetExpiredExtractionLeases(Instant now);

    void markExtractionDone(String jobId, Instant now);

    default boolean markExtractionDone(MemoryExtractionLease lease, Instant now) {
        if (lease == null || lease.job() == null) {
            return false;
        }
        markExtractionDone(lease.job().id(), now);
        return true;
    }

    void markExtractionRetry(String jobId, String failureCode, String failureMessage, Instant now, Instant nextRetryAt);

    default boolean markExtractionRetry(
        MemoryExtractionLease lease,
        String failureCode,
        String failureMessage,
        Instant now,
        Instant nextRetryAt
    ) {
        if (lease == null || lease.job() == null) {
            return false;
        }
        markExtractionRetry(lease.job().id(), failureCode, failureMessage, now, nextRetryAt);
        return true;
    }

    void markExtractionFailed(String jobId, String failureCode, String failureMessage, Instant now);

    default boolean markExtractionFailed(
        MemoryExtractionLease lease,
        String failureCode,
        String failureMessage,
        Instant now
    ) {
        if (lease == null || lease.job() == null) {
            return false;
        }
        markExtractionFailed(lease.job().id(), failureCode, failureMessage, now);
        return true;
    }

    boolean hasPendingExtractionJobs(Instant now);

    MemoryJobHealth memoryJobHealth(Instant now);

    void assertMemoryStoreReadable();

    int purgeRejected(Instant cutoff, int batchSize);

    int purgeDeleted(Instant cutoff, int batchSize);

    int purgeCompletedExtractionJobs(Instant cutoff, int batchSize);

    record MemoryJobHealth(int failedCount, int stuckCount) {
    }
}
