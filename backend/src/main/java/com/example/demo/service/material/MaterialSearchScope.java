package com.example.demo.service.material;

import com.example.demo.model.KnowledgeScope;
import com.example.demo.model.RetrievalFilters;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

public record MaterialSearchScope(
    Mode mode,
    KnowledgeScope knowledgeScope,
    RetrievalFilters retrievalFilters,
    LocalDate effectiveDate,
    Instant uploadedAfterInclusive,
    Instant uploadedBeforeExclusive,
    Set<String> materialIds
) {

    public MaterialSearchScope {
        mode = mode == null ? Mode.UNSCOPED : mode;
        knowledgeScope = knowledgeScope == null ? KnowledgeScope.empty() : knowledgeScope;
        retrievalFilters = retrievalFilters == null ? RetrievalFilters.empty() : retrievalFilters;
        materialIds = normalizeMaterialIds(materialIds);
        if (mode == Mode.MATERIAL_IDS && materialIds.isEmpty()) {
            mode = Mode.NO_RESULTS;
        }
        if (mode != Mode.MATERIAL_IDS) {
            materialIds = Set.of();
        }
    }

    public static MaterialSearchScope unscoped() {
        return new MaterialSearchScope(
            Mode.UNSCOPED,
            KnowledgeScope.empty(),
            RetrievalFilters.empty(),
            null,
            null,
            null,
            Set.of()
        );
    }

    public static MaterialSearchScope noResults() {
        return new MaterialSearchScope(
            Mode.NO_RESULTS,
            KnowledgeScope.empty(),
            RetrievalFilters.empty(),
            null,
            null,
            null,
            Set.of()
        );
    }

    public static MaterialSearchScope filtered(
        KnowledgeScope knowledgeScope,
        RetrievalFilters retrievalFilters,
        LocalDate effectiveDate,
        Instant uploadedAfterInclusive,
        Instant uploadedBeforeExclusive
    ) {
        return new MaterialSearchScope(
            Mode.FILTERED,
            knowledgeScope,
            retrievalFilters,
            effectiveDate,
            uploadedAfterInclusive,
            uploadedBeforeExclusive,
            Set.of()
        );
    }

    public static MaterialSearchScope filtered(
        KnowledgeScope knowledgeScope,
        RetrievalFilters retrievalFilters,
        Instant uploadedAfterInclusive,
        Instant uploadedBeforeExclusive
    ) {
        return filtered(knowledgeScope, retrievalFilters, null, uploadedAfterInclusive, uploadedBeforeExclusive);
    }

    public static MaterialSearchScope filteredMaterialIds(
        Set<String> materialIds,
        KnowledgeScope knowledgeScope,
        RetrievalFilters retrievalFilters,
        LocalDate effectiveDate,
        Instant uploadedAfterInclusive,
        Instant uploadedBeforeExclusive
    ) {
        Set<String> normalizedIds = normalizeMaterialIds(materialIds);
        if (normalizedIds.isEmpty()) {
            return noResults();
        }
        return new MaterialSearchScope(
            Mode.MATERIAL_IDS,
            knowledgeScope,
            retrievalFilters,
            effectiveDate,
            uploadedAfterInclusive,
            uploadedBeforeExclusive,
            normalizedIds
        );
    }

    public static MaterialSearchScope fromRetrievalCriteria(
        KnowledgeScope knowledgeScope,
        RetrievalFilters retrievalFilters,
        LocalDate effectiveDate,
        Instant uploadedAfterInclusive,
        Instant uploadedBeforeExclusive
    ) {
        KnowledgeScope safeScope = knowledgeScope == null ? KnowledgeScope.empty() : knowledgeScope;
        RetrievalFilters safeFilters = retrievalFilters == null ? RetrievalFilters.empty() : retrievalFilters;
        if (isEmptyScope(safeScope)
            && safeFilters.isEmpty()
            && effectiveDate == null
            && uploadedAfterInclusive == null
            && uploadedBeforeExclusive == null) {
            return unscoped();
        }
        return filtered(safeScope, safeFilters, effectiveDate, uploadedAfterInclusive, uploadedBeforeExclusive);
    }

    public static MaterialSearchScope fromRetrievalCriteria(
        KnowledgeScope knowledgeScope,
        RetrievalFilters retrievalFilters,
        Instant uploadedAfterInclusive,
        Instant uploadedBeforeExclusive
    ) {
        return fromRetrievalCriteria(knowledgeScope, retrievalFilters, null, uploadedAfterInclusive, uploadedBeforeExclusive);
    }

    public static MaterialSearchScope fromLegacyMaterialIds(Set<String> allowedMaterialIds) {
        if (allowedMaterialIds == null) {
            return unscoped();
        }
        Set<String> normalizedIds = normalizeMaterialIds(allowedMaterialIds);
        if (normalizedIds.isEmpty()) {
            return noResults();
        }
        return new MaterialSearchScope(
            Mode.MATERIAL_IDS,
            KnowledgeScope.empty(),
            RetrievalFilters.empty(),
            null,
            null,
            null,
            normalizedIds
        );
    }

    public static MaterialSearchScope fromLegacyMaterialIds(
        Set<String> allowedMaterialIds,
        RetrievalFilters retrievalFilters
    ) {
        RetrievalFilters safeFilters = retrievalFilters == null ? RetrievalFilters.empty() : retrievalFilters;
        if (allowedMaterialIds == null) {
            return safeFilters.isEmpty()
                ? unscoped()
                : filtered(KnowledgeScope.empty(), safeFilters, safeFilters.effectiveDate(), null, null);
        }
        Set<String> normalizedIds = normalizeMaterialIds(allowedMaterialIds);
        if (normalizedIds.isEmpty()) {
            return noResults();
        }
        return new MaterialSearchScope(
            Mode.MATERIAL_IDS,
            KnowledgeScope.empty(),
            safeFilters,
            safeFilters.effectiveDate(),
            null,
            null,
            normalizedIds
        );
    }

    public boolean isNoResults() {
        return mode == Mode.NO_RESULTS;
    }

    public boolean isFiltered() {
        return mode == Mode.FILTERED;
    }

    public boolean isUnscoped() {
        return mode == Mode.UNSCOPED;
    }

    public boolean isMaterialIds() {
        return mode == Mode.MATERIAL_IDS;
    }

    public boolean requiresCriteriaFiltering() {
        return mode == Mode.FILTERED
            || (mode == Mode.MATERIAL_IDS && hasCriteriaFiltering());
    }

    public boolean hasCriteriaFiltering() {
        return !isEmptyScope(knowledgeScope)
            || !retrievalFilters.isEmpty()
            || effectiveDate != null
            || uploadedAfterInclusive != null
            || uploadedBeforeExclusive != null;
    }

    private static boolean isEmptyScope(KnowledgeScope scope) {
        return scope.presetIds().isEmpty()
            && scope.facetIds().isEmpty()
            && scope.documentClasses().isEmpty()
            && scope.documentTypes().isEmpty()
            && scope.documentStatuses().isEmpty()
            && scope.projectKeys().isEmpty()
            && scope.documentNumber() == null
            && scope.languageCodes().isEmpty()
            && scope.tags().isEmpty()
            && scope.workspaceKey() == null
            && scope.periodStartFrom() == null
            && scope.periodStartTo() == null
            && scope.periodEndFrom() == null
            && scope.periodEndTo() == null
            && !scope.uploadedTodayOnly();
    }

    private static Set<String> normalizeMaterialIds(Set<String> rawIds) {
        if (rawIds == null || rawIds.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String rawId : rawIds) {
            if (rawId == null || rawId.isBlank()) {
                continue;
            }
            normalized.add(rawId.trim());
        }
        return normalized.isEmpty() ? Set.of() : Set.copyOf(normalized);
    }

    public enum Mode {
        UNSCOPED,
        FILTERED,
        MATERIAL_IDS,
        NO_RESULTS
    }
}
