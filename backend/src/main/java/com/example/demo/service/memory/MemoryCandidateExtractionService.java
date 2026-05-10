package com.example.demo.service.memory;

import com.example.demo.model.MemoryEntryResponse;
import com.example.demo.model.MemoryEntryType;
import com.example.demo.service.conversation.StoredConversationRun;
import com.example.demo.service.memory.port.MemoryRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class MemoryCandidateExtractionService {

    private static final Pattern EXPLICIT_MEMORY_MARKER = Pattern.compile(
        "(?iu)(?:запомни|запомнить|remember|for future reference|на будущее|учти на будущее)[:\\s,-]+(.+)"
    );

    private final MemoryRepository repository;
    private final MemorySafetyPolicy safetyPolicy;

    public MemoryCandidateExtractionService(MemoryRepository repository, MemorySafetyPolicy safetyPolicy) {
        this.repository = repository;
        this.safetyPolicy = safetyPolicy;
    }

    public List<MemoryEntryResponse> extractCandidates(StoredConversationRun run, String workspaceKey, String projectKey) {
        return extractCandidates(run, workspaceKey, projectKey, null);
    }

    public List<MemoryEntryResponse> extractCandidates(
        StoredConversationRun run,
        String workspaceKey,
        String projectKey,
        MemoryExtractionLease lease
    ) {
        if (run == null || !"COMPLETED".equals(run.status()) || !StringUtils.hasText(run.userPrompt())) {
            return List.of();
        }
        List<Candidate> candidates = candidatesFromPrompt(run.userPrompt(), workspaceKey, projectKey);
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<MemoryEntryResponse> saved = new ArrayList<>();
        for (Candidate candidate : candidates) {
            MemoryEntryDraft draft = safetyPolicy.candidateDraft(
                candidate.type(),
                candidate.content(),
                candidate.workspaceKey(),
                candidate.projectKey(),
                BigDecimal.valueOf(0.70),
                Map.of("extractor", "explicit-marker-v1", "reviewMode", true),
                run.conversationId(),
                run.runId(),
                run.turnNo(),
                run.userPrompt()
            );
            saved.add(repository.createCandidate(draft, java.time.Instant.now(), lease));
        }
        return saved;
    }

    private List<Candidate> candidatesFromPrompt(String prompt, String workspaceKey, String projectKey) {
        Matcher matcher = EXPLICIT_MEMORY_MARKER.matcher(prompt);
        if (!matcher.find()) {
            return List.of();
        }
        String content = matcher.group(1);
        if (!StringUtils.hasText(content) || safetyPolicy.isForbiddenCandidate(content)) {
            return List.of();
        }
        MemoryEntryType type = inferType(content, workspaceKey, projectKey);
        String scopedWorkspace = type == MemoryEntryType.WORKSPACE_NOTE || type == MemoryEntryType.PROJECT_NOTE
            ? workspaceKey
            : null;
        String scopedProject = type == MemoryEntryType.PROJECT_NOTE ? projectKey : null;
        return List.of(new Candidate(type, content, scopedWorkspace, scopedProject));
    }

    private MemoryEntryType inferType(String content, String workspaceKey, String projectKey) {
        String lower = content.toLowerCase(Locale.ROOT);
        if (lower.contains("зови") || lower.contains("называй") || lower.contains("меня зовут")
            || lower.contains("call me") || lower.contains("my name is")) {
            return MemoryEntryType.USER_ALIAS;
        }
        if (StringUtils.hasText(projectKey)
            && (lower.contains("проект") || lower.contains("project note") || lower.contains("по проекту"))) {
            return MemoryEntryType.PROJECT_NOTE;
        }
        if (StringUtils.hasText(workspaceKey)
            && (lower.contains("workspace") || lower.contains("рабоч") || lower.contains("пространств"))) {
            return MemoryEntryType.WORKSPACE_NOTE;
        }
        if (lower.contains("предпоч") || lower.contains("prefer") || lower.contains("отвечай")
            || lower.contains("формат") || lower.contains("style")) {
            return MemoryEntryType.USER_PREFERENCE;
        }
        return MemoryEntryType.PINNED_USER_FACT;
    }

    private record Candidate(
        MemoryEntryType type,
        String content,
        String workspaceKey,
        String projectKey
    ) {
    }
}
