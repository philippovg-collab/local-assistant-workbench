package com.example.demo.model.eval;

import java.util.LinkedHashMap;
import java.util.Map;

public record EvalDatasetVersionCaseRef(
    String caseId,
    String caseKey,
    int revision,
    EvalReviewStatus reviewStatus,
    String contentHash
) {

    public EvalDatasetVersionCaseRef {
        if (isBlank(caseId)) {
            throw new IllegalArgumentException("caseId is required");
        }
        if (isBlank(caseKey)) {
            throw new IllegalArgumentException("caseKey is required");
        }
        if (revision < 1) {
            throw new IllegalArgumentException("revision must be positive");
        }
        reviewStatus = reviewStatus == null ? EvalReviewStatus.DRAFT : reviewStatus;
        contentHash = isBlank(contentHash) ? null : contentHash.trim();
    }

    public static EvalDatasetVersionCaseRef fromMap(Map<String, Object> value) {
        if (value == null) {
            throw new IllegalArgumentException("case revision ref is required");
        }
        return new EvalDatasetVersionCaseRef(
            text(value.get("caseId")),
            text(value.get("caseKey")),
            integer(value.get("revision")),
            reviewStatus(value.get("reviewStatus")),
            text(value.get("contentHash"))
        );
    }

    public EvalDatasetVersionCaseRef withContentHash(String resolvedContentHash) {
        return new EvalDatasetVersionCaseRef(caseId, caseKey, revision, reviewStatus, resolvedContentHash);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("caseId", caseId);
        value.put("caseKey", caseKey);
        value.put("revision", revision);
        value.put("reviewStatus", reviewStatus.name());
        if (!isBlank(contentHash)) {
            value.put("contentHash", contentHash);
        }
        return value;
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static int integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = text(value);
        if (text == null) {
            throw new IllegalArgumentException("revision is required");
        }
        return Integer.parseInt(text);
    }

    private static EvalReviewStatus reviewStatus(Object value) {
        String text = text(value);
        return text == null ? EvalReviewStatus.DRAFT : EvalReviewStatus.valueOf(text);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
