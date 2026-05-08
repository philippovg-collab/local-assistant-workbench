package com.example.demo.service;

import com.example.demo.config.ChatAuditProperties;
import com.example.demo.model.ChatExecutionRequest;
import com.example.demo.model.ChatRunMessage;
import com.example.demo.model.InstructionTraceEntry;
import com.example.demo.model.PromptPolicySnapshot;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class AuditRedactionService {

    private static final String REDACTED = "[REDACTED]";
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
            redactStoredText(request.temporaryInstruction())
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

    private record Replacement(Pattern pattern, String replacement) {
    }
}
