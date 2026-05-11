package com.example.demo.model.eval;

import com.example.demo.model.EvidenceLocator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;

public record EvalCase(
    String id,
    String datasetId,
    String caseKey,
    int revision,
    EvalCaseType caseType,
    EvalExpectedMode expectedMode,
    EvalCaseSeverity severity,
    String question,
    Map<String, Object> knowledgeScope,
    Map<String, Object> retrievalFilters,
    List<String> goldFacts,
    List<String> acceptedAnswers,
    List<EvidenceLocator> goldEvidenceLocators,
    List<List<EvidenceLocator>> requiredDocGroups,
    List<String> forbiddenDocumentRefs,
    List<String> tags,
    EvalCaseOrigin origin,
    EvalReviewStatus reviewStatus,
    boolean active,
    Instant createdAt,
    Instant updatedAt
) {
    public EvalCase {
        knowledgeScope = knowledgeScope == null ? Map.of() : knowledgeScope;
        retrievalFilters = retrievalFilters == null ? Map.of() : retrievalFilters;
        goldFacts = normalizeStrings(goldFacts, false);
        acceptedAnswers = normalizeStrings(acceptedAnswers, true);
        goldEvidenceLocators = goldEvidenceLocators == null ? List.of() : List.copyOf(goldEvidenceLocators);
        requiredDocGroups = copyGroups(requiredDocGroups);
        forbiddenDocumentRefs = normalizeStrings(forbiddenDocumentRefs, false);
        tags = normalizeStrings(tags, false);
        origin = origin == null ? EvalCaseOrigin.empty() : origin;
        reviewStatus = reviewStatus == null ? EvalReviewStatus.DRAFT : reviewStatus;
        severity = severity == null ? EvalCaseSeverity.MEDIUM : severity;
        active = active && reviewStatus != EvalReviewStatus.ARCHIVED;
    }

    public EvalCase(
        String id,
        String datasetId,
        String caseKey,
        int revision,
        EvalCaseType caseType,
        EvalExpectedMode expectedMode,
        EvalSeverity severity,
        String question,
        Map<String, Object> knowledgeScope,
        Map<String, Object> retrievalFilters,
        Map<String, Object> goldFacts,
        Map<String, Object> goldAnswers,
        Map<String, Object> goldEvidence,
        EvalCaseOrigin origin,
        Instant createdAt,
        Instant updatedAt
    ) {
        this(
            id,
            datasetId,
            caseKey,
            revision,
            caseType,
            expectedMode,
            fromLegacySeverity(severity),
            question,
            knowledgeScope,
            retrievalFilters,
            stringsFrom(goldFacts, List.of("facts", "goldFacts")),
            stringsFrom(goldAnswers, List.of("acceptedAnswers", "accepted", "answers")),
            locatorsFrom(goldEvidence, List.of("goldEvidenceLocators", "evidenceLocators", "locators", "evidence")),
            groupsFrom(goldEvidence),
            stringsFrom(goldEvidence, List.of("forbiddenDocumentRefs", "forbiddenDocuments", "forbiddenDocumentIds", "forbiddenDocIds")),
            List.of(),
            origin,
            EvalReviewStatus.DRAFT,
            true,
            createdAt,
            updatedAt
        );
    }

    public Map<String, Object> goldAnswers() {
        return Map.of("acceptedAnswers", acceptedAnswers);
    }

    public Map<String, Object> goldEvidence() {
        return Map.of(
            "goldEvidenceLocators", goldEvidenceLocators,
            "requiredDocGroups", requiredDocGroups,
            "forbiddenDocumentRefs", forbiddenDocumentRefs
        );
    }

    private static List<String> normalizeStrings(List<String> values, boolean lowerCase) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
            .filter(value -> value != null && !value.isBlank())
            .map(value -> value.trim().replaceAll("\\s+", " "))
            .map(value -> lowerCase ? value.toLowerCase(Locale.ROOT) : value)
            .distinct()
            .toList();
    }

    private static EvalCaseSeverity fromLegacySeverity(EvalSeverity severity) {
        if (severity == null) {
            return null;
        }
        return switch (severity) {
            case BLOCKER -> EvalCaseSeverity.BLOCKER;
            case CRITICAL -> EvalCaseSeverity.HIGH;
            case MAJOR -> EvalCaseSeverity.MEDIUM;
            case MINOR -> EvalCaseSeverity.LOW;
        };
    }

    private static List<String> stringsFrom(Map<String, Object> payload, List<String> keys) {
        if (payload == null || payload.isEmpty()) {
            return List.of();
        }
        if (payload.size() == 1 && payload.values().iterator().next() instanceof List<?> onlyList) {
            return stringsFromList(onlyList);
        }
        for (String key : keys) {
            Object value = payload.get(key);
            if (value instanceof List<?> list) {
                return stringsFromList(list);
            }
        }
        return List.of();
    }

    private static List<String> stringsFromList(List<?> values) {
        List<String> normalized = new ArrayList<>();
        for (Object value : values) {
            if (value instanceof String text && !text.isBlank()) {
                normalized.add(text);
            }
        }
        return normalized;
    }

    private static List<EvidenceLocator> locatorsFrom(Map<String, Object> payload, List<String> keys) {
        if (payload == null || payload.isEmpty()) {
            return List.of();
        }
        for (String key : keys) {
            Object value = payload.get(key);
            if (value instanceof List<?> list) {
                return locatorsFromList(list);
            }
        }
        return List.of();
    }

    private static List<List<EvidenceLocator>> groupsFrom(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return List.of();
        }
        Object value = payload.get("requiredDocGroups");
        if (!(value instanceof List<?> rawGroups)) {
            return List.of();
        }
        List<List<EvidenceLocator>> groups = new ArrayList<>();
        for (Object rawGroup : rawGroups) {
            if (rawGroup instanceof List<?> list) {
                List<EvidenceLocator> group = locatorsFromList(list);
                if (!group.isEmpty()) {
                    groups.add(group);
                }
            }
        }
        return List.copyOf(groups);
    }

    private static List<List<EvidenceLocator>> copyGroups(List<List<EvidenceLocator>> groups) {
        if (groups == null) {
            return List.of();
        }
        List<List<EvidenceLocator>> copied = new ArrayList<>();
        for (List<EvidenceLocator> group : groups) {
            if (group != null && !group.isEmpty()) {
                copied.add(List.copyOf(group));
            }
        }
        return List.copyOf(copied);
    }

    @SuppressWarnings("unchecked")
    private static List<EvidenceLocator> locatorsFromList(List<?> values) {
        List<EvidenceLocator> locators = new ArrayList<>();
        for (Object value : values) {
            if (value instanceof EvidenceLocator locator) {
                locators.add(locator);
            } else if (value instanceof Map<?, ?> map) {
                Map<String, Object> payload = (Map<String, Object>) map;
                locators.add(new EvidenceLocator(
                    text(payload.get("sourceKey")),
                    text(payload.get("materialId")),
                    text(payload.get("documentNumber")),
                    text(payload.get("versionLabel")),
                    null,
                    integer(payload.get("lineageVersion")),
                    integer(payload.get("chunkIndex")),
                    integer(payload.get("page")),
                    stringList(payload.get("sectionPath")),
                    stringList(payload.get("headingTrail")),
                    text(payload.get("tableId")),
                    text(payload.get("slideId")),
                    text(payload.get("rowKey")),
                    text(payload.get("columnKey")),
                    integer(payload.get("spanStart")),
                    integer(payload.get("spanEnd"))
                ));
            }
        }
        return List.copyOf(locators);
    }

    private static String text(Object value) {
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    private static Integer integer(Object value) {
        if (value instanceof Integer integer) {
            return integer;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.valueOf(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return stringsFromList(list);
    }
}
