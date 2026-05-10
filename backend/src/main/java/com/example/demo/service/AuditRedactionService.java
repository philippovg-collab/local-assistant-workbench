package com.example.demo.service;

import com.example.demo.config.ChatAuditProperties;
import com.example.demo.model.ChatExecutionRequest;
import com.example.demo.model.ChatRunMessage;
import com.example.demo.model.ContextAssemblyHistoryItem;
import com.example.demo.model.ContextAssemblyMemoryItem;
import com.example.demo.model.ContextAssemblySnapshotDetail;
import com.example.demo.model.ConversationSummarySourceRef;
import com.example.demo.model.InstructionTraceEntry;
import com.example.demo.model.PromptPolicySnapshot;
import com.example.demo.model.RetrievalQueryReferencedSource;
import com.example.demo.model.RetrievalQueryResolution;
import com.example.demo.service.context.ConversationSummaryPayload;
import com.example.demo.service.memory.MemoryEntryDraft;
import java.util.ArrayList;
import java.lang.reflect.Array;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class AuditRedactionService {

    private static final String REDACTED = "[REDACTED]";
    private static final Pattern SENSITIVE_KEY_PATTERN = Pattern.compile(
        "(?i).*(password|passwd|pwd|api[_-]?key|apikey|x-api-key|access[_-]?token|refresh[_-]?token|authorization|cookie|set-cookie|secret|token).*"
    );
    private static final List<Replacement> SECRET_PATTERNS = List.of(
        new Replacement(
            Pattern.compile("(?i)(Authorization\\s*:\\s*Bearer\\s+)[^\\s,;]+"),
            "$1" + REDACTED
        ),
        new Replacement(
            Pattern.compile("(?i)(Authorization\\s*:\\s*Basic\\s+)[A-Za-z0-9+/=._~-]+"),
            "$1" + REDACTED
        ),
        new Replacement(
            Pattern.compile("(?i)\\b(Bearer\\s+)[A-Za-z0-9._~+\\-/]+=*"),
            "$1" + REDACTED
        ),
        new Replacement(
            Pattern.compile("(?i)((?:Cookie|Set-Cookie)\\s*:\\s*)[^\\r\\n]+"),
            "$1" + REDACTED
        ),
        new Replacement(
            Pattern.compile("(?i)(\"(?:password|passwd|pwd|api[_-]?key|apikey|x-api-key|access[_-]?token|refresh[_-]?token|secret|token)\"\\s*:\\s*\")[^\"]*(\")"),
            "$1" + REDACTED + "$2"
        ),
        new Replacement(
            Pattern.compile("(?i)\\b(password|passwd|pwd|api[_-]?key|apikey|x-api-key|access[_-]?token|refresh[_-]?token|secret|token)(\\s*[=:]\\s*)([^\\s,;&\"']+)"),
            "$1$2" + REDACTED
        )
    );

    private final ChatAuditProperties properties;

    public AuditRedactionService(ChatAuditProperties properties) {
        this.properties = properties;
    }

    public ChatExecutionRequest redactRequest(ChatExecutionRequest request) {
        if (request == null) {
            return null;
        }
        return new ChatExecutionRequest(
            request.mode(),
            request.model(),
            redactStoredText(request.prompt()),
            redactStoredText(request.systemPrompt()),
            request.instructionIds(),
            request.answerMode(),
            request.knowledgeScope(),
            request.instructionWorkspaceKey(),
            request.retrievalFilters(),
            request.dismissedRetrievalHintKeys(),
            request.scenarioInstructionIds(),
            redactStoredText(request.temporaryInstruction()),
            request.conversationId(),
            request.parentRunId(),
            request.clientTurnId(),
            request.persistConversation(),
            request.contextDebug(),
            request.contextOptions()
        );
    }

    public PromptPolicySnapshot redactPromptSnapshot(PromptPolicySnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        return new PromptPolicySnapshot(
            redactStoredText(snapshot.baseSystemPrompt()),
            redactStoredText(snapshot.systemInstructionsText()),
            redactStoredText(snapshot.safetyInstructionsText()),
            redactStoredText(snapshot.contextInstructionsText()),
            redactStoredText(snapshot.userInstructionsText()),
            redactStoredText(snapshot.temporaryInstructionText()),
            redactStoredText(snapshot.answerModeBlockText()),
            redactStoredText(snapshot.groundingBlockText()),
            redactStoredText(snapshot.resolvedSystemPrompt()),
            redactRequestMessages(snapshot.messages()),
            snapshot.promptHash(),
            redactInstructionTrace(snapshot.instructionTrace()),
            snapshot.knowledgeScopeResolved(),
            snapshot.groundingRulesApplied()
        );
    }

    public List<ChatRunMessage> redactRequestMessages(List<ChatRunMessage> messages) {
        if (!properties.isStoreRequestMessages()) {
            return List.of();
        }
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        return messages.stream()
            .map(message -> new ChatRunMessage(message.role(), redactStoredText(message.content())))
            .toList();
    }

    public String redactRawLlmText(String value) {
        if (!properties.isStoreRawLlmResponse()) {
            return null;
        }
        return redactStoredText(value);
    }

    public String redactStoredText(String value) {
        if (value == null) {
            return null;
        }
        String redacted = properties.isRedactionEnabled() ? maskSecrets(value) : value;
        return truncate(redacted);
    }

    public Map<String, Object> redactMetadata(Map<String, ?> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> redacted = new LinkedHashMap<>();
        metadata.forEach((key, value) -> {
            String safeKey = redactStoredText(key);
            if (safeKey != null) {
                redacted.put(safeKey, redactValue(value, key));
            }
        });
        return redacted;
    }

    public ContextAssemblySnapshotDetail redactContextAssemblySnapshot(ContextAssemblySnapshotDetail snapshot) {
        if (snapshot == null) {
            return null;
        }
        return new ContextAssemblySnapshotDetail(
            snapshot.id(),
            snapshot.runId(),
            snapshot.conversationId(),
            snapshot.turnNo(),
            snapshot.assemblyMode(),
            redactStoredText(snapshot.originalPrompt()),
            redactStoredText(snapshot.resolvedRetrievalQuery()),
            redactContextHistory(snapshot.selectedHistory()),
            snapshot.droppedItems(),
            redactContextMemory(snapshot.selectedMemory()),
            snapshot.droppedMemory(),
            snapshot.tokenBudget(),
            snapshot.finalMessagesHash(),
            snapshot.degradedMode(),
            redactRetrievalQueryResolution(snapshot.retrievalQueryResolution()),
            redactMetadata(snapshot.stickyResolution()),
            snapshot.summaryUsed(),
            snapshot.summaryThroughTurnNo(),
            snapshot.summaryStatus(),
            snapshot.summaryTokenEstimate(),
            redactStoredText(snapshot.summaryDegradedReason()),
            snapshot.memoryStatus(),
            redactStoredText(snapshot.memoryDegradedReason()),
            snapshot.createdAt()
        );
    }

    public ConversationSummaryPayload redactConversationSummaryPayload(ConversationSummaryPayload payload) {
        if (payload == null) {
            return null;
        }
        return new ConversationSummaryPayload(
            redactStoredText(payload.summaryText()),
            redactTextList(payload.facts()),
            redactTextList(payload.activeEntities()),
            redactSummarySourceRefs(payload.sourceRefs()),
            payload.coveredTurnNos()
        );
    }

    public MemoryEntryDraft redactMemoryEntryDraft(MemoryEntryDraft draft) {
        if (draft == null) {
            return null;
        }
        return new MemoryEntryDraft(
            draft.entryType(),
            redactStoredText(draft.contentText()),
            redactStoredText(draft.normalizedKey()),
            redactStoredText(draft.workspaceKey()),
            redactStoredText(draft.projectKey()),
            draft.pinned(),
            draft.confidence(),
            nonNullMetadata(redactMetadata(draft.provenance())),
            draft.sourceConversationId(),
            draft.sourceRunId(),
            draft.sourceTurnNo(),
            redactStoredText(draft.sourceTextPreview()),
            draft.sourceTextHash()
        );
    }

    public Object redactValue(Object value) {
        return redactValue(value, null);
    }

    private Object redactValue(Object value, String sourceKey) {
        if (isSensitiveKey(sourceKey)) {
            return REDACTED;
        }
        if (value == null
            || value instanceof Number
            || value instanceof Boolean) {
            return value;
        }
        if (value instanceof CharSequence text) {
            return redactStoredText(text.toString());
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> redacted = new LinkedHashMap<>();
            map.forEach((key, nestedValue) -> {
                String rawKey = String.valueOf(key);
                String safeKey = redactStoredText(rawKey);
                if (safeKey != null) {
                    redacted.put(safeKey, redactValue(nestedValue, rawKey));
                }
            });
            return redacted;
        }
        if (value instanceof Iterable<?> iterable) {
            return streamOf(iterable)
                .map(item -> redactValue(item, null))
                .toList();
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> redacted = new java.util.ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                redacted.add(redactValue(Array.get(value, index), null));
            }
            return redacted;
        }
        return redactStoredText(String.valueOf(value));
    }

    private boolean isSensitiveKey(String key) {
        return key != null && SENSITIVE_KEY_PATTERN.matcher(key).matches();
    }

    private List<InstructionTraceEntry> redactInstructionTrace(List<InstructionTraceEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        return entries.stream()
            .map(entry -> new InstructionTraceEntry(
                entry.instructionId(),
                entry.title(),
                entry.category(),
                entry.scopeLevel(),
                entry.scopeTargetId(),
                entry.revision(),
                entry.active(),
                entry.temporary(),
                redactStoredText(entry.contentPreview())
            ))
            .toList();
    }

    private List<ContextAssemblyHistoryItem> redactContextHistory(List<ContextAssemblyHistoryItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream()
            .map(item -> new ContextAssemblyHistoryItem(
                item.runId(),
                item.turnNo(),
                item.role(),
                redactStoredText(item.content()),
                item.estimatedTokens(),
                item.createdAt()
            ))
            .toList();
    }

    private List<ContextAssemblyMemoryItem> redactContextMemory(List<ContextAssemblyMemoryItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream()
            .map(item -> new ContextAssemblyMemoryItem(
                item.id(),
                item.entryType(),
                redactStoredText(item.contentText()),
                redactStoredText(item.workspaceKey()),
                redactStoredText(item.projectKey()),
                item.pinned(),
                item.confidence(),
                item.estimatedTokens(),
                item.updatedAt()
            ))
            .toList();
    }

    private RetrievalQueryResolution redactRetrievalQueryResolution(RetrievalQueryResolution resolution) {
        if (resolution == null) {
            return null;
        }
        return new RetrievalQueryResolution(
            redactStoredText(resolution.originalQuery()),
            redactStoredText(resolution.queryForRetrieval()),
            redactStoredText(resolution.resolvedQuery()),
            resolution.decision(),
            resolution.confidence(),
            redactTextList(resolution.markers()),
            resolution.referencedRunIds(),
            redactReferencedSources(resolution.referencedSources()),
            resolution.degraded(),
            redactStoredText(resolution.degradedReason())
        );
    }

    private List<RetrievalQueryReferencedSource> redactReferencedSources(List<RetrievalQueryReferencedSource> sources) {
        if (sources == null || sources.isEmpty()) {
            return List.of();
        }
        return sources.stream()
            .map(source -> new RetrievalQueryReferencedSource(
                source.sourceIndex(),
                source.documentIndex(),
                source.materialId(),
                redactStoredText(source.title()),
                redactStoredText(source.documentNumber()),
                redactStoredText(source.project()),
                redactStoredText(source.counterparty()),
                source.page()
            ))
            .toList();
    }

    private List<ConversationSummarySourceRef> redactSummarySourceRefs(List<ConversationSummarySourceRef> refs) {
        if (refs == null || refs.isEmpty()) {
            return List.of();
        }
        return refs.stream()
            .map(ref -> new ConversationSummarySourceRef(
                ref.materialId(),
                redactStoredText(ref.title()),
                redactStoredText(ref.documentNumber()),
                redactStoredText(ref.project()),
                redactStoredText(ref.counterparty()),
                ref.page()
            ))
            .toList();
    }

    private List<String> redactTextList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> redacted = new ArrayList<>(values.size());
        for (String value : values) {
            String safe = redactStoredText(value);
            if (safe != null) {
                redacted.add(safe);
            }
        }
        return List.copyOf(redacted);
    }

    private Map<String, Object> nonNullMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> cleaned = new LinkedHashMap<>();
        metadata.forEach((key, value) -> {
            if (key != null && value != null) {
                cleaned.put(key, value);
            }
        });
        return cleaned;
    }

    private String maskSecrets(String value) {
        String result = value;
        for (Replacement replacement : SECRET_PATTERNS) {
            result = replacement.pattern().matcher(result).replaceAll(replacement.replacement());
        }
        return result;
    }

    private String truncate(String value) {
        int maxChars = properties.getMaxStoredTextChars();
        if (maxChars < 0 || value.length() <= maxChars) {
            return value;
        }
        if (maxChars == 0) {
            return "";
        }
        String marker = "[truncated]";
        if (maxChars <= marker.length()) {
            return marker.substring(0, maxChars);
        }
        return value.substring(0, maxChars - marker.length()) + marker;
    }

    private java.util.stream.Stream<?> streamOf(Iterable<?> iterable) {
        return java.util.stream.StreamSupport.stream(iterable.spliterator(), false);
    }

    private record Replacement(Pattern pattern, String replacement) {
    }
}
