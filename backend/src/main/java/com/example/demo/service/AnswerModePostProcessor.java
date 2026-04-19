package com.example.demo.service;

import com.example.demo.model.AnswerMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AnswerModePostProcessor {

    private static final Pattern QUOTE_PATTERN = Pattern.compile("[\"“«][^\"”»\\n]{3,220}[\"”»]");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\b\\d+[\\d.,]*\\b");
    private static final String STRICT_NOT_FOUND = "Не найдено в источниках.";
    private static final String DOCUMENTS_ONLY_UNSUPPORTED = "В документах нет достаточного подтверждения для такого вывода.";
    private static final Set<String> LOW_SIGNAL_TOKENS = Collections.unmodifiableSet(new LinkedHashSet<>(List.of(
        "это", "этот", "эта", "и", "или", "что", "если", "также", "когда", "который", "которая",
        "для", "при", "как", "there", "with", "from", "that", "this", "нет", "да"
    )));

    private final MaterialContentSupport contentSupport;

    public AnswerModePostProcessor(MaterialContentSupport contentSupport) {
        this.contentSupport = contentSupport;
    }

    public String apply(AnswerMode answerMode, String answer, MaterialRetrievalResult retrievalResult) {
        if (answerMode == null || !StringUtils.hasText(answer) || retrievalResult == null) {
            return answer;
        }

        boolean supported = isSupportedByRetrievedContext(answer, retrievalResult.matches());

        return switch (answerMode) {
            case STRICT_SOURCES_ONLY -> supported ? answer : STRICT_NOT_FOUND;
            case DOCUMENTS_ONLY -> supported ? answer : DOCUMENTS_ONLY_UNSUPPORTED;
            case WITH_QUOTES -> ensureQuotes(answer, retrievalResult.matches());
            case BROADER_REASONING -> supported ? answer : ensureBroaderReasoningLabel(answer);
            case BRIEF -> answer;
        };
    }

    private boolean isSupportedByRetrievedContext(String answer, List<RetrievedMaterialChunk> matches) {
        if (!StringUtils.hasText(answer) || matches == null || matches.isEmpty()) {
            return false;
        }

        String normalizedAnswer = answer.trim();
        if (STRICT_NOT_FOUND.equalsIgnoreCase(normalizedAnswer)) {
            return false;
        }

        String combinedContext = matches.stream()
            .limit(4)
            .map(RetrievedMaterialChunk::contextText)
            .filter(StringUtils::hasText)
            .reduce((left, right) -> left + "\n" + right)
            .orElse("");
        if (!StringUtils.hasText(combinedContext)) {
            return false;
        }

        Set<String> contextTokens = contentSupport.tokenize(combinedContext);
        Set<String> answerTokens = new LinkedHashSet<>(contentSupport.tokenize(normalizedAnswer));
        answerTokens.removeIf(token -> token.length() < 3 || LOW_SIGNAL_TOKENS.contains(token));

        if (containsUnsupportedNumber(normalizedAnswer, combinedContext)) {
            return false;
        }

        if (answerTokens.isEmpty()) {
            return true;
        }

        long overlap = answerTokens.stream().filter(contextTokens::contains).count();
        int requiredOverlap = normalizedAnswer.length() <= 48 ? 1 : Math.min(3, Math.max(1, answerTokens.size() / 4));
        return overlap >= requiredOverlap;
    }

    private boolean containsUnsupportedNumber(String answer, String combinedContext) {
        Matcher matcher = NUMBER_PATTERN.matcher(answer);
        while (matcher.find()) {
            String value = matcher.group();
            if (!combinedContext.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private String ensureQuotes(String answer, List<RetrievedMaterialChunk> matches) {
        if (!StringUtils.hasText(answer) || matches == null || matches.isEmpty()) {
            return answer;
        }
        if (QUOTE_PATTERN.matcher(answer).find()) {
            return answer;
        }

        List<String> quotes = matches.stream()
            .limit(2)
            .map(this::quoteSnippet)
            .filter(StringUtils::hasText)
            .distinct()
            .toList();
        if (quotes.isEmpty()) {
            return answer;
        }

        StringBuilder builder = new StringBuilder(answer.trim());
        builder.append("\n\nЦитаты из источников:");
        for (String quote : quotes) {
            builder.append("\n- \"").append(quote).append('"');
        }
        return builder.toString();
    }

    private String quoteSnippet(RetrievedMaterialChunk chunk) {
        if (chunk == null || !StringUtils.hasText(chunk.contextText())) {
            return null;
        }

        String text = chunk.contextText().replaceAll("\\s+", " ").trim();
        List<String> matchedTerms = chunk.source() == null ? List.of() : chunk.source().matchedTerms();
        String lowerText = text.toLowerCase(Locale.ROOT);

        if (matchedTerms != null) {
            for (String matchedTerm : matchedTerms) {
                if (!StringUtils.hasText(matchedTerm)) {
                    continue;
                }
                int index = lowerText.indexOf(matchedTerm.toLowerCase(Locale.ROOT));
                if (index >= 0) {
                    int start = Math.max(0, index - 50);
                    int end = Math.min(text.length(), index + matchedTerm.length() + 70);
                    return clipQuote(text.substring(start, end), start > 0, end < text.length());
                }
            }
        }

        return clipQuote(text, false, text.length() > 140);
    }

    private String clipQuote(String text, boolean clippedLeft, boolean clippedRight) {
        if (!StringUtils.hasText(text)) {
            return null;
        }

        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() > 140) {
            normalized = normalized.substring(0, 140).trim();
            clippedRight = true;
        }
        if (clippedLeft) {
            normalized = "…" + normalized;
        }
        if (clippedRight) {
            normalized = normalized + "…";
        }
        return normalized;
    }

    private String ensureBroaderReasoningLabel(String answer) {
        String normalized = answer == null ? "" : answer.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.contains("более широкое рассуждение:") || lower.contains("broader reasoning:")) {
            return normalized;
        }

        return normalized + "\n\nБолее широкое рассуждение: часть вывода выходит за пределы прямых фактов из документов.";
    }

    public String buildOpenSourceUrl(String materialId, String chunkId, Integer chunkIndex, Integer page) {
        StringBuilder url = new StringBuilder("/api/materials/")
            .append(URLEncoder.encode(materialId, StandardCharsets.UTF_8));
        boolean firstParameter = true;
        firstParameter = appendParameter(url, firstParameter, "chunkId", chunkId);
        firstParameter = appendParameter(url, firstParameter, "chunkIndex", chunkIndex == null ? null : String.valueOf(chunkIndex));
        appendParameter(url, firstParameter, "page", page == null ? null : String.valueOf(page));
        return url.toString();
    }

    private boolean appendParameter(StringBuilder url, boolean firstParameter, String name, String value) {
        if (!StringUtils.hasText(value)) {
            return firstParameter;
        }
        url.append(firstParameter ? '?' : '&')
            .append(URLEncoder.encode(name, StandardCharsets.UTF_8))
            .append('=')
            .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
        return false;
    }
}
