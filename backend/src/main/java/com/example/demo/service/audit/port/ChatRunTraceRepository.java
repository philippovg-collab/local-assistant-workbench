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

    void saveRequestSnapshot(
        String runId,
        ChatExecutionRequest request,
        ChatExecutionRequest normalizedRequest
    );

    void savePromptSnapshot(String runId, PromptPolicySnapshot snapshot);

    void savePromptMessages(String runId, List<ChatRunMessage> messages);

    void saveRetrievalSummary(
        String runId,
        String retrievalStatus,
        RetrievalTrace trace,
        RetrievalDebug debug
    );

    void insertLlmCall(String runId, LlmCallTrace call);

    void saveOutput(String runId, ChatRunOutputTrace output);

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

    boolean insertEventIfRunMutable(String runId, String eventType, Object payload, Instant createdAt);

    List<ChatAuditRunSummary> findRunSummaries(int limit, String workspaceKey);

    Optional<ChatAuditRunDetail> findAuditRunDetail(String runId);

    Optional<ChatRunTraceDetail> findTrace(String runId);

    int deleteRunsOlderThan(Instant cutoff);
}
