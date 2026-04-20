package com.example.demo.service;

import com.example.demo.api.ApiException;
import com.example.demo.infrastructure.audit.PostgresChatAuditRepository;
import com.example.demo.infrastructure.audit.PostgresChatRunTraceRepository;
import com.example.demo.model.ChatAuditRunDetail;
import com.example.demo.model.ChatAuditRunSummary;
import com.example.demo.model.ChatExecutionResponse;
import com.example.demo.model.ChatRunTraceDetail;
import com.example.demo.model.AppliedInstruction;
import com.example.demo.model.ChatRunOutputTrace;
import com.example.demo.model.InstructionTraceEntry;
import com.example.demo.model.KnowledgeScopeResolved;
import com.example.demo.model.LlmCallTrace;
import com.example.demo.model.RetrievalSummaryTrace;
import com.example.demo.model.RetrievalTrace;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ChatRunQueryService {

    private static final int DEFAULT_LIST_LIMIT = 20;

    private final PostgresChatRunTraceRepository traceRepository;
    private final PostgresChatAuditRepository legacyRepository;

    public ChatRunQueryService(
        PostgresChatRunTraceRepository traceRepository,
        PostgresChatAuditRepository legacyRepository
    ) {
        this.traceRepository = traceRepository;
        this.legacyRepository = legacyRepository;
    }

    public List<ChatAuditRunSummary> listRuns() {
        List<ChatAuditRunSummary> modernRuns = traceRepository.findRunSummaries(DEFAULT_LIST_LIMIT);
        List<ChatAuditRunSummary> legacyRuns = legacyRepository.findAll(DEFAULT_LIST_LIMIT).stream()
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
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "chat_audit.not_found",
                "Chat audit run '" + id + "' does not exist"
            ));
    }

    public ChatRunTraceDetail getTrace(String id) {
        String runId = requireValidId(id);
        return traceRepository.findTrace(runId)
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "chat_trace.not_found",
                "Chat run trace '" + id + "' does not exist"
            ));
    }

    public ChatExecutionResponse getResult(String id) {
        ChatRunTraceDetail trace = getTrace(id);
        String status = trace.status();
        if (!"COMPLETED".equals(status)) {
            if ("FAILED".equals(status)) {
                throw new ApiException(
                    HttpStatus.CONFLICT,
                    "chat_run.failed",
                    trace.failureMessage() == null ? "Chat run failed" : trace.failureMessage()
                );
            }
            if ("CANCELLED".equals(status)) {
                throw new ApiException(
                    HttpStatus.CONFLICT,
                    "chat_run.cancelled",
                    "Chat run was cancelled"
                );
            }
            throw new ApiException(
                HttpStatus.CONFLICT,
                "chat_run.not_completed",
                "Chat run is still in progress"
            );
        }

        return traceRepository.findResult(trace.id())
            .orElseGet(() -> {
                ChatExecutionResponse reconstructed = reconstructResult(trace);
                traceRepository.insertResultIfAbsent(
                    trace.id(),
                    reconstructed,
                    trace.completedAt() == null ? trace.createdAt() : trace.completedAt(),
                    "TRACE_BACKFILL"
                );
                return reconstructed;
            });
    }

    private ChatExecutionResponse reconstructResult(ChatRunTraceDetail trace) {
        ChatRunOutputTrace output = trace.output();
        if (output == null || trace.requestSnapshot() == null || output.finalUserAnswer() == null) {
            throw new ApiException(
                HttpStatus.CONFLICT,
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
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
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

}
