package com.example.demo.service.memory;

import com.example.demo.error.ApplicationException;
import com.example.demo.error.ErrorType;
import com.example.demo.model.MemoryEntryType;
import com.example.demo.service.AuditRedactionService;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class MemorySafetyPolicy {

    private static final int MAX_CONTENT_CHARS = 4000;
    private static final int MAX_PREVIEW_CHARS = 360;
    private static final Pattern SECRET_PATTERN = Pattern.compile(
        "(?i)(password|passwd|token|api[_ -]?key|secret|credential|пароль|токен|ключ доступа)\\s*[:=]"
    );
    private static final Pattern SOURCE_FACT_PATTERN = Pattern.compile(
        "(?i)(retrieved context|source excerpt|chunk|document says|в документе|по документу|источник|чанк|тариф|закон|стоимость|цена)"
    );
    private static final Pattern INFERRED_PRIVATE_ATTRIBUTE_PATTERN = Pattern.compile(
        "(?iu)(?:infer|guess|probably|seems|appears|думаю|похоже|кажется|вероятно).{0,80}"
            + "(?:health|religion|politic|ethnic|sexual|medical|disability|здоров|религи|полит|этнич|сексуал|медицин|инвалид)"
    );

    private final AuditRedactionService redactionService;

    @Autowired
    public MemorySafetyPolicy(AuditRedactionService redactionService) {
        this.redactionService = redactionService;
    }

    public MemorySafetyPolicy() {
        this(new AuditRedactionService(new com.example.demo.config.ChatAuditProperties()));
    }

    public MemoryEntryDraft manualDraft(
        MemoryEntryType type,
        String contentText,
        String normalizedKey,
        String workspaceKey,
        String projectKey,
        boolean pinned,
        BigDecimal confidence,
        Map<String, Object> provenance
    ) {
        return draft(
            type,
            contentText,
            normalizedKey,
            workspaceKey,
            projectKey,
            pinned,
            confidence,
            safeProvenance(provenance),
            null,
            null,
            null,
            null
        );
    }

    public MemoryEntryDraft candidateDraft(
        MemoryEntryType type,
        String contentText,
        String workspaceKey,
        String projectKey,
        BigDecimal confidence,
        Map<String, Object> provenance,
        String sourceConversationId,
        String sourceRunId,
        Integer sourceTurnNo,
        String sourcePrompt
    ) {
        if (isForbiddenCandidate(contentText) || isForbiddenCandidate(sourcePrompt)) {
            throw new ApplicationException(
                ErrorType.INVALID_REQUEST,
                "memory.candidate_forbidden",
                "Memory candidates cannot contain source facts, retrieved chunks, credentials, or inferred private attributes."
            );
        }
        return draft(
            type,
            contentText,
            null,
            workspaceKey,
            projectKey,
            false,
            confidence,
            safeProvenance(provenance),
            sourceConversationId,
            sourceRunId,
            sourceTurnNo,
            sourcePrompt
        );
    }

    public String preview(String sourceText) {
        String normalized = normalizeWhitespace(sourceText);
        if (normalized == null) {
            return null;
        }
        String redacted = redactionService.redactStoredText(normalized);
        return redacted.length() <= MAX_PREVIEW_CHARS
            ? redacted
            : redacted.substring(0, MAX_PREVIEW_CHARS) + "...";
    }

    public String hash(String sourceText) {
        String normalized = normalizeWhitespace(sourceText);
        if (normalized == null) {
            return null;
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                normalized.getBytes(StandardCharsets.UTF_8)
            ));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public boolean isForbiddenCandidate(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        return SECRET_PATTERN.matcher(text).find()
            || SOURCE_FACT_PATTERN.matcher(text).find()
            || INFERRED_PRIVATE_ATTRIBUTE_PATTERN.matcher(text).find();
    }

    private MemoryEntryDraft draft(
        MemoryEntryType type,
        String contentText,
        String normalizedKey,
        String workspaceKey,
        String projectKey,
        boolean pinned,
        BigDecimal confidence,
        Map<String, Object> provenance,
        String sourceConversationId,
        String sourceRunId,
        Integer sourceTurnNo,
        String sourcePrompt
    ) {
        MemoryEntryType effectiveType = requireType(type);
        String content = requireContent(contentText);
        String key = StringUtils.hasText(normalizedKey)
            ? normalizeKey(normalizedKey)
            : normalizeKey(effectiveType.name() + ":" + content);
        return new MemoryEntryDraft(
            effectiveType,
            content,
            key,
            trimToNull(workspaceKey),
            trimToNull(projectKey),
            pinned,
            confidence,
            provenance,
            trimToNull(sourceConversationId),
            trimToNull(sourceRunId),
            sourceTurnNo,
            preview(sourcePrompt),
            hash(sourcePrompt)
        );
    }

    private MemoryEntryType requireType(MemoryEntryType type) {
        if (type == null) {
            throw new ApplicationException(
                ErrorType.INVALID_REQUEST,
                "memory.type_required",
                "Memory entry type is required"
            );
        }
        return type;
    }

    private String requireContent(String contentText) {
        String content = redactionService.redactStoredText(normalizeWhitespace(contentText));
        if (!StringUtils.hasText(content)) {
            throw new ApplicationException(
                ErrorType.INVALID_REQUEST,
                "memory.content_required",
                "Memory content is required"
            );
        }
        if (content.length() > MAX_CONTENT_CHARS) {
            throw new ApplicationException(
                ErrorType.INVALID_REQUEST,
                "memory.content_too_large",
                "Memory content exceeds the maximum supported length"
            );
        }
        if (SECRET_PATTERN.matcher(content).find()) {
            throw new ApplicationException(
                ErrorType.INVALID_REQUEST,
                "memory.content_forbidden",
                "Memory content cannot store credentials, tokens, passwords, or secrets"
            );
        }
        if (SOURCE_FACT_PATTERN.matcher(content).find()) {
            throw new ApplicationException(
                ErrorType.INVALID_REQUEST,
                "memory.content_forbidden",
                "Memory content cannot store KB facts, source excerpts, retrieved chunks, prices, tariffs, or legal facts"
            );
        }
        return content;
    }

    private Map<String, Object> safeProvenance(Map<String, Object> provenance) {
        Map<String, Object> redacted = redactionService.redactMetadata(provenance);
        if (redacted.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> cleaned = new LinkedHashMap<>();
        redacted.forEach((key, value) -> {
            if (key != null && value != null) {
                cleaned.put(key, value);
            }
        });
        return cleaned;
    }

    private String normalizeKey(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", "-")
            .replaceAll("(^-+|-+$)", "");
        if (normalized.isBlank()) {
            return "memory";
        }
        return normalized.length() <= 240 ? normalized : normalized.substring(0, 240);
    }

    private static String normalizeWhitespace(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
