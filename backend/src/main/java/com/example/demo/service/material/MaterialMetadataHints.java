package com.example.demo.service.material;

import com.example.demo.model.DocumentType;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record MaterialMetadataHints(
    DocumentType documentType,
    LocalDate documentDate,
    String documentNumber,
    String author,
    String department,
    String versionLabel,
    String language,
    List<String> tags,
    String project,
    String counterparty,
    String businessStatus,
    LocalDate periodStart,
    LocalDate periodEnd,
    Map<String, Double> fieldConfidence
) {

    public MaterialMetadataHints {
        tags = tags == null ? List.of() : List.copyOf(tags);
        fieldConfidence = fieldConfidence == null
            ? Map.of()
            : Map.copyOf(new LinkedHashMap<>(fieldConfidence));
    }

    public MaterialMetadataHints withTags(List<String> replacementTags, double confidence) {
        List<String> resolvedTags = replacementTags == null ? List.of() : List.copyOf(replacementTags);
        Map<String, Double> resolvedConfidence = new LinkedHashMap<>(fieldConfidence);
        if (!resolvedTags.isEmpty()) {
            resolvedConfidence.put("tags", confidence);
        }
        return new MaterialMetadataHints(
            documentType,
            documentDate,
            documentNumber,
            author,
            department,
            versionLabel,
            language,
            resolvedTags,
            project,
            counterparty,
            businessStatus,
            periodStart,
            periodEnd,
            resolvedConfidence
        );
    }

    public static MaterialMetadataHints empty() {
        return new MaterialMetadataHints(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            List.of(),
            null,
            null,
            null,
            null,
            null,
            Map.of()
        );
    }
}
