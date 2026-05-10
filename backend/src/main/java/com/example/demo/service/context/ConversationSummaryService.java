package com.example.demo.service.context;

import com.example.demo.config.ContextProperties;
import com.example.demo.llm.LlmClient;
import com.example.demo.model.ConversationSummarySourceRef;
import com.example.demo.service.AuditRedactionService;
import com.example.demo.service.context.ConversationSummaryPromptBuilder.SummaryPromptInput;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ConversationSummaryService {

    private final ContextProperties contextProperties;
    private final LlmClient llmClient;
    private final ConversationSummaryPromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;
    private final AuditRedactionService redactionService;

    @Autowired
    public ConversationSummaryService(
        ContextProperties contextProperties,
        LlmClient llmClient,
        ConversationSummaryPromptBuilder promptBuilder,
        ObjectMapper objectMapper,
        AuditRedactionService redactionService
    ) {
        this.contextProperties = contextProperties;
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
        this.objectMapper = objectMapper;
        this.redactionService = redactionService;
    }

    public ConversationSummaryService(
        ContextProperties contextProperties,
        LlmClient llmClient,
        ConversationSummaryPromptBuilder promptBuilder,
        ObjectMapper objectMapper
    ) {
        this(
            contextProperties,
            llmClient,
            promptBuilder,
            objectMapper,
            new AuditRedactionService(new com.example.demo.config.ChatAuditProperties())
        );
    }

    public ConversationSummaryPayload summarize(SummaryPromptInput input, String model) {
        LlmClient.ChatResult result = llmClient.chat(new LlmClient.ChatRequest(
            model,
            promptBuilder.build(input)
        ));
        return parseAndSanitize(result == null ? null : result.answer());
    }

    ConversationSummaryPayload parseAndSanitize(String rawAnswer) {
        if (!StringUtils.hasText(rawAnswer)) {
            throw new IllegalArgumentException("Summary response is empty");
        }
        try {
            SummaryOutput output = objectMapper.readValue(rawAnswer.trim(), SummaryOutput.class);
            return redactionService.redactConversationSummaryPayload(new ConversationSummaryPayload(
                cleanText(output.summaryText(), contextProperties.getSummaryMaxOutputChars()),
                cleanList(output.facts(), contextProperties.getSummaryMaxFacts()),
                cleanList(output.activeEntities(), contextProperties.getSummaryMaxActiveEntities()),
                cleanSourceRefs(output.sourceRefs(), contextProperties.getSummaryMaxSourceRefs()),
                cleanTurnNos(output.coveredTurnNos())
            ));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Summary response must be strict JSON", exception);
        }
    }

    private String cleanText(String value, int maxChars) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String cleaned = stripControls(value).trim();
        if (cleaned.length() <= maxChars) {
            return cleaned;
        }
        return cleaned.substring(0, maxChars).trim();
    }

    private List<String> cleanList(List<String> values, int maxItems) {
        if (values == null || maxItems <= 0) {
            return List.of();
        }
        List<String> cleaned = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String value : values) {
            String item = cleanText(value, 400);
            if (!StringUtils.hasText(item)) {
                continue;
            }
            String key = item.toLowerCase(Locale.ROOT);
            if (seen.add(key)) {
                cleaned.add(item);
            }
            if (cleaned.size() >= maxItems) {
                break;
            }
        }
        return cleaned;
    }

    private List<ConversationSummarySourceRef> cleanSourceRefs(
        List<ConversationSummarySourceRef> refs,
        int maxItems
    ) {
        if (refs == null || maxItems <= 0) {
            return List.of();
        }
        List<ConversationSummarySourceRef> cleaned = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (ConversationSummarySourceRef ref : refs) {
            if (ref == null) {
                continue;
            }
            ConversationSummarySourceRef item = new ConversationSummarySourceRef(
                cleanNullable(ref.materialId(), 120),
                cleanNullable(ref.title(), 300),
                cleanNullable(ref.documentNumber(), 120),
                cleanNullable(ref.project(), 160),
                cleanNullable(ref.counterparty(), 160),
                ref.page()
            );
            String key = String.join(
                "|",
                nullToEmpty(item.materialId()),
                nullToEmpty(item.documentNumber()),
                item.page() == null ? "" : item.page().toString()
            );
            if (seen.add(key)) {
                cleaned.add(item);
            }
            if (cleaned.size() >= maxItems) {
                break;
            }
        }
        return cleaned;
    }

    private List<Integer> cleanTurnNos(List<Integer> turnNos) {
        if (turnNos == null) {
            return List.of();
        }
        List<Integer> cleaned = new ArrayList<>();
        Set<Integer> seen = new LinkedHashSet<>();
        for (Integer turnNo : turnNos) {
            if (turnNo == null || turnNo <= 0 || !seen.add(turnNo)) {
                continue;
            }
            cleaned.add(turnNo);
        }
        return cleaned;
    }

    private String cleanNullable(String value, int maxChars) {
        String cleaned = cleanText(value, maxChars);
        return cleaned.isBlank() ? null : cleaned;
    }

    private String stripControls(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '\n' || character == '\r' || character == '\t' || character >= 32) {
                builder.append(character);
            }
        }
        return builder.toString();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private record SummaryOutput(
        String summaryText,
        List<String> facts,
        List<String> activeEntities,
        List<ConversationSummarySourceRef> sourceRefs,
        List<Integer> coveredTurnNos
    ) {
    }
}
