package com.example.demo.service.context;

import com.example.demo.config.ContextProperties;
import com.example.demo.config.LlmProperties;
import com.example.demo.llmprovider.ActiveLlmProviderResolver;
import com.example.demo.model.ChatExecutionResponse;
import com.example.demo.model.ChatSource;
import com.example.demo.model.ContextAssemblySnapshotDetail;
import com.example.demo.model.ConversationSummaryMemory;
import com.example.demo.model.ConversationSummarySourceRef;
import com.example.demo.model.MaterialMetadataSnapshot;
import com.example.demo.model.RetrievalQueryReferencedSource;
import com.example.demo.service.audit.port.ChatRunTraceRepository;
import com.example.demo.service.context.ConversationSummaryPromptBuilder.SummaryPromptInput;
import com.example.demo.service.context.ConversationSummaryPromptBuilder.SummaryTurn;
import com.example.demo.service.context.port.ContextAssemblyTraceRepository;
import com.example.demo.service.context.port.ConversationSummaryRepository;
import com.example.demo.service.conversation.StoredConversationRun;
import com.example.demo.service.conversation.port.ConversationRepository;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ConversationSummaryWorkerService {

    private static final Logger logger = LoggerFactory.getLogger(ConversationSummaryWorkerService.class);
    private static final String STATUS_COMPLETED = "COMPLETED";

    private final ContextProperties contextProperties;
    private final LlmProperties llmProperties;
    private final ActiveLlmProviderResolver activeProviderResolver;
    private final ConversationSummaryRepository summaryRepository;
    private final ConversationRepository conversationRepository;
    private final ChatRunTraceRepository traceRepository;
    private final ContextAssemblyTraceRepository contextAssemblyTraceRepository;
    private final ConversationSummaryService summaryService;
    private final Executor executor;
    private final AtomicBoolean scheduled = new AtomicBoolean(false);
    private final String workerId = ManagementFactory.getRuntimeMXBean().getName() + "-summary-" + UUID.randomUUID();

    @Autowired
    public ConversationSummaryWorkerService(
        ContextProperties contextProperties,
        LlmProperties llmProperties,
        ActiveLlmProviderResolver activeProviderResolver,
        ConversationSummaryRepository summaryRepository,
        ConversationRepository conversationRepository,
        ChatRunTraceRepository traceRepository,
        ContextAssemblyTraceRepository contextAssemblyTraceRepository,
        ConversationSummaryService summaryService,
        @Qualifier("conversationSummaryExecutor") Executor executor
    ) {
        this.contextProperties = contextProperties;
        this.llmProperties = llmProperties;
        this.activeProviderResolver = activeProviderResolver;
        this.summaryRepository = summaryRepository;
        this.conversationRepository = conversationRepository;
        this.traceRepository = traceRepository;
        this.contextAssemblyTraceRepository = contextAssemblyTraceRepository;
        this.summaryService = summaryService;
        this.executor = executor == null ? Runnable::run : executor;
    }

    public ConversationSummaryWorkerService(
        ContextProperties contextProperties,
        LlmProperties llmProperties,
        ConversationSummaryRepository summaryRepository,
        ConversationRepository conversationRepository,
        ChatRunTraceRepository traceRepository,
        ContextAssemblyTraceRepository contextAssemblyTraceRepository,
        ConversationSummaryService summaryService,
        @Qualifier("conversationSummaryExecutor") Executor executor
    ) {
        this(
            contextProperties,
            llmProperties,
            null,
            summaryRepository,
            conversationRepository,
            traceRepository,
            contextAssemblyTraceRepository,
            summaryService,
            executor
        );
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        requestProcessing();
    }

    public void requestProcessing() {
        if (!contextProperties.isSummaryEnabled()) {
            return;
        }
        if (!scheduled.compareAndSet(false, true)) {
            return;
        }
        try {
            executor.execute(this::drainQueue);
        } catch (RejectedExecutionException exception) {
            scheduled.set(false);
            logger.warn("Conversation summary executor rejected processing request; summary jobs remain pending", exception);
        }
    }

    private void drainQueue() {
        try {
            while (!Thread.currentThread().isInterrupted() && contextProperties.isSummaryEnabled()) {
                ConversationSummaryRefreshJob job = summaryRepository.claimNextRefreshJob(
                    workerId,
                    Instant.now(),
                    Duration.ofSeconds(contextProperties.getSummaryRetryMaxSeconds())
                ).orElse(null);
                if (job == null) {
                    break;
                }
                process(job);
            }
        } finally {
            scheduled.set(false);
            if (contextProperties.isSummaryEnabled() && summaryRepository.hasPendingRefreshJobs()) {
                requestProcessing();
            }
        }
    }

    private void process(ConversationSummaryRefreshJob job) {
        try {
            ConversationSummaryMemory previousSummary = summaryRepository
                .findByConversationId(job.conversationId())
                .orElse(null);
            int previousThroughTurnNo = previousSummary == null || previousSummary.summaryThroughTurnNo() == null
                ? 0
                : previousSummary.summaryThroughTurnNo();
            if (previousThroughTurnNo >= job.requestedThroughTurnNo()) {
                summaryRepository.deleteRefreshJob(job);
                return;
            }

            List<SummaryTurn> turns = completedTurns(job, previousThroughTurnNo);
            if (turns.isEmpty()) {
                summaryRepository.deleteRefreshJob(job);
                return;
            }

            ConversationSummaryPayload payload = summaryService.summarize(
                new SummaryPromptInput(previousSummary, turns),
                modelFor(turns)
            );
            String updatedFromRunId = turns.getLast().runId();
            summaryRepository.completeRefresh(job, payload, updatedFromRunId, Instant.now());
        } catch (RuntimeException exception) {
            fail(job, exception);
        }
    }

    private List<SummaryTurn> completedTurns(
        ConversationSummaryRefreshJob job,
        int previousThroughTurnNo
    ) {
        List<StoredConversationRun> completedRuns = conversationRepository.listRuns(job.conversationId()).stream()
            .filter(run -> STATUS_COMPLETED.equals(run.status()))
            .filter(run -> run.turnNo() > previousThroughTurnNo)
            .filter(run -> run.turnNo() <= job.requestedThroughTurnNo())
            .sorted(Comparator.comparingInt(StoredConversationRun::turnNo))
            .toList();
        int maxInputTurns = contextProperties.getSummaryMaxInputTurns();
        if (completedRuns.size() > maxInputTurns) {
            completedRuns = completedRuns.subList(completedRuns.size() - maxInputTurns, completedRuns.size());
        }
        return completedRuns.stream()
            .map(this::summaryTurn)
            .filter(turn -> StringUtils.hasText(turn.originalUserPrompt())
                && StringUtils.hasText(turn.finalAssistantAnswer()))
            .toList();
    }

    private SummaryTurn summaryTurn(StoredConversationRun run) {
        ChatExecutionResponse response = traceRepository.findResult(run.runId()).orElse(null);
        if (response == null) {
            return new SummaryTurn(
                run.turnNo(),
                run.runId(),
                null,
                null,
                run.userPrompt(),
                null,
                List.of()
            );
        }
        ContextAssemblySnapshotDetail snapshot = contextAssemblyTraceRepository.findByRunId(run.runId()).orElse(null);
        return new SummaryTurn(
            run.turnNo(),
            run.runId(),
            response.mode(),
            response.contextStatus(),
            firstNonBlank(run.userPrompt(), response.prompt()),
            response.answer(),
            sourceRefs(response, snapshot)
        );
    }

    private List<ConversationSummarySourceRef> sourceRefs(
        ChatExecutionResponse response,
        ContextAssemblySnapshotDetail snapshot
    ) {
        Map<String, ConversationSummarySourceRef> refs = new LinkedHashMap<>();
        if (snapshot != null
            && snapshot.retrievalQueryResolution() != null
            && snapshot.retrievalQueryResolution().referencedSources() != null) {
            for (RetrievalQueryReferencedSource source : snapshot.retrievalQueryResolution().referencedSources()) {
                putRef(refs, new ConversationSummarySourceRef(
                    source.materialId(),
                    source.title(),
                    source.documentNumber(),
                    source.project(),
                    source.counterparty(),
                    source.page()
                ));
            }
        }
        if (response != null && response.sources() != null) {
            for (ChatSource source : response.sources()) {
                MaterialMetadataSnapshot metadata = source.metadata();
                putRef(refs, new ConversationSummarySourceRef(
                    source.materialId(),
                    source.title(),
                    metadata == null ? null : metadata.documentNumber(),
                    metadata == null ? null : metadata.project(),
                    metadata == null ? null : metadata.counterparty(),
                    source.page()
                ));
            }
        }
        return refs.values().stream()
            .limit(contextProperties.getSummaryMaxSourceRefs())
            .toList();
    }

    private void putRef(Map<String, ConversationSummarySourceRef> refs, ConversationSummarySourceRef ref) {
        if (ref == null) {
            return;
        }
        String key = String.join(
            "|",
            nullToEmpty(ref.materialId()),
            nullToEmpty(ref.documentNumber()),
            ref.page() == null ? "" : ref.page().toString()
        );
        refs.putIfAbsent(key, ref);
    }

    private String modelFor(List<SummaryTurn> turns) {
        for (int index = turns.size() - 1; index >= 0; index--) {
            ChatExecutionResponse response = traceRepository.findResult(turns.get(index).runId()).orElse(null);
            if (response != null && StringUtils.hasText(response.model())) {
                return response.model();
            }
        }
        if (activeProviderResolver != null) {
            String activeDefaultModel = activeProviderResolver.defaultChatModel();
            if (StringUtils.hasText(activeDefaultModel)) {
                return activeDefaultModel;
            }
        }
        return llmProperties.getModel();
    }

    private void fail(ConversationSummaryRefreshJob job, RuntimeException exception) {
        int nextAttemptCount = job.attemptCount() + 1;
        boolean exhausted = nextAttemptCount >= contextProperties.getSummaryMaxAttempts();
        Instant retryAt = exhausted ? null : Instant.now().plusSeconds(backoffSeconds(job.attemptCount()));
        try {
            summaryRepository.failRefresh(
                job,
                exception.getClass().getSimpleName(),
                exception.getMessage(),
                retryAt,
                exhausted,
                Instant.now()
            );
        } catch (RuntimeException storageException) {
            logger.warn("Unable to record conversation summary refresh failure: conversationId={}", job.conversationId(), storageException);
        }
        logger.warn("Conversation summary refresh failed: conversationId={}", job.conversationId(), exception);
    }

    private long backoffSeconds(int attemptCount) {
        long base = contextProperties.getSummaryRetryBaseSeconds();
        long multiplier = 1L << Math.min(10, Math.max(0, attemptCount));
        return Math.min(contextProperties.getSummaryRetryMaxSeconds(), base * multiplier);
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
