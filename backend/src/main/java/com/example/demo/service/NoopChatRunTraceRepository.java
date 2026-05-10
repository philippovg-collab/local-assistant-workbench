package com.example.demo.service;

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
import com.example.demo.service.audit.port.ChatRunTraceRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

final class NoopChatRunTraceRepository implements ChatRunTraceRepository {

    @Override
    public void insertHeader(String runId, ChatMode mode, String requestedModel, AnswerMode requestedAnswerMode, Instant createdAt) {
    }

    @Override
    public boolean saveRequestSnapshot(
        String runId,
        ChatExecutionRequest request,
        ChatExecutionRequest normalizedRequest,
        ChatRunLeaseToken leaseToken
    ) {
        return true;
    }

    @Override
    public boolean savePromptSnapshot(String runId, PromptPolicySnapshot snapshot, ChatRunLeaseToken leaseToken) {
        return true;
    }

    @Override
    public boolean savePromptMessages(String runId, List<ChatRunMessage> messages, ChatRunLeaseToken leaseToken) {
        return true;
    }

    @Override
    public boolean saveRetrievalSummary(
        String runId,
        String retrievalStatus,
        RetrievalTrace trace,
        RetrievalDebug debug,
        ChatRunLeaseToken leaseToken
    ) {
        return true;
    }

    @Override
    public boolean insertLlmCall(String runId, LlmCallTrace call, ChatRunLeaseToken leaseToken) {
        return true;
    }

    @Override
    public boolean saveOutput(String runId, ChatRunOutputTrace output, ChatRunLeaseToken leaseToken) {
        return true;
    }

    @Override
    public boolean completeRun(
        String runId,
        String resolvedModel,
        AnswerMode appliedAnswerMode,
        String contextStatus,
        Instant completedAt,
        long latencyMsTotal
    ) {
        return true;
    }

    @Override
    public boolean completeRun(
        String runId,
        String resolvedModel,
        AnswerMode appliedAnswerMode,
        String contextStatus,
        Instant completedAt,
        long latencyMsTotal,
        ChatRunLeaseToken leaseToken
    ) {
        return true;
    }

    @Override
    public boolean completeRunWithResult(
        String runId,
        String resolvedModel,
        AnswerMode appliedAnswerMode,
        String contextStatus,
        Instant completedAt,
        long latencyMsTotal,
        ChatExecutionResponse response
    ) {
        return true;
    }

    @Override
    public boolean completeRunWithResult(
        String runId,
        String resolvedModel,
        AnswerMode appliedAnswerMode,
        String contextStatus,
        Instant completedAt,
        long latencyMsTotal,
        ChatExecutionResponse response,
        ChatRunLeaseToken leaseToken
    ) {
        return true;
    }

    @Override
    public Optional<ChatExecutionResponse> findResult(String runId) {
        return Optional.empty();
    }

    @Override
    public Optional<ChatRunHeaderStatus> findHeaderStatus(String runId) {
        return Optional.empty();
    }

    @Override
    public boolean insertResultIfAbsent(String runId, ChatExecutionResponse response, Instant completedAt, String source) {
        return false;
    }

    @Override
    public boolean transitionStage(String runId, String status, ChatRunLeaseToken leaseToken) {
        return true;
    }

    @Override
    public boolean failRun(
        String runId,
        String failureStage,
        String failureCode,
        String failureMessage,
        Instant failedAt,
        long latencyMsTotal
    ) {
        return false;
    }

    @Override
    public boolean failRun(
        String runId,
        String failureStage,
        String failureCode,
        String failureMessage,
        Instant failedAt,
        long latencyMsTotal,
        ChatRunLeaseToken leaseToken
    ) {
        return false;
    }

    @Override
    public boolean cancelRun(String runId, Instant cancelledAt, long latencyMsTotal) {
        return false;
    }

    @Override
    public void insertEvent(String runId, String eventType, Object payload, Instant createdAt) {
    }

    @Override
    public boolean insertEventIfRunMutable(
        String runId,
        String eventType,
        Object payload,
        Instant createdAt,
        ChatRunLeaseToken leaseToken
    ) {
        return true;
    }

    @Override
    public List<ChatAuditRunSummary> findRunSummaries(int limit, String workspaceKey) {
        return List.of();
    }

    @Override
    public Optional<ChatAuditRunDetail> findAuditRunDetail(String runId) {
        return Optional.empty();
    }

    @Override
    public Optional<ChatRunTraceDetail> findTrace(String runId) {
        return Optional.empty();
    }

    @Override
    public int deleteRunsOlderThan(Instant cutoff) {
        return 0;
    }
}
