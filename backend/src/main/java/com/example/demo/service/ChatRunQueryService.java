package com.example.demo.service;

import com.example.demo.error.ApplicationException;
import com.example.demo.error.ErrorType;
import com.example.demo.service.audit.port.ChatAuditRepository;
import com.example.demo.service.audit.port.ChatRunTraceRepository;
import com.example.demo.service.audit.ChatRunHeaderStatus;
import com.example.demo.model.ChatAuditRunDetail;
import com.example.demo.model.ChatAuditRunSummary;
import com.example.demo.model.ChatExecutionResponse;
import com.example.demo.model.ChatRunStatusResponse;
import com.example.demo.model.ChatRunTraceDetail;
import com.example.demo.model.AppliedInstruction;
import com.example.demo.model.ChatRunOutputTrace;
import com.example.demo.model.InstructionTraceEntry;
import com.example.demo.model.KnowledgeScopeResolved;
import com.example.demo.model.LlmCallTrace;
import com.example.demo.model.RetrievalSummaryTrace;
import com.example.demo.model.RetrievalTrace;
import com.example.demo.service.conversation.port.ConversationRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ChatRunQueryService {

    private static final Logger logger = LoggerFactory.getLogger(ChatRunQueryService.class);
    private static final int DEFAULT_LIST_LIMIT = 20;

    private final ChatRunTraceRepository traceRepository;
    private final ChatAuditRepository legacyRepository;
    private final ConversationRepository conversationRepository;

    @Autowired
    public ChatRunQueryService(
        ChatRunTraceRepository traceRepository,
        ChatAuditRepository legacyRepository,
        ObjectProvider<ConversationRepository> conversationRepositoryProvider
    ) {
        this.traceRepository = traceRepository;
        this.legacyRepository = legacyRepository;
        this.conversationRepository = conversationRepositoryProvider.getIfAvailable();
    }

    public ChatRunQueryService(
        ChatRunTraceRepository traceRepository,
        ChatAuditRepository legacyRepository
    ) {
        this.traceRepository = traceRepository;
        this.legacyRepository = legacyRepository;
        this.conversationRepository = null;
    }

    public List<ChatAuditRunSummary> listRuns() {
        return listRuns(null);
    }

    public List<ChatAuditRunSummary> listRuns(String workspaceKey) {
        String normalizedWorkspaceKey = normalizeOptionalWorkspaceKey(workspaceKey);
        List<ChatAuditRunSummary> modernRuns = traceRepository.findRunSummaries(DEFAULT_LIST_LIMIT, normalizedWorkspaceKey);
        List<ChatAuditRunSummary> legacyRuns = normalizedWorkspaceKey != null ? List.of() : legacyRepository.findAll(DEFAULT_LIST_LIMIT).stream()
            .map(record -> new ChatAuditRunSummary(
                record.id(),
                record.mode(),
                record.model(),
                record.answerMode(),
                clip(record.prompt(), 120),
                clip(record.answer(), 160),
                record.createdAt()
            ))
            .toList();
        List<ChatAuditRunSummary> mergedRuns = new ArrayList<>(modernRuns.size() + legacyRuns.size());
        mergedRuns.addAll(modernRuns);
        mergedRuns.addAll(legacyRuns);
        mergedRuns.sort(Comparator.comparing(ChatAuditRunSummary::createdAt, Comparator.nullsLast(Comparator.reverseOrder())));

        Map<String, ChatAuditRunSummary> deduplicated = new LinkedHashMap<>();
        for (ChatAuditRunSummary run : mergedRuns) {
            deduplicated.putIfAbsent(run.id(), run);
        }
        return deduplicated.values().stream()
            .limit(DEFAULT_LIST_LIMIT)
            .toList();
    }

    public ChatAuditRunDetail getRun(String id) {
        String runId = requireValidId(id);
        return traceRepository.findAuditRunDetail(runId)
            .or(() -> legacyRepository.findById(runId).map(record -> ChatAuditJson.read(record.auditJson())))
            .orElseThrow(() -> new ApplicationException(
                ErrorType.NOT_FOUND,
                "chat_audit.not_found",
                "Chat audit run '" + id + "' does not exist"
            ));
    }

    public ChatRunTraceDetail getTrace(String id) {
        String runId = requireValidId(id);
        return traceRepository.findTrace(runId)
            .orElseThrow(() -> new ApplicationException(
                ErrorType.NOT_FOUND,
                "chat_trace.not_found",
                "Chat run trace '" + id + "' does not exist"
            ));
    }

    public ChatRunStatusResponse getStatus(String id) {
        String runId = requireValidId(id);
        ChatRunHeaderStatus header = traceRepository.findHeaderStatus(runId)
            .orElseThrow(() -> new ApplicationException(
                ErrorType.NOT_FOUND,
                "chat_trace.not_found",
                "Chat run trace '" + id + "' does not exist"
            ));
        return new ChatRunStatusResponse(
            header.id(),
            header.status(),
            header.createdAt(),
            header.completedAt(),
            header.failedAt(),
            header.latencyMsTotal(),
            header.failureStage(),
            header.failureCode(),
            header.failureMessage()
        );
    }

    public ChatExecutionResponse getResult(String id) {
        String runId = requireValidId(id);
        ChatRunHeaderStatus header = traceRepository.findHeaderStatus(runId)
            .orElseThrow(() -> new ApplicationException(
                ErrorType.NOT_FOUND,
                "chat_trace.not_found",
                "Chat run trace '" + id + "' does not exist"
            ));
        String status = header.status();
        if (!"COMPLETED".equals(status)) {
            if ("FAILED".equals(status)) {
                throw new ApplicationException(
                    ErrorType.CONFLICT,
                    "chat_run.failed",
                    header.failureMessage() == null ? "Chat run failed" : header.failureMessage()
                );
            }
            if ("CANCELLED".equals(status)) {
                throw new ApplicationException(
                    ErrorType.CONFLICT,
                    "chat_run.cancelled",
                    "Chat run was cancelled"
                );
            }
            throw new ApplicationException(
                ErrorType.CONFLICT,
                "chat_run.not_completed",
                "Chat run is still in progress"
            );
        }

        ChatExecutionResponse response = traceRepository.findResult(runId)
            .orElseGet(() -> {
                ChatRunTraceDetail trace = traceRepository.findTrace(runId)
                    .orElseThrow(() -> new ApplicationException(
                        ErrorType.NOT_FOUND,
                        "chat_trace.not_found",
                        "Chat run trace '" + id + "' does not exist"
                    ));
                ChatExecutionResponse reconstructed = reconstructResult(trace);
                backfillResultBestEffort(trace, reconstructed);
                return reconstructed;
            });
        return enrichResult(runId, response);
    }

    private ChatExecutionResponse enrichResult(String runId, ChatExecutionResponse response) {
        if (conversationRepository == null || response == null) {
            return response;
        }
        try {
            return conversationRepository.findRunByRunId(runId)
                .map(run -> response.withConversationMetadata(
                    run.conversationId(),
                    run.turnNo(),
                    response.contextAssemblyId() == null ? run.contextAssemblyId() : response.contextAssemblyId(),
                    response.contextSummary()
                ))
                .orElse(response);
        } catch (RuntimeException exception) {
            logger.warn("Unable to enrich chat run result with conversation metadata: runId={}", runId, exception);
            return response;
        }
    }

    private void backfillResultBestEffort(ChatRunTraceDetail trace, ChatExecutionResponse response) {
        try {
            traceRepository.insertResultIfAbsent(
                trace.id(),
                response,
                trace.completedAt() == null ? trace.createdAt() : trace.completedAt(),
                "TRACE_BACKFILL"
            );
        } catch (RuntimeException exception) {
            logger.warn("Unable to lazy-backfill immutable chat run result: runId={}", trace.id(), exception);
        }
    }

    private ChatExecutionResponse reconstructResult(ChatRunTraceDetail trace) {
        ChatRunOutputTrace output = trace.output();
        if (output == null || trace.requestSnapshot() == null || output.finalUserAnswer() == null) {
            throw new ApplicationException(
                ErrorType.CONFLICT,
                "chat_run.result_unavailable",
                "Chat run completed but result output is unavailable"
            );
        }
        RetrievalSummaryTrace retrieval = trace.retrievalSummary();
        LlmCallTrace latestLlmCall = trace.llmCalls().isEmpty()
            ? null
            : trace.llmCalls().get(trace.llmCalls().size() - 1);
        List<InstructionTraceEntry> instructionTrace = trace.promptSnapshot() == null
            ? List.of()
            : trace.promptSnapshot().instructionTrace();
        KnowledgeScopeResolved knowledgeScopeResolved = trace.promptSnapshot() == null
            ? KnowledgeScopeResolved.empty()
            : trace.promptSnapshot().knowledgeScopeResolved();

        return new ChatExecutionResponse(
            trace.mode(),
            firstNonBlank(trace.resolvedModel(), trace.requestedModel(), latestLlmCall == null ? "" : latestLlmCall.model()),
            trace.requestSnapshot().prompt(),
            output.finalUserAnswer(),
            trace.contextStatus(),
            (trace.completedAt() == null ? trace.createdAt() : trace.completedAt()).toString(),
            latestLlmCall == null ? null : latestLlmCall.promptTokens(),
            latestLlmCall == null ? null : latestLlmCall.completionTokens(),
            latestLlmCall == null ? null : latestLlmCall.totalTokens(),
            trace.appliedAnswerMode() == null ? trace.requestedAnswerMode() : trace.appliedAnswerMode(),
            instructionTrace.stream()
                .filter(entry -> !entry.temporary())
                .map(entry -> new AppliedInstruction(
                    entry.instructionId(),
                    entry.title(),
                    entry.category(),
                    entry.scopeLevel(),
                    entry.scopeTargetId(),
                    entry.revision()
                ))
                .toList(),
            instructionTrace,
            knowledgeScopeResolved,
            retrieval == null || retrieval.trace() == null ? new RetrievalTrace(0, 0, 0, 0, 0, 0, 0, 0, 0) : retrieval.trace(),
            retrieval == null ? null : retrieval.debug(),
            output.sources(),
            trace.id()
        );
    }

    private String requireValidId(String id) {
        try {
            return UUID.fromString(id).toString();
        } catch (IllegalArgumentException exception) {
            throw new ApplicationException(
                ErrorType.INVALID_REQUEST,
                "chat_audit.invalid_id",
                "Chat audit id must be a valid UUID",
                exception
            );
        }
    }

    private String clip(String value, int limit) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= limit) {
            return normalized;
        }
        return normalized.substring(0, limit) + "...";
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String normalizeOptionalWorkspaceKey(String workspaceKey) {
        if (workspaceKey == null || workspaceKey.isBlank()) {
            return null;
        }
        return workspaceKey.trim().toLowerCase(java.util.Locale.ROOT);
    }

}
