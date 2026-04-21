package com.example.demo.service;

import com.example.demo.api.InputLimits;
import com.example.demo.config.LlmProperties;
import com.example.demo.config.MaterialProperties;
import com.example.demo.llm.LlmClient;
import com.example.demo.llm.LlmTracingClient;
import com.example.demo.service.material.MaterialMetadataHints;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class MaterialAutoTaggingService {

    public static final double LLM_TAG_CONFIDENCE = 0.82d;

    private static final Logger logger = LoggerFactory.getLogger(MaterialAutoTaggingService.class);
    private static final ObjectMapper JSON_MAPPER = JsonMapper.builder().findAndAddModules().build();
    private static final int SHORT_TEXT_LIMIT = 2_000;
    private static final int MEDIUM_TEXT_LIMIT = 20_000;
    private static final int MIN_CONTENT_CHARS = 20;
    private static final int MIN_CONTENT_TOKENS = 3;
    private static final int DEFAULT_MAX_INPUT_CHARS = 16_000;

    private static final Set<String> GENERIC_TAGS = Set.of(
        "and",
        "brief",
        "contract",
        "data",
        "document",
        "draft",
        "file",
        "final",
        "info",
        "information",
        "material",
        "note",
        "other",
        "pdf",
        "policy",
        "report",
        "scan",
        "text",
        "updated",
        "version",
        "договор",
        "документ",
        "документы",
        "информация",
        "материал",
        "материалы",
        "общий",
        "отчет",
        "отчёт",
        "политика",
        "прочее",
        "разное",
        "скан",
        "текст",
        "файл"
    );

    private final LlmTracingClient llmTracingClient;
    private final LlmProperties llmProperties;
    private final MaterialProperties materialProperties;

    public MaterialAutoTaggingService(
        LlmTracingClient llmTracingClient,
        LlmProperties llmProperties,
        MaterialProperties materialProperties
    ) {
        this.llmTracingClient = llmTracingClient;
        this.llmProperties = llmProperties;
        this.materialProperties = materialProperties == null ? new MaterialProperties() : materialProperties;
    }

    public List<String> suggestTags(TaggingRequest request) {
        if (request == null || llmTracingClient == null || !autoTagsProperties().isLlmEnabled()) {
            return List.of();
        }

        String contentText = normalizeText(request.contentText());
        if (!StringUtils.hasText(contentText)
            || contentText.length() < MIN_CONTENT_CHARS
            || tokenCount(contentText) < MIN_CONTENT_TOKENS) {
            return List.of();
        }

        String model = normalizeText(llmProperties == null ? null : llmProperties.getModel());
        if (!StringUtils.hasText(model)) {
            return List.of();
        }

        TagBudget tagBudget = tagBudgetFor(contentText.length());
        String userPrompt = buildUserPrompt(request, contentText, tagBudget);
        LlmClient.ChatRequest chatRequest = new LlmClient.ChatRequest(
            model,
            List.of(
                new LlmClient.Message(
                    "system",
                    "Ты извлекаешь предметные автотеги для корпоративной базы знаний. " +
                        "Отвечай только валидным JSON без markdown."
                ),
                new LlmClient.Message("user", userPrompt)
            )
        );

        try {
            LlmClient.ChatResult result = llmTracingClient.chat(chatRequest);
            List<String> rawTags = parseTags(result == null ? null : result.answer());
            return normalizeTags(rawTags, request.manualTags(), tagBudget.max());
        } catch (RuntimeException exception) {
            logger.warn(
                "LLM material auto-tagging failed; falling back to metadata heuristics: {}",
                exception.getMessage()
            );
            return List.of();
        }
    }

    private MaterialProperties.AutoTagsProperties autoTagsProperties() {
        MaterialProperties.AutoTagsProperties autoTags = materialProperties.getAutoTags();
        return autoTags == null ? new MaterialProperties.AutoTagsProperties() : autoTags;
    }

    private String buildUserPrompt(TaggingRequest request, String contentText, TagBudget tagBudget) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Составь предметные автотеги по содержимому материала.\n");
        prompt.append("Главный источник: текст материала. Заголовок и имя файла используй только как контекст.\n");
        prompt.append("Верни от ")
            .append(tagBudget.min())
            .append(" до ")
            .append(tagBudget.max())
            .append(" коротких тегов на языке документа.\n");
        prompt.append("Теги должны описывать проект, тему, объект, процесс, оборудование, подразделение, регуляторный или договорной предмет.\n");
        prompt.append("Не возвращай общие слова вроде документ, файл, материал, отчёт, pdf.\n");
        prompt.append("Не дублируй ручные теги.\n");
        prompt.append("Формат ответа строго JSON: {\"tags\":[\"...\"]}\n\n");

        appendPromptField(prompt, "Заголовок", request.title());
        appendPromptField(prompt, "Имя файла", request.originalFileName());
        appendPromptField(prompt, "Тип источника", request.sourceType());
        appendPromptField(prompt, "Media type", request.mediaType());
        if (request.manualTags() != null && !request.manualTags().isEmpty()) {
            prompt.append("Ручные теги: ").append(String.join(", ", request.manualTags())).append('\n');
        }
        String hintsSummary = parserHintsSummary(request.parserHints());
        if (StringUtils.hasText(hintsSummary)) {
            prompt.append("Извлеченные metadata hints:\n").append(hintsSummary).append('\n');
        }
        prompt.append("\nТекст материала:\n");
        prompt.append(sampleContent(contentText, autoTagsProperties().getMaxInputChars()));
        return prompt.toString();
    }

    private String parserHintsSummary(MaterialMetadataHints hints) {
        if (hints == null) {
            return "";
        }
        StringBuilder summary = new StringBuilder();
        appendHint(summary, "documentType", hints.documentType());
        appendHint(summary, "documentDate", hints.documentDate());
        appendHint(summary, "documentNumber", hints.documentNumber());
        appendHint(summary, "author", hints.author());
        appendHint(summary, "department", hints.department());
        appendHint(summary, "versionLabel", hints.versionLabel());
        appendHint(summary, "language", hints.language());
        appendHint(summary, "project", hints.project());
        appendHint(summary, "counterparty", hints.counterparty());
        appendHint(summary, "businessStatus", hints.businessStatus());
        appendHint(summary, "periodStart", hints.periodStart());
        appendHint(summary, "periodEnd", hints.periodEnd());
        if (!hints.tags().isEmpty()) {
            summary.append("- tags: ").append(String.join(", ", hints.tags())).append('\n');
        }
        return summary.toString();
    }

    private void appendHint(StringBuilder summary, String label, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String stringValue && !StringUtils.hasText(stringValue)) {
            return;
        }
        summary.append("- ").append(label).append(": ").append(value).append('\n');
    }

    private void appendPromptField(StringBuilder prompt, String label, String value) {
        if (StringUtils.hasText(value)) {
            prompt.append(label).append(": ").append(value.trim()).append('\n');
        }
    }

    private String sampleContent(String contentText, int configuredMaxInputChars) {
        int maxInputChars = Math.max(1_000, configuredMaxInputChars <= 0 ? DEFAULT_MAX_INPUT_CHARS : configuredMaxInputChars);
        if (contentText.length() <= maxInputChars) {
            return contentText;
        }

        int segmentChars = Math.max(250, (maxInputChars - 180) / 3);
        String beginning = contentText.substring(0, Math.min(segmentChars, contentText.length()));
        int middleStart = Math.max(0, (contentText.length() / 2) - (segmentChars / 2));
        int middleEnd = Math.min(contentText.length(), middleStart + segmentChars);
        String middle = contentText.substring(middleStart, middleEnd);
        String ending = contentText.substring(Math.max(0, contentText.length() - segmentChars));

        return """
            [НАЧАЛО МАТЕРИАЛА]
            %s

            [СЕРЕДИНА МАТЕРИАЛА]
            %s

            [КОНЕЦ МАТЕРИАЛА]
            %s
            """.formatted(beginning, middle, ending).trim();
    }

    private TagBudget tagBudgetFor(int contentLength) {
        MaterialProperties.AutoTagsProperties autoTags = autoTagsProperties();
        if (contentLength <= SHORT_TEXT_LIMIT) {
            return sanitizedBudget(3, autoTags.getShortTarget());
        }
        if (contentLength <= MEDIUM_TEXT_LIMIT) {
            return sanitizedBudget(6, autoTags.getMediumTarget());
        }
        return sanitizedBudget(10, autoTags.getLargeTarget());
    }

    private TagBudget sanitizedBudget(int preferredMin, int configuredMax) {
        int max = Math.min(InputLimits.TAGS_MAX, Math.max(1, configuredMax));
        int min = Math.min(max, Math.max(1, preferredMin));
        return new TagBudget(min, max);
    }

    private List<String> parseTags(String rawAnswer) {
        if (!StringUtils.hasText(rawAnswer)) {
            return List.of();
        }

        String rawJson = extractJsonObject(rawAnswer);
        if (!StringUtils.hasText(rawJson)) {
            return List.of();
        }

        try {
            JsonNode root = JSON_MAPPER.readTree(rawJson);
            JsonNode tagsNode = root == null ? null : root.get("tags");
            if (tagsNode == null || !tagsNode.isArray()) {
                return List.of();
            }
            List<String> tags = new ArrayList<>();
            for (JsonNode tagNode : tagsNode) {
                if (tagNode != null && tagNode.isValueNode()) {
                    tags.add(tagNode.asText());
                }
            }
            return tags;
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private String extractJsonObject(String rawAnswer) {
        int firstBrace = rawAnswer.indexOf('{');
        int lastBrace = rawAnswer.lastIndexOf('}');
        if (firstBrace < 0 || lastBrace <= firstBrace) {
            return null;
        }
        return rawAnswer.substring(firstBrace, lastBrace + 1);
    }

    private List<String> normalizeTags(List<String> rawTags, List<String> manualTags, int maxTags) {
        if (rawTags == null || rawTags.isEmpty()) {
            return List.of();
        }

        Set<String> manualKeys = new LinkedHashSet<>();
        if (manualTags != null) {
            for (String manualTag : manualTags) {
                String normalizedManual = normalizeTag(manualTag);
                if (normalizedManual != null) {
                    manualKeys.add(dedupeKey(normalizedManual));
                }
            }
        }

        LinkedHashSet<String> normalizedTags = new LinkedHashSet<>();
        int limit = Math.min(InputLimits.TAGS_MAX, Math.max(1, maxTags));
        for (String rawTag : rawTags) {
            String candidate = normalizeTag(rawTag);
            if (candidate == null) {
                continue;
            }
            String key = dedupeKey(candidate);
            if (manualKeys.contains(key) || GENERIC_TAGS.contains(key)) {
                continue;
            }
            normalizedTags.add(candidate);
            if (normalizedTags.size() == limit) {
                break;
            }
        }
        return List.copyOf(normalizedTags);
    }

    private String normalizeTag(String rawTag) {
        if (!StringUtils.hasText(rawTag)) {
            return null;
        }
        String normalized = rawTag.trim()
            .replaceAll("^[\\p{P}\\s]+", "")
            .replaceAll("[\\p{P}\\s]+$", "")
            .replaceAll("\\s+", " ")
            .toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(normalized)
            || normalized.length() < 3
            || normalized.length() > InputLimits.TAG_MAX
            || normalized.chars().allMatch(Character::isDigit)
            || normalized.split("\\s+").length > 4) {
            return null;
        }
        return normalized;
    }

    private String dedupeKey(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private int tokenCount(String contentText) {
        int count = 0;
        for (String token : contentText.split("[^\\p{L}\\p{N}]+")) {
            if (StringUtils.hasText(token)) {
                count++;
            }
        }
        return count;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    public record TaggingRequest(
        String title,
        String sourceType,
        String originalFileName,
        String mediaType,
        String contentText,
        List<String> manualTags,
        MaterialMetadataHints parserHints
    ) {
        public TaggingRequest {
            manualTags = manualTags == null ? List.of() : List.copyOf(manualTags);
            parserHints = parserHints == null ? MaterialMetadataHints.empty() : parserHints;
        }
    }

    private record TagBudget(int min, int max) {
    }
}
