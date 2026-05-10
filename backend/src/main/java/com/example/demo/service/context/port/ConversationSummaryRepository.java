package com.example.demo.service.context.port;

import com.example.demo.model.ConversationSummaryMemory;
import com.example.demo.service.context.ConversationSummaryPayload;
import com.example.demo.service.context.ConversationSummaryRefreshJob;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public interface ConversationSummaryRepository {

    Optional<ConversationSummaryMemory> findByConversationId(String conversationId);

    boolean requestRefresh(String conversationId, int requestedThroughTurnNo, Instant now);

    Optional<ConversationSummaryRefreshJob> claimNextRefreshJob(
        String leaseOwner,
        Instant now,
        Duration leaseDuration
    );

    boolean completeRefresh(
        ConversationSummaryRefreshJob job,
        ConversationSummaryPayload payload,
        String updatedFromRunId,
        Instant now
    );

    void failRefresh(
        ConversationSummaryRefreshJob job,
        String errorCode,
        String errorMessage,
        Instant nextRetryAt,
        boolean exhausted,
        Instant now
    );

    void deleteRefreshJob(String conversationId);

    default boolean deleteRefreshJob(ConversationSummaryRefreshJob job) {
        if (job == null) {
            return false;
        }
        deleteRefreshJob(job.conversationId());
        return true;
    }

    boolean hasPendingRefreshJobs();
}
