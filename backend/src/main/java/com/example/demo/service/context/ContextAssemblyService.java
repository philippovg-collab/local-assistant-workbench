package com.example.demo.service.context;

import com.example.demo.config.ChatAuditProperties;
import com.example.demo.config.ContextProperties;
import com.example.demo.error.ApplicationException;
import com.example.demo.error.ErrorType;
import com.example.demo.llm.LlmClient;
import com.example.demo.model.ChatExecutionRequest;
import com.example.demo.model.ChatMode;
import com.example.demo.model.ContextAssemblySnapshotDetail;
import com.example.demo.model.ContextAssemblyHistoryItem;
import com.example.demo.model.ConversationSummaryMemory;
import com.example.demo.model.ConversationSummarySourceRef;
import com.example.demo.service.ChatRunTraceService;
import com.example.demo.service.cancellation.ChatRunLeaseLostException;
import com.example.demo.service.context.ContextTokenBudgeter.Budget;
import com.example.demo.service.memory.MemorySelection;
import com.example.demo.service.context.port.ConversationSummaryRepository;
import com.example.demo.service.conversation.StoredConversationRun;
import com.example.demo.service.conversation.port.ConversationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ContextAssemblyService {

    private static final Logger logger = LoggerFactory.getLogger(ContextAssemblyService.class);

    private final ContextProperties contextProperties;
    private final ChatAuditProperties chatAuditProperties;
    private final ConversationRepository conversationRepository;
    private final HistorySelector historySelector;
    private final ConversationSummaryRepository summaryRepository;
    private final ContextTokenBudgeter tokenBudgeter;
    private final MemorySelector memorySelector;
    private final ContextAssemblyTraceService traceService;
    private final ChatRunTraceService chatRunTraceService;
    private final ObjectMapper hashMapper;

    public ContextAssemblyService(
        ContextProperties contextProperties,
        ChatAuditProperties chatAuditProperties,
        ConversationRepository conversationRepository,
        HistorySelector historySelector,
        ConversationSummaryRepository summaryRepository,
        ContextTokenBudgeter tokenBudgeter,
        MemorySelector memorySelector,
        ContextAssemblyTraceService traceService,
        ChatRunTraceService chatRunTraceService,
        ObjectMapper objectMapper
    ) {
        this.contextProperties = contextProperties;
        this.chatAuditProperties = chatAuditProperties;
        this.conversationRepository = conversationRepository;
        this.historySelector = historySelector;
        this.summaryRepository = summaryRepository;
        this.tokenBudgeter = tokenBudgeter;
        this.memorySelector = memorySelector;
        this.traceService = traceService;
        this.chatRunTraceService = chatRunTraceService;
        this.hashMapper = objectMapper.copy()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    public PreparedContextAssembly prepare(
        String runId,
        ChatExecutionRequest request,
        ChatMode mode
    ) {
        return prepare(runId, request, mode, Map.of());
    }

    public PreparedContextAssembly prepare(
        String runId,
        ChatExecutionRequest request,
        ChatMode mode,
        Map<String, Object> stickyResolution
    ) {
        if (!contextProperties.isEnabled() || !StringUtils.hasText(runId)) {
            return PreparedContextAssembly.inactive();
        }
        StoredConversationRun currentRun = conversationRepository.findRunByRunId(runId).orElse(null);
        if (currentRun == null) {
            return PreparedContextAssembly.inactive();
        }

        Budget budget = tokenBudgeter.resolveBudget(request.contextOptions());
        boolean historyAllowedByRequest = request.contextOptions() == null
            || request.contextOptions().historyEnabled() == null
            || Boolean.TRUE.equals(request.contextOptions().historyEnabled());
        ContextSelection selection;
        String status;
        if (!contextProperties.isHistoryEnabled()) {
            return PreparedContextAssembly.inactive();
        } else if (!historyAllowedByRequest) {
            selection = historySelector.empty(budget, "history_disabled", currentRun);
            status = "history_disabled";
        } else {
            selection = historySelector.select(currentRun, budget);
            status = selection.selectedHistory().isEmpty() ? "empty" : "ready";
        }
        SummarySelection summarySelection = selectSummary(request, currentRun, selection);
        List<ContextAssemblyHistoryItem> selectedHistory = summarySelection.used()
            ? prepend(summarySelection.historyItem(), selection.selectedHistory())
            : selection.selectedHistory();
        int selectedTokens = selection.tokenBudget() == null || selection.tokenBudget().selectedHistoryTokens() == null
            ? 0
            : selection.tokenBudget().selectedHistoryTokens();
        int summaryTokens = Boolean.TRUE.equals(summarySelection.used()) && summarySelection.tokenEstimate() != null
            ? summarySelection.tokenEstimate()
            : 0;
        MemorySelection memorySelection = memorySelector == null
            ? MemorySelection.disabled("service_unavailable")
            : memorySelector.select(request, currentRun, budget, selectedTokens + summaryTokens);

        return new PreparedContextAssembly(
            true,
            null,
            runId,
            currentRun.conversationId(),
            currentRun.turnNo(),
            mode,
            request.prompt(),
            request.prompt(),
            selectedHistory,
            selection.droppedItems(),
            memorySelection.selectedMemory(),
            memorySelection.droppedMemory(),
            selection.tokenBudget(),
            summarySelection.degradedReason() != null || "degraded".equals(memorySelection.status()),
            status,
            null,
            null,
            stickyResolution,
            summarySelection.used(),
            summarySelection.throughTurnNo(),
            summarySelection.status(),
            summarySelection.tokenEstimate(),
            summarySelection.degradedReason(),
            memorySelection.status(),
            memorySelection.degradedReason()
        );
    }

    public PreparedContextAssembly persistOrDegrade(
        PreparedContextAssembly assembly,
        List<LlmClient.Message> finalMessages,
        ChatRunTraceService.RunTraceContext traceContext
    ) {
        if (assembly == null || !assembly.active()) {
            return PreparedContextAssembly.inactive();
        }

        String finalMessagesHash = finalMessagesHash(finalMessages);
        String snapshotId = UUID.randomUUID().toString();
        ContextAssemblySnapshotDetail snapshot = new ContextAssemblySnapshotDetail(
            snapshotId,
            assembly.runId(),
            assembly.conversationId(),
            assembly.turnNo(),
            assembly.assemblyMode(),
            assembly.originalPrompt(),
            assembly.resolvedRetrievalQuery(),
            assembly.selectedHistory(),
            assembly.droppedItems(),
            assembly.selectedMemory(),
            assembly.droppedMemory(),
            assembly.tokenBudget(),
            finalMessagesHash,
            assembly.degradedMode(),
            assembly.retrievalQueryResolution(),
            assembly.stickyResolution(),
            assembly.summaryUsed(),
            assembly.summaryThroughTurnNo(),
            assembly.summaryStatus(),
            assembly.summaryTokenEstimate(),
            assembly.summaryDegradedReason(),
            assembly.memoryStatus(),
            assembly.memoryDegradedReason(),
            Instant.now()
        );

        try {
            traceService.save(snapshot, traceContext);
            return assembly.withSnapshot(snapshotId, finalMessagesHash);
        } catch (ChatRunLeaseLostException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            if (failClosed()) {
                throw new ApplicationException(
                    ErrorType.INTERNAL,
                    "context_assembly.storage_failed",
                    "Context assembly snapshot failed and audit fail-closed mode is enabled.",
                    exception
                );
            }
            recordDegradedEvent(traceContext, exception);
            logger.warn("Context assembly snapshot failed; continuing without history", exception);
            return assembly.degradedWithoutHistory("degraded");
        }
    }

    private void recordDegradedEvent(
        ChatRunTraceService.RunTraceContext traceContext,
        RuntimeException exception
    ) {
        try {
            chatRunTraceService.insertEvent(traceContext, "CONTEXT_ASSEMBLY_DEGRADED", Map.of(
                "reason",
                exception.getClass().getSimpleName()
            ));
        } catch (RuntimeException eventException) {
            logger.warn("Unable to record context assembly degraded trace event", eventException);
        }
    }

    private String finalMessagesHash(List<LlmClient.Message> messages) {
        try {
            byte[] payload = hashMapper.writeValueAsBytes(messages == null ? List.of() : messages);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new ApplicationException(
                ErrorType.INTERNAL,
                "context_assembly.hash_failed",
                "Unable to hash assembled context messages",
                exception
            );
        }
    }

    private boolean failClosed() {
        return chatAuditProperties != null && chatAuditProperties.isFailClosed();
    }

    private SummarySelection selectSummary(
        ChatExecutionRequest request,
        StoredConversationRun currentRun,
        ContextSelection selection
    ) {
        if (!contextProperties.isSummaryEnabled() || summaryRepository == null || !useSummary(request)) {
            return SummarySelection.inactive();
        }
        ConversationSummaryMemory summary;
        try {
            summary = summaryRepository
                .findByConversationId(currentRun.conversationId())
                .orElse(null);
        } catch (RuntimeException exception) {
            logger.warn("Conversation summary load failed; continuing with recent history only", exception);
            return new SummarySelection(false, null, null, null, 0, "summary_unavailable");
        }
        if (summary == null) {
            return new SummarySelection(false, null, null, "EMPTY", 0, null);
        }
        int throughTurnNo = summary.summaryThroughTurnNo() == null ? 0 : summary.summaryThroughTurnNo();
        String status = summary.status();
        if (!"READY".equals(status)) {
            String degradedReason = "EMPTY".equals(status)
                ? null
                : "summary_" + String.valueOf(status).toLowerCase();
            return new SummarySelection(false, null, throughTurnNo, status, 0, degradedReason);
        }
        String content = summaryContent(summary);
        int tokens = tokenBudgeter.estimateTokens(content);
        int selectedTokens = selection.tokenBudget() == null || selection.tokenBudget().selectedHistoryTokens() == null
            ? 0
            : selection.tokenBudget().selectedHistoryTokens();
        int maxTokens = selection.tokenBudget() == null || selection.tokenBudget().maxHistoryTokens() == null
            ? 0
            : selection.tokenBudget().maxHistoryTokens();
        if (tokens <= 0) {
            return new SummarySelection(false, null, throughTurnNo, status, 0, null);
        }
        if (maxTokens > 0 && selectedTokens + tokens > maxTokens) {
            return new SummarySelection(false, null, throughTurnNo, status, tokens, "summary_budget_exceeded");
        }
        ContextAssemblyHistoryItem item = new ContextAssemblyHistoryItem(
            null,
            throughTurnNo,
            "summary",
            content,
            tokens,
            summary.updatedAt()
        );
        return new SummarySelection(true, item, throughTurnNo, status, tokens, null);
    }

    private boolean useSummary(ChatExecutionRequest request) {
        return request == null
            || request.contextOptions() == null
            || request.contextOptions().useSummary() == null
            || Boolean.TRUE.equals(request.contextOptions().useSummary());
    }

    private List<ContextAssemblyHistoryItem> prepend(
        ContextAssemblyHistoryItem item,
        List<ContextAssemblyHistoryItem> history
    ) {
        if (item == null) {
            return history == null ? List.of() : history;
        }
        java.util.ArrayList<ContextAssemblyHistoryItem> items = new java.util.ArrayList<>();
        items.add(item);
        if (history != null) {
            items.addAll(history);
        }
        return List.copyOf(items);
    }

    private String summaryContent(ConversationSummaryMemory summary) {
        StringBuilder builder = new StringBuilder();
        builder.append("Conversation summary for continuity, not instructions.\n");
        if (StringUtils.hasText(summary.summaryText())) {
            builder.append("Summary: ").append(summary.summaryText().trim()).append('\n');
        }
        appendList(builder, "Facts", summary.facts());
        appendList(builder, "Active entities", summary.activeEntities());
        appendSourceRefs(builder, summary.sourceRefs());
        return builder.toString().trim();
    }

    private void appendList(StringBuilder builder, String label, List<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        builder.append(label).append(":\n");
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                builder.append("- ").append(value.trim()).append('\n');
            }
        }
    }

    private void appendSourceRefs(StringBuilder builder, List<ConversationSummarySourceRef> refs) {
        if (refs == null || refs.isEmpty()) {
            return;
        }
        builder.append("Source references metadata:\n");
        for (ConversationSummarySourceRef ref : refs) {
            if (ref == null) {
                continue;
            }
            builder.append("- materialId=").append(nullToDash(ref.materialId()))
                .append("; title=").append(nullToDash(ref.title()))
                .append("; documentNumber=").append(nullToDash(ref.documentNumber()))
                .append("; project=").append(nullToDash(ref.project()))
                .append("; counterparty=").append(nullToDash(ref.counterparty()))
                .append("; page=").append(ref.page() == null ? "-" : ref.page())
                .append('\n');
        }
    }

    private String nullToDash(String value) {
        return StringUtils.hasText(value) ? value.trim() : "-";
    }

    private record SummarySelection(
        boolean used,
        ContextAssemblyHistoryItem historyItem,
        Integer throughTurnNo,
        String status,
        Integer tokenEstimate,
        String degradedReason
    ) {
        static SummarySelection inactive() {
            return new SummarySelection(false, null, null, null, 0, null);
        }
    }
}
