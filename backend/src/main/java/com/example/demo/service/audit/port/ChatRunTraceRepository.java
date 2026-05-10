package com.example.demo.service.audit.port;

import com.example.demo.model.AnswerMode;
import com.example.demo.model.ChatAuditRunDetail;
import com.example.demo.model.ChatAuditRunSummary;
import com.example.demo.model.ChatExecutionRequest;
import com.example.demo.model.ChatExecutionResponse;
import com.example.demo.model.ChatMode;
import com.example.demo.model.ChatRunMessage;
import com.example.demo.model.ChatRunOutputTrace;
import com.example.demo.model.ChatRunTraceDetail;
import com.example.demo.model.LlmCallTrace;
import com.example.demo.model.PromptPolicySnapshot;
import com.example.demo.model.RetrievalDebug;
import com.example.demo.model.RetrievalTrace;
import com.example.demo.service.audit.ChatRunHeaderStatus;
import com.example.demo.service.audit.ChatRunLeaseToken;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ChatRunTraceRepository {

    void insertHeader(
        String runId,
        ChatMode mode,
        String requestedModel,
        AnswerMode requestedAnswerMode,
        Instant createdAt
    );

    default boolean saveRequestSnapshot(
        String runId,
        ChatExecutionRequest request,
        ChatExecutionRequest normalizedRequest
    ) {
        return saveRequestSnapshot(runId, request, normalizedRequest, null);
    }

    boolean saveRequestSnapshot(
        String runId,
        ChatExecutionRequest request,
        ChatExecutionRequest normalizedRequest,
        ChatRunLeaseToken leaseToken
    );

    default boolean savePromptSnapshot(String runId, PromptPolicySnapshot snapshot) {
        return savePromptSnapshot(runId, snapshot, null);
    }

    boolean savePromptSnapshot(String runId, PromptPolicySnapshot snapshot, ChatRunLeaseToken leaseToken);

    default boolean savePromptMessages(String runId, List<ChatRunMessage> messages) {
        return savePromptMessages(runId, messages, null);
    }

    boolean savePromptMessages(String runId, List<ChatRunMessage> messages, ChatRunLeaseToken leaseToken);

    default boolean saveRetrievalSummary(
        String runId,
        String retrievalStatus,
        RetrievalTrace trace,
        RetrievalDebug debug
    ) {
        return saveRetrievalSummary(runId, retrievalStatus, trace, debug, null);
    }

    boolean saveRetrievalSummary(
        String runId,
        String retrievalStatus,
        RetrievalTrace trace,
        RetrievalDebug debug,
        ChatRunLeaseToken leaseToken
    );

    default boolean insertLlmCall(String runId, LlmCallTrace call) {
        return insertLlmCall(runId, call, null);
    }

    boolean insertLlmCall(String runId, LlmCallTrace call, ChatRunLeaseToken leaseToken);

    default boolean saveOutput(String runId, ChatRunOutputTrace output) {
        return saveOutput(runId, output, null);
    }

    boolean saveOutput(String runId, ChatRunOutputTrace output, ChatRunLeaseToken leaseToken);

    boolean transitionStage(String runId, String status, ChatRunLeaseToken leaseToken);

    boolean completeRun(
        String runId,
        String resolvedModel,
        AnswerMode appliedAnswerMode,
        String contextStatus,
        Instant completedAt,
        long latencyMsTotal
    );

    boolean completeRun(
        String runId,
        String resolvedModel,
        AnswerMode appliedAnswerMode,
        String contextStatus,
        Instant completedAt,
        long latencyMsTotal,
        ChatRunLeaseToken leaseToken
    );

    boolean completeRunWithResult(
        String runId,
        String resolvedModel,
        AnswerMode appliedAnswerMode,
        String contextStatus,
        Instant completedAt,
        long latencyMsTotal,
        ChatExecutionResponse response
    );

    boolean completeRunWithResult(
        String runId,
        String resolvedModel,
        AnswerMode appliedAnswerMode,
        String contextStatus,
        Instant completedAt,
        long latencyMsTotal,
        ChatExecutionResponse response,
        ChatRunLeaseToken leaseToken
    );

    Optional<ChatExecutionResponse> findResult(String runId);

    Optional<ChatRunHeaderStatus> findHeaderStatus(String runId);

    boolean insertResultIfAbsent(
        String runId,
        ChatExecutionResponse response,
        Instant completedAt,
        String source
    );

    boolean failRun(
        String runId,
        String failureStage,
        String failureCode,
        String failureMessage,
        Instant failedAt,
        long latencyMsTotal
    );

    boolean failRun(
        String runId,
        String failureStage,
        String failureCode,
        String failureMessage,
        Instant failedAt,
        long latencyMsTotal,
        ChatRunLeaseToken leaseToken
    );

    boolean cancelRun(String runId, Instant cancelledAt, long latencyMsTotal);

    void insertEvent(String runId, String eventType, Object payload, Instant createdAt);

    default boolean insertEventIfRunMutable(String runId, String eventType, Object payload, Instant createdAt) {
        return insertEventIfRunMutable(runId, eventType, payload, createdAt, null);
    }

    boolean insertEventIfRunMutable(
        String runId,
        String eventType,
        Object payload,
        Instant createdAt,
        ChatRunLeaseToken leaseToken
    );

    List<ChatAuditRunSummary> findRunSummaries(int limit, String workspaceKey);

    Optional<ChatAuditRunDetail> findAuditRunDetail(String runId);

    Optional<ChatRunTraceDetail> findTrace(String runId);

    int deleteRunsOlderThan(Instant cutoff);
}
