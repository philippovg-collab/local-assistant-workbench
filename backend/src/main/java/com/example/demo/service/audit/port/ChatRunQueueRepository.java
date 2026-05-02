package com.example.demo.service.audit.port;

import com.example.demo.model.ChatExecutionRequest;
import com.example.demo.model.ChatMode;
import com.example.demo.service.audit.ChatRunQueueLease;
import com.example.demo.service.audit.EnqueuedChatRun;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public interface ChatRunQueueRepository {

    EnqueuedChatRun enqueue(ChatExecutionRequest request, ChatMode mode, Instant createdAt);

    Optional<ChatRunQueueLease> claimNext(String workerId, Instant claimedAt, Duration leaseDuration);

    boolean extendLease(ChatRunQueueLease lease, Instant heartbeatAt, Instant leaseExpiresAt);

    boolean deleteQueueEntryIfOwned(ChatRunQueueLease lease);

    void deleteQueueEntry(String runId);

    RecoverySummary recoverExpiredLeases(Instant observedAt, int maxAttempts);

    boolean hasPendingRuns();

    record RecoverySummary(int requeuedCount, int abandonedCount) {
    }
}
