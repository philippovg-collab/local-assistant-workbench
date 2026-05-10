package com.example.demo.service.context;

import com.example.demo.config.ContextProperties;
import com.example.demo.model.ChatExecutionRequest;
import com.example.demo.model.ContextAssemblyDroppedMemoryItem;
import com.example.demo.model.ContextAssemblyMemoryItem;
import com.example.demo.model.MemoryEntryResponse;
import com.example.demo.service.context.ContextTokenBudgeter.Budget;
import com.example.demo.service.memory.MemorySelection;
import com.example.demo.service.memory.port.MemoryRepository;
import com.example.demo.service.conversation.StoredConversationRun;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class MemorySelector {

    private final ContextProperties properties;
    private final MemoryRepository memoryRepository;
    private final ContextTokenBudgeter tokenBudgeter;

    public MemorySelector(
        ContextProperties properties,
        MemoryRepository memoryRepository,
        ContextTokenBudgeter tokenBudgeter
    ) {
        this.properties = properties;
        this.memoryRepository = memoryRepository;
        this.tokenBudgeter = tokenBudgeter;
    }

    public MemorySelection select(
        ChatExecutionRequest request,
        StoredConversationRun currentRun,
        Budget budget,
        int consumedTokens
    ) {
        boolean requested = request != null
            && request.contextOptions() != null
            && Boolean.TRUE.equals(request.contextOptions().useLongTermMemory());
        if (!properties.isLongTermMemoryEnabled()) {
            return new MemorySelection(requested, "disabled", "memory_flag_disabled", List.of(), List.of());
        }
        if (!requested) {
            return MemorySelection.disabled("request_disabled");
        }
        if (currentRun == null) {
            return new MemorySelection(true, "degraded", "conversation_run_unavailable", List.of(), List.of());
        }

        String workspaceKey = workspaceKey(request);
        String projectKey = projectKey(request);
        List<MemoryEntryResponse> entries = memoryRepository.selectApprovedForContext(
            workspaceKey,
            projectKey,
            properties.getMemorySelectionLimit()
        );
        if (entries.isEmpty()) {
            return new MemorySelection(true, "empty", null, List.of(), List.of());
        }

        int maxTokens = budget == null ? 0 : budget.maxHistoryTokens();
        int remaining = Math.max(0, maxTokens - Math.max(0, consumedTokens));
        List<ContextAssemblyMemoryItem> selected = new ArrayList<>();
        List<ContextAssemblyDroppedMemoryItem> dropped = new ArrayList<>();
        for (MemoryEntryResponse entry : entries) {
            int tokens = tokenBudgeter.estimateTokens(memoryLine(entry));
            if (maxTokens > 0 && tokens > remaining) {
                dropped.add(drop(entry, "token_budget", tokens));
                continue;
            }
            selected.add(new ContextAssemblyMemoryItem(
                entry.id(),
                entry.entryType(),
                entry.contentText(),
                entry.workspaceKey(),
                entry.projectKey(),
                entry.pinned(),
                entry.confidence(),
                tokens,
                entry.updatedAt()
            ));
            remaining -= tokens;
        }
        return new MemorySelection(true, selected.isEmpty() ? "empty" : "ready", null, selected, dropped);
    }

    private String workspaceKey(ChatExecutionRequest request) {
        if (request == null) {
            return null;
        }
        if (request.knowledgeScope() != null && StringUtils.hasText(request.knowledgeScope().workspaceKey())) {
            return request.knowledgeScope().workspaceKey();
        }
        return StringUtils.hasText(request.instructionWorkspaceKey()) ? request.instructionWorkspaceKey().trim() : null;
    }

    private String projectKey(ChatExecutionRequest request) {
        if (request == null || request.knowledgeScope() == null || request.knowledgeScope().projectKeys().isEmpty()) {
            return null;
        }
        return request.knowledgeScope().projectKeys().getFirst();
    }

    private String memoryLine(MemoryEntryResponse entry) {
        return entry.entryType().name() + ": " + entry.contentText();
    }

    private ContextAssemblyDroppedMemoryItem drop(MemoryEntryResponse entry, String reason, int tokens) {
        return new ContextAssemblyDroppedMemoryItem(
            entry.id(),
            entry.entryType(),
            entry.workspaceKey(),
            entry.projectKey(),
            entry.pinned(),
            reason,
            tokens
        );
    }
}
