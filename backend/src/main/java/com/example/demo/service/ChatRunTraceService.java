package com.example.demo.service;

import com.example.demo.api.ApiException;
import com.example.demo.service.audit.port.ChatRunTraceRepository;
import com.example.demo.llm.LlmClient;
import com.example.demo.model.AnswerMode;
import com.example.demo.model.ChatAuditRunDetail;
import com.example.demo.model.ChatAuditRunSummary;
import com.example.demo.model.ChatExecutionResponse;
import com.example.demo.model.ChatExecutionRequest;
import com.example.demo.model.ChatMode;
import com.example.demo.model.ChatRunMessage;
import com.example.demo.model.ChatRunOutputTrace;
import com.example.demo.model.ChatSource;
import com.example.demo.model.InstructionTraceEntry;
import com.example.demo.model.KnowledgeScopeResolved;
import com.example.demo.model.LlmCallTrace;
import com.example.demo.model.PromptPolicySnapshot;
import com.example.demo.model.RetrievalDebug;
import com.example.demo.model.RetrievalTrace;
import com.example.demo.service.audit.ChatRunLeaseToken;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;

@Service
public class ChatRunTraceService {

    private static final String PROVIDER_OLLAMA = "ollama";

    private final ChatRunTraceRepository repository;
    private final ChatRunStateMachine stateMachine;
    private final AtomicInteger consecutiveFailureCount = new AtomicInteger(0);
    private final AtomicReference<TraceHealth> traceHealth = new AtomicReference<>(TraceHealth.up(null));

    public ChatRunTraceService(ChatRunTraceRepository repository) {
        this.repository = repository;
        this.stateMachine = new ChatRunStateMachine(repository);
    }

    public static ChatRunTraceService noop() {
        return new ChatRunTraceService(new NoopChatRunTraceRepository());
    }

    public RunTraceContext startRun(ChatExecutionRequest request, ChatMode mode) {
        return write(() -> {
            Instant createdAt = Instant.now();
            String runId = UUID.randomUUID().toString();
            repository.insertHeader(
                runId,
                mode,
                request == null ? null : request.model(),
                request == null ? null : request.answerMode(),
                createdAt
            );
            repository.insertEvent(runId, "RECEIVED", Map.of("mode", mode.toValue()), createdAt);
            return new RunTraceContext(runId, createdAt);
        });
    }

    public void saveRequestSnapshot(
        RunTraceContext context,
        ChatExecutionRequest request,
        ChatExecutionRequest normalizedRequest
    ) {
        write(() -> {
            repository.saveRequestSnapshot(context.id(), request, normalizedRequest);
            repository.insertEventIfRunMutable(context.id(), "REQUEST_SNAPSHOT_SAVED", Map.of(), Instant.now());
            return null;
        });
    }

    public void savePromptSnapshot(
        RunTraceContext context,
        PromptPolicySnapshot snapshot,
        List<InstructionTraceEntry> instructionTrace,
        KnowledgeScopeResolved knowledgeScopeResolved
    ) {
        write(() -> {
            PromptPolicySnapshot enrichedSnapshot = snapshot == null
                ? null
                : snapshot.withTrace(instructionTrace, knowledgeScopeResolved);
            repository.savePromptSnapshot(context.id(), enrichedSnapshot);
            repository.insertEventIfRunMutable(context.id(), "PROMPT_RESOLVED", Map.of(), Instant.now());
            return null;
        });
    }

    public void savePromptMessages(RunTraceContext context, List<LlmClient.Message> messages) {
        write(() -> {
            repository.savePromptMessages(context.id(), toChatRunMessages(messages));
            repository.insertEventIfRunMutable(context.id(), "PROMPT_MESSAGES_SAVED", Map.of(
                "messageCount",
                messages == null ? 0 : messages.size()
            ), Instant.now());
            return null;
        });
    }

    public void saveRetrievalSummary(
        RunTraceContext context,
        String retrievalStatus,
        RetrievalTrace trace,
        RetrievalDebug debug
    ) {
        write(() -> {
            repository.saveRetrievalSummary(context.id(), retrievalStatus, trace, debug);
            repository.insertEventIfRunMutable(context.id(), "RETRIEVAL_" + retrievalStatus, Map.of(), Instant.now());
            return null;
        });
    }

    public void saveLlmSuccess(
        RunTraceContext context,
        LlmClient.ChatRequest request,
        LlmClient.ChatResult result,
        Integer timeoutSeconds
    ) {
        write(() -> {
            repository.insertLlmCall(context.id(), new LlmCallTrace(
                UUID.randomUUID().toString(),
                PROVIDER_OLLAMA,
                result == null ? null : result.model(),
                toChatRunMessages(request == null ? null : request.messages()),
                result == null ? null : result.rawResponse(),
                result == null ? null : result.answer(),
                result == null ? null : result.promptTokens(),
                result == null ? null : result.completionTokens(),
                result == null ? null : result.totalTokens(),
                result == null ? null : result.latencyMs(),
                0,
                timeoutSeconds,
                result == null ? null : result.finishReason(),
                null,
                null,
                Instant.now()
            ));
            repository.insertEventIfRunMutable(context.id(), "LLM_DONE", Map.of(), Instant.now());
            return null;
        });
    }

    public void saveLlmFailure(
        RunTraceContext context,
        LlmClient.ChatRequest request,
        Throwable throwable,
        Long latencyMs,
        Integer timeoutSeconds
    ) {
        write(() -> {
            repository.insertLlmCall(context.id(), new LlmCallTrace(
                UUID.randomUUID().toString(),
                PROVIDER_OLLAMA,
                request == null ? null : request.model(),
                toChatRunMessages(request == null ? null : request.messages()),
                null,
                null,
                null,
                null,
                null,
                latencyMs,
                0,
                timeoutSeconds,
                null,
                reasonCode(throwable),
                rootMessage(throwable),
                Instant.now()
            ));
            repository.insertEventIfRunMutable(context.id(), "LLM_FAILED", Map.of(
                "code",
                reasonCode(throwable),
                "message",
                rootMessage(throwable)
            ), Instant.now());
            return null;
        });
    }

    public void saveOutput(
        RunTraceContext context,
        String rawModelAnswer,
        String finalUserAnswer,
        List<ChatSource> sources,
        Map<String, Object> postprocess,
        boolean abstained,
        boolean strictSourcesBlockedAnswer
    ) {
        write(() -> {
            repository.saveOutput(context.id(), new ChatRunOutputTrace(
                rawModelAnswer,
                finalUserAnswer,
                sources,
                com.fasterxml.jackson.databind.json.JsonMapper.builder().findAndAddModules().build().valueToTree(postprocess == null ? Map.of() : postprocess),
                abstained,
                strictSourcesBlockedAnswer
            ));
            repository.insertEventIfRunMutable(context.id(), "OUTPUT_SAVED", Map.of(), Instant.now());
            return null;
        });
    }

    public boolean completeRun(
        RunTraceContext context,
        String resolvedModel,
        AnswerMode appliedAnswerMode,
        String contextStatus
    ) {
        return write(() -> stateMachine.complete(context, resolvedModel, appliedAnswerMode, contextStatus));
    }

    public boolean completeRunWithResult(RunTraceContext context, ChatExecutionResponse response) {
        return write(() -> stateMachine.completeWithResult(context, response));
    }

    public boolean failRun(RunTraceContext context, String failureStage, Throwable throwable) {
        return write(() -> stateMachine.fail(context, failureStage, throwable));
    }

    public boolean cancelRun(RunTraceContext context) {
        if (context == null) {
            return false;
        }
        return cancelRun(context.id(), context.createdAt());
    }

    public boolean cancelRun(String runId, Instant createdAt) {
        return write(() -> stateMachine.cancel(runId, createdAt));
    }

    public boolean transitionStage(RunTraceContext context, String status) {
        return write(() -> stateMachine.transitionStage(context, status));
    }

    public void insertEvent(RunTraceContext context, String eventType, Object payload) {
        if (context == null) {
            return;
        }
        write(() -> {
            repository.insertEvent(context.id(), eventType, payload == null ? Map.of() : payload, Instant.now());
            return null;
        });
    }

    public TraceHealth currentHealth() {
        return traceHealth.get();
    }

    private <T> T write(TraceWriteOperation<T> operation) {
        try {
            T result = operation.execute();
            consecutiveFailureCount.set(0);
            traceHealth.set(TraceHealth.up(Instant.now()));
            return result;
        } catch (RuntimeException exception) {
            int failures = consecutiveFailureCount.incrementAndGet();
            traceHealth.set(TraceHealth.down(
                reasonCode(exception),
                rootMessage(exception),
                failures,
                Instant.now()
            ));
            throw exception;
        }
    }

    private List<ChatRunMessage> toChatRunMessages(List<LlmClient.Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        return messages.stream()
            .map(message -> new ChatRunMessage(message.role(), message.content()))
            .toList();
    }

    private String reasonCode(Throwable throwable) {
        if (throwable instanceof ApiException apiException) {
            return apiException.getCode();
        }
        return "chat_trace.execution_failed";
    }

    private String rootMessage(Throwable throwable) {
        if (throwable == null) {
            return "unknown";
        }
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    public record RunTraceContext(
        String id,
        Instant createdAt,
        ChatRunLeaseToken leaseToken
    ) {
        public RunTraceContext(String id, Instant createdAt) {
            this(id, createdAt, null);
        }
    }

    public record TraceHealth(
        String status,
        String reasonCode,
        String reasonMessage,
        int consecutiveFailureCount,
        String lastStateChangedAt
    ) {
        private static TraceHealth up(Instant observedAt) {
            return new TraceHealth("UP", null, null, 0, observedAt == null ? null : observedAt.toString());
        }

        private static TraceHealth down(
            String reasonCode,
            String reasonMessage,
            int consecutiveFailureCount,
            Instant observedAt
        ) {
            return new TraceHealth(
                "DOWN",
                reasonCode,
                reasonMessage,
                consecutiveFailureCount,
                observedAt == null ? null : observedAt.toString()
            );
        }
    }

    @FunctionalInterface
    private interface TraceWriteOperation<T> {
        T execute();
    }

    private static final class NoopChatRunTraceRepository implements ChatRunTraceRepository {

        @Override
        public void insertHeader(String runId, ChatMode mode, String requestedModel, AnswerMode requestedAnswerMode, Instant createdAt) {
        }

        @Override
        public void saveRequestSnapshot(String runId, ChatExecutionRequest request, ChatExecutionRequest normalizedRequest) {
        }

        @Override
        public void savePromptSnapshot(String runId, PromptPolicySnapshot snapshot) {
        }

        @Override
        public void savePromptMessages(String runId, List<ChatRunMessage> messages) {
        }

        @Override
        public void saveRetrievalSummary(String runId, String retrievalStatus, RetrievalTrace trace, RetrievalDebug debug) {
        }

        @Override
        public void insertLlmCall(String runId, LlmCallTrace call) {
        }

        @Override
        public void saveOutput(String runId, ChatRunOutputTrace output) {
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
        public Optional<com.example.demo.service.audit.ChatRunHeaderStatus> findHeaderStatus(String runId) {
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
        public boolean insertEventIfRunMutable(String runId, String eventType, Object payload, Instant createdAt) {
            return false;
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
        public Optional<com.example.demo.model.ChatRunTraceDetail> findTrace(String runId) {
            return Optional.empty();
        }

        @Override
        public int deleteRunsOlderThan(Instant cutoff) {
            return 0;
        }
    }
}
