package com.example.demo.service.context.port;

import com.example.demo.service.context.ContextRetentionCleanupResult;
import java.time.Instant;

public interface ContextMaintenanceRepository {

    void assertConversationStoreReadable();

    void assertContextSnapshotStoreReadable();

    void assertStickyStateStoreReadable();

    void assertRetrievalResolutionStoreReadable();

    void assertMemoryStoreReadable();

    SummaryJobHealth summaryJobHealth(Instant now);

    MemoryJobHealth memoryJobHealth(Instant now);

    ContextRetentionCleanupResult deleteExpiredContextArtifacts(
        Instant snapshotCutoff,
        Instant summaryJobCutoff,
        Instant memoryRejectedCutoff,
        Instant memoryDeletedCutoff,
        Instant memoryJobCutoff,
        Instant deletedConversationCutoff,
        Instant emptyConversationCutoff,
        int keepLatestSnapshotsPerConversation,
        int batchSize
    );

    record SummaryJobHealth(int failedCount, int stuckCount) {
    }

    record MemoryJobHealth(int failedCount, int stuckCount) {
    }
}
