package com.example.demo.service.context;

public record ContextRetentionCleanupResult(
    int deletedSnapshots,
    int deletedSummaryJobs,
    int purgedRejectedMemoryEntries,
    int purgedDeletedMemoryEntries,
    int deletedMemoryJobs,
    int purgedSoftDeletedConversations,
    int purgedEmptyConversations
) {
    public static ContextRetentionCleanupResult empty() {
        return new ContextRetentionCleanupResult(0, 0, 0, 0, 0, 0, 0);
    }

    public int totalDeleted() {
        return deletedSnapshots
            + deletedSummaryJobs
            + purgedRejectedMemoryEntries
            + purgedDeletedMemoryEntries
            + deletedMemoryJobs
            + purgedSoftDeletedConversations
            + purgedEmptyConversations;
    }
}
