package com.example.demo.service.context;

import com.example.demo.config.ContextProperties;
import com.example.demo.error.ApplicationException;
import com.example.demo.error.ErrorType;
import com.example.demo.model.ChatMode;
import com.example.demo.model.ContextAssemblyDroppedMemoryItem;
import com.example.demo.model.ChatRunContextDetail;
import com.example.demo.model.ChatRunTraceDetail;
import com.example.demo.model.ContextAssemblyMemoryItem;
import com.example.demo.model.ContextAssemblyHistoryItem;
import com.example.demo.model.ContextAssemblySnapshotDetail;
import com.example.demo.service.audit.port.ChatRunTraceRepository;
import com.example.demo.service.context.port.ContextAssemblyTraceRepository;
import com.example.demo.service.conversation.StoredConversationRun;
import com.example.demo.service.conversation.port.ConversationRepository;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ContextAssemblyQueryService {

    private static final int PREVIEW_LIMIT = 320;

    private final ContextProperties contextProperties;
    private final ContextAssemblyTraceRepository repository;
    private final ConversationRepository conversationRepository;
    private final ChatRunTraceRepository traceRepository;

    public ContextAssemblyQueryService(
        ContextProperties contextProperties,
        ContextAssemblyTraceRepository repository,
        ConversationRepository conversationRepository,
        ChatRunTraceRepository traceRepository
    ) {
        this.contextProperties = contextProperties;
        this.repository = repository;
        this.conversationRepository = conversationRepository;
        this.traceRepository = traceRepository;
    }

    public ChatRunContextDetail getByRunId(String runId) {
        String normalizedRunId = requireUuid(runId);
        ContextAssemblySnapshotDetail snapshot = repository.findByRunId(normalizedRunId).orElse(null);
        if (snapshot == null) {
            return expiredOrNotFound(runId, normalizedRunId);
        }
        return detailFrom(snapshot);
    }

    private String requireUuid(String value) {
        try {
            return UUID.fromString(value).toString();
        } catch (RuntimeException exception) {
            throw new ApplicationException(
                ErrorType.INVALID_REQUEST,
                "context_assembly.invalid_id",
                "Chat run id must be a valid UUID",
                exception
            );
        }
    }

    private ChatRunContextDetail expiredOrNotFound(String rawRunId, String runId) {
        return conversationRepository.findRunByRunId(runId)
            .map(run -> missingSnapshotDetail(rawRunId, run))
            .orElseThrow(() -> new ApplicationException(
                ErrorType.NOT_FOUND,
                "context_assembly.not_found",
                "Context assembly snapshot for chat run '" + rawRunId + "' does not exist"
            ));
    }

    private ChatRunContextDetail missingSnapshotDetail(String rawRunId, StoredConversationRun run) {
        String status = missingSnapshotStatus(run);
        String reasonCode = switch (status) {
            case "EXPIRED" -> "context_assembly.expired";
            case "DISABLED" -> "context_assembly.disabled";
            default -> "context_assembly.not_available";
        };
        String reasonMessage = switch (status) {
            case "EXPIRED" -> "Context assembly snapshot for chat run '" + rawRunId + "' has expired or was removed by retention.";
            case "DISABLED" -> "Context assembly is disabled for this runtime.";
            default -> "Context assembly was not created for chat run '" + rawRunId + "'.";
        };
        return new ChatRunContextDetail(
            status,
            run.runId(),
            run.conversationId(),
            run.turnNo(),
            run.contextAssemblyId(),
            null,
            featureState(),
            preview(run.userPrompt()),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            Map.of(),
            null,
            new ChatRunContextDetail.ContextSummaryState(false, null, null, null, null),
            new ChatRunContextDetail.ContextMemoryState(contextProperties.isLongTermMemoryEnabled(), false, "unavailable", reasonCode),
            new ChatRunContextDetail.ContextInspectorTokenBudget(0, 0, 0, 0, 0, 0, 0, 0),
            links(run.runId(), run.conversationId()),
            reasonCode,
            reasonMessage
        );
    }

    private String missingSnapshotStatus(StoredConversationRun run) {
        if ("EXPIRED".equals(run.contextAssemblyStatus())) {
            return "EXPIRED";
        }
        if (!contextProperties.isEnabled()) {
            return "DISABLED";
        }
        return "NOT_AVAILABLE";
    }

    private ChatRunContextDetail detailFrom(ContextAssemblySnapshotDetail snapshot) {
        Map<String, StoredConversationRun> runs = runsById(snapshot.conversationId());
        List<ChatRunContextDetail.ContextHistoryItem> selectedHistory = historyItems(snapshot, runs);
        boolean degraded = Boolean.TRUE.equals(snapshot.degradedMode());
        return new ChatRunContextDetail(
            degraded ? "DEGRADED" : "AVAILABLE",
            snapshot.runId(),
            snapshot.conversationId(),
            snapshot.turnNo(),
            snapshot.id(),
            snapshot.createdAt(),
            featureState(),
            preview(snapshot.originalPrompt()),
            selectedHistory,
            droppedItems(snapshot),
            memoryItems(snapshot.selectedMemory()),
            droppedMemoryItems(snapshot.droppedMemory()),
            snapshot.stickyResolution(),
            snapshot.retrievalQueryResolution(),
            new ChatRunContextDetail.ContextSummaryState(
                snapshot.summaryUsed(),
                snapshot.summaryStatus(),
                snapshot.summaryThroughTurnNo(),
                snapshot.summaryTokenEstimate(),
                snapshot.summaryDegradedReason()
            ),
            new ChatRunContextDetail.ContextMemoryState(
                contextProperties.isLongTermMemoryEnabled(),
                snapshot.memoryStatus() != null && !"disabled".equals(snapshot.memoryStatus()),
                snapshot.memoryStatus(),
                snapshot.memoryDegradedReason()
            ),
            tokenBudget(snapshot),
            links(snapshot.runId(), snapshot.conversationId()),
            degraded ? "context_assembly.degraded" : null,
            degraded ? "Context assembly ran in degraded mode for this chat run." : null
        );
    }

    private ChatRunContextDetail.ContextFeatureState featureState() {
        return new ChatRunContextDetail.ContextFeatureState(
            contextProperties.isEnabled(),
            contextProperties.isConversationsEnabled(),
            contextProperties.isHistoryEnabled(),
            contextProperties.isStickyStateEnabled(),
            contextProperties.isRetrievalQueryResolutionEnabled(),
            contextProperties.isSummaryEnabled(),
            contextProperties.isLongTermMemoryEnabled()
        );
    }

    private Map<String, StoredConversationRun> runsById(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return Map.of();
        }
        Map<String, StoredConversationRun> runs = new LinkedHashMap<>();
        for (StoredConversationRun run : conversationRepository.listRuns(conversationId)) {
            runs.put(run.runId(), run);
        }
        return runs;
    }

    private List<ChatRunContextDetail.ContextHistoryItem> historyItems(
        ContextAssemblySnapshotDetail snapshot,
        Map<String, StoredConversationRun> runs
    ) {
        Map<String, HistoryPreview> previews = new LinkedHashMap<>();
        for (ContextAssemblyHistoryItem item : snapshot.selectedHistory()) {
            if (!StringUtils.hasText(item.runId())) {
                continue;
            }
            HistoryPreview preview = previews.computeIfAbsent(
                item.runId(),
                ignored -> new HistoryPreview(item.runId(), item.turnNo())
            );
            preview.add(item);
        }
        return previews.values().stream()
            .sorted(Comparator.comparing(HistoryPreview::turnNo, Comparator.nullsLast(Comparator.naturalOrder())))
            .map(preview -> {
                StoredConversationRun run = runs.get(preview.runId());
                ChatMode mode = modeOf(preview.runId(), snapshot.assemblyMode());
                return new ChatRunContextDetail.ContextHistoryItem(
                    preview.runId(),
                    preview.turnNo(),
                    mode,
                    preview.promptPreview(),
                    preview.answerPreview(),
                    run == null ? null : run.status(),
                    preview.tokenEstimate(),
                    links(preview.runId(), snapshot.conversationId())
                );
            })
            .toList();
    }

    private List<ChatRunContextDetail.ContextDroppedItem> droppedItems(ContextAssemblySnapshotDetail snapshot) {
        return snapshot.droppedItems().stream()
            .map(item -> new ChatRunContextDetail.ContextDroppedItem(
                "history",
                item.runId(),
                item.turnNo(),
                item.reason(),
                item.estimatedTokens(),
                links(item.runId(), snapshot.conversationId())
            ))
            .toList();
    }

    private List<ChatRunContextDetail.ContextMemoryItem> memoryItems(List<ContextAssemblyMemoryItem> items) {
        return items.stream()
            .map(item -> new ChatRunContextDetail.ContextMemoryItem(
                item.id(),
                item.entryType(),
                preview(item.contentText()),
                item.workspaceKey(),
                item.projectKey(),
                item.pinned(),
                item.estimatedTokens()
            ))
            .toList();
    }

    private List<ChatRunContextDetail.ContextDroppedMemoryItem> droppedMemoryItems(
        List<ContextAssemblyDroppedMemoryItem> items
    ) {
        return items.stream()
            .map(item -> new ChatRunContextDetail.ContextDroppedMemoryItem(
                item.id(),
                item.entryType(),
                item.workspaceKey(),
                item.projectKey(),
                item.pinned(),
                item.reason(),
                item.estimatedTokens()
            ))
            .toList();
    }

    private ChatRunContextDetail.ContextInspectorTokenBudget tokenBudget(ContextAssemblySnapshotDetail snapshot) {
        int max = snapshot.tokenBudget() == null || snapshot.tokenBudget().maxHistoryTokens() == null
            ? 0
            : snapshot.tokenBudget().maxHistoryTokens();
        int history = snapshot.tokenBudget() == null || snapshot.tokenBudget().selectedHistoryTokens() == null
            ? 0
            : snapshot.tokenBudget().selectedHistoryTokens();
        int summary = Boolean.TRUE.equals(snapshot.summaryUsed()) && snapshot.summaryTokenEstimate() != null
            ? snapshot.summaryTokenEstimate()
            : 0;
        int memory = snapshot.selectedMemory().stream()
            .map(ContextAssemblyMemoryItem::estimatedTokens)
            .filter(value -> value != null)
            .mapToInt(Integer::intValue)
            .sum();
        int retrieval = 0;
        int dropped = snapshot.tokenBudget() == null || snapshot.tokenBudget().droppedHistoryTokens() == null
            ? 0
            : snapshot.tokenBudget().droppedHistoryTokens();
        int droppedMemory = snapshot.droppedMemory().stream()
            .map(ContextAssemblyDroppedMemoryItem::estimatedTokens)
            .filter(value -> value != null)
            .mapToInt(Integer::intValue)
            .sum();
        int used = history + summary + memory + retrieval;
        int remaining = Math.max(0, max - used);
        return new ChatRunContextDetail.ContextInspectorTokenBudget(max, used, remaining, history, summary, memory, retrieval, dropped + droppedMemory);
    }

    private ChatMode modeOf(String runId, ChatMode fallback) {
        try {
            return traceRepository.findTrace(runId)
                .map(ChatRunTraceDetail::mode)
                .orElse(fallback);
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private ChatRunContextDetail.ContextLinks links(String runId, String conversationId) {
        if (!StringUtils.hasText(runId)) {
            return null;
        }
        return new ChatRunContextDetail.ContextLinks(
            "/api/chat-runs/" + runId,
            "/api/chat-runs/" + runId + "/status",
            "/api/chat-runs/" + runId + "/trace",
            "/api/chat-runs/" + runId + "/result",
            StringUtils.hasText(conversationId) ? "/api/conversations/" + conversationId + "/runs" : null
        );
    }

    private static String preview(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= PREVIEW_LIMIT) {
            return normalized;
        }
        return normalized.substring(0, PREVIEW_LIMIT) + "...";
    }

    private static final class HistoryPreview {

        private final String runId;
        private final Integer turnNo;
        private String promptPreview;
        private String answerPreview;
        private int tokenEstimate;

        private HistoryPreview(String runId, Integer turnNo) {
            this.runId = runId;
            this.turnNo = turnNo;
        }

        private void add(ContextAssemblyHistoryItem item) {
            tokenEstimate += item.estimatedTokens() == null ? 0 : item.estimatedTokens();
            if ("assistant".equals(item.role())) {
                answerPreview = preview(item.content());
            } else if (promptPreview == null) {
                promptPreview = preview(item.content());
            }
        }

        private String runId() {
            return runId;
        }

        private Integer turnNo() {
            return turnNo;
        }

        private String promptPreview() {
            return promptPreview;
        }

        private String answerPreview() {
            return answerPreview;
        }

        private Integer tokenEstimate() {
            return tokenEstimate;
        }
    }
}
