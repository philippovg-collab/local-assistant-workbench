package com.example.demo.service;

import com.example.demo.service.material.DocumentBlockConfidence;
import com.example.demo.service.material.DocumentBlockType;
import com.example.demo.service.material.StoredMaterialChunk;
import com.example.demo.service.material.StoredMaterialRecord;

import com.example.demo.model.ChunkScoreBreakdown;
import com.example.demo.model.MaterialMetadataSnapshot;
import com.example.demo.model.RetrievalFilters;
import com.example.demo.model.RetrievalQueryHints;
import com.example.demo.model.SourceTrustLevel;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ChunkReranker {

    private static final Set<String> LOW_SIGNAL_TOKENS = Set.of(
        "the",
        "and",
        "for",
        "with",
        "that",
        "this",
        "как",
        "что",
        "для",
        "или",
        "это",
        "эта",
        "этот",
        "про",
        "по",
        "на"
    );

    private final MaterialContentSupport contentSupport;

    public ChunkReranker(MaterialContentSupport contentSupport) {
        this.contentSupport = contentSupport;
    }

    public List<HybridChunkRanker.RankedChunk> rerank(
        String query,
        RetrievalQueryHints queryHints,
        RetrievalFilters effectiveFilters,
        List<HybridChunkRanker.RankedChunk> candidates,
        Map<String, StoredMaterialRecord> recordsById,
        Map<String, List<StoredMaterialChunk>> chunksByMaterialId,
        int limit
    ) {
        if (candidates == null || candidates.isEmpty() || limit <= 0) {
            return List.of();
        }

        Set<String> significantQueryTokens = significantTokens(query);
        List<RerankedCandidate> rescored = new ArrayList<>();
        for (HybridChunkRanker.RankedChunk candidate : candidates) {
            StoredMaterialRecord record = recordsById.get(candidate.match().materialId());
            StoredMaterialChunk storedChunk = findChunk(chunksByMaterialId.get(candidate.match().materialId()), candidate.match().chunkIndex());
            ScoredCandidate scored = scoreCandidate(candidate, storedChunk, record, query, significantQueryTokens, queryHints, effectiveFilters);
            rescored.add(new RerankedCandidate(
                candidate.withScoreAndBreakdown(scored.breakdown().finalScore(), scored.breakdown()),
                scored.exactIdentifierMatch(),
                scored.rawScore()
            ));
        }

        return rescored.stream()
            .sorted(Comparator
                .comparingInt(RerankedCandidate::rawScore)
                .reversed()
                .thenComparing(RerankedCandidate::exactIdentifierMatch, Comparator.reverseOrder())
                .thenComparingInt(candidate -> candidate.chunk().lexicalRank() == null ? Integer.MAX_VALUE : candidate.chunk().lexicalRank())
                .thenComparingInt(candidate -> candidate.chunk().semanticRank() == null ? Integer.MAX_VALUE : candidate.chunk().semanticRank())
                .thenComparingInt(candidate -> candidate.chunk().match().page() == null ? Integer.MAX_VALUE : candidate.chunk().match().page())
                .thenComparingInt(candidate -> candidate.chunk().match().chunkIndex()))
            .limit(limit)
            .map(RerankedCandidate::chunk)
            .toList();
    }

    private ScoredCandidate scoreCandidate(
        HybridChunkRanker.RankedChunk candidate,
        StoredMaterialChunk storedChunk,
        StoredMaterialRecord record,
        String query,
        Set<String> significantQueryTokens,
        RetrievalQueryHints queryHints,
        RetrievalFilters effectiveFilters
    ) {
        MaterialMetadataSnapshot metadata = record == null ? MaterialMetadataSnapshot.empty() : record.metadata();
        int semanticRankBonus = bonusForSemanticRank(candidate.semanticRank());
        int lexicalRankBonus = bonusForLexicalRank(candidate.lexicalRank());
        IdentifierScore identifierScore = identifierScore(candidate, storedChunk, metadata, queryHints, effectiveFilters);
        int headingBonus = headingBonus(candidate, storedChunk, query, significantQueryTokens);
        int metadataBonus = metadataBonus(metadata, queryHints, effectiveFilters);
        int sourceTrustBoost = sourceTrustBoost(metadata.sourceTrust());
        int appendixPenalty = appendixPenaltyOf(candidate.match().chunkType());
        int boilerplatePenalty = isBoilerplate(candidate, storedChunk) ? -10 : 0;
        int lowConfidencePenalty = lowConfidencePenalty(storedChunk == null ? null : storedChunk.parserConfidence());

        int rawScore =
            candidate.score()
                + semanticRankBonus
                + lexicalRankBonus
                + identifierScore.bonus()
                + headingBonus
                + metadataBonus
                + sourceTrustBoost
                + appendixPenalty
                + boilerplatePenalty
                + lowConfidencePenalty;
        int finalScore = clampScore(rawScore);

        return new ScoredCandidate(
            identifierScore.exactMatch(),
            rawScore,
            new ChunkScoreBreakdown(
                candidate.score(),
                semanticRankBonus,
                lexicalRankBonus,
                identifierScore.bonus(),
                headingBonus,
                metadataBonus,
                sourceTrustBoost,
                appendixPenalty,
                boilerplatePenalty,
                lowConfidencePenalty,
                finalScore
            )
        );
    }

    private IdentifierScore identifierScore(
        HybridChunkRanker.RankedChunk candidate,
        StoredMaterialChunk storedChunk,
        MaterialMetadataSnapshot metadata,
        RetrievalQueryHints queryHints,
        RetrievalFilters effectiveFilters
    ) {
        String identifier = firstNonBlank(
            effectiveFilters == null ? null : effectiveFilters.documentNumber(),
            queryHints == null ? null : queryHints.documentNumber()
        );
        if (!StringUtils.hasText(identifier)) {
            return new IdentifierScore(0, false);
        }

        if (equalsIgnoreCase(identifier, metadata.documentNumber())) {
            return new IdentifierScore(20, true);
        }

        String titleAndText = String.join(
            "\n",
            safe(candidate.match().title()),
            safe(candidate.match().chunkText()),
            storedChunk == null ? "" : String.join("\n", storedChunk.headingTrail())
        );
        if (containsIgnoreCase(titleAndText, identifier)) {
            return new IdentifierScore(12, true);
        }

        String normalizedIdentifier = normalizeIdentifier(identifier);
        if (!normalizedIdentifier.isEmpty() && normalizeIdentifier(titleAndText).contains(normalizedIdentifier)) {
            return new IdentifierScore(6, false);
        }
        return new IdentifierScore(0, false);
    }

    private int headingBonus(
        HybridChunkRanker.RankedChunk candidate,
        StoredMaterialChunk storedChunk,
        String query,
        Set<String> significantQueryTokens
    ) {
        int bonus = 0;
        String title = safe(candidate.match().title());
        String normalizedQuery = normalizeText(query);
        String normalizedTitle = normalizeText(title);
        if (StringUtils.hasText(normalizedQuery) && StringUtils.hasText(normalizedTitle)) {
            if (normalizedTitle.contains(normalizedQuery) || normalizedQuery.contains(normalizedTitle)) {
                bonus += 8;
            }
        }

        List<String> headings = storedChunk == null ? List.of() : storedChunk.headingTrail();
        boolean headingMatched = headings.stream().anyMatch(heading -> {
            String normalizedHeading = normalizeText(heading);
            return StringUtils.hasText(normalizedQuery)
                && StringUtils.hasText(normalizedHeading)
                && (normalizedHeading.contains(normalizedQuery) || normalizedQuery.contains(normalizedHeading));
        });
        if (headingMatched) {
            bonus += 5;
        }

        Set<String> headingTokens = new LinkedHashSet<>(contentSupport.tokenize(title));
        headings.forEach(heading -> headingTokens.addAll(contentSupport.tokenize(heading)));
        long overlap = significantQueryTokens.stream().filter(headingTokens::contains).count();
        if (overlap >= 2) {
            bonus += 3;
        }
        return bonus;
    }

    private int metadataBonus(
        MaterialMetadataSnapshot metadata,
        RetrievalQueryHints queryHints,
        RetrievalFilters effectiveFilters
    ) {
        int bonus = 0;
        RetrievalFilters filters = effectiveFilters == null ? RetrievalFilters.empty() : effectiveFilters;
        if (!filters.documentTypes().isEmpty() && filters.documentTypes().contains(metadata.documentType())) {
            bonus += 4;
        }
        if (!filters.documentStatuses().isEmpty() && filters.documentStatuses().contains(metadata.documentStatus())) {
            bonus += 4;
        }
        if (!filters.projectKeys().isEmpty() && filters.lowerCaseProjectKeys().contains(safe(metadata.projectKey()).toLowerCase(Locale.ROOT))) {
            bonus += 4;
        }
        if (!filters.languageCodes().isEmpty() && filters.languageCodes().contains(metadata.languageCode())) {
            bonus += 4;
        }
        if (equalsIgnoreCase(queryHints == null ? null : queryHints.versionLabel(), metadata.versionLabel())) {
            bonus += 4;
        }
        if (equalsIgnoreCase(filters.department(), metadata.department())) {
            bonus += 2;
        }
        if (equalsIgnoreCase(filters.project(), metadata.project())) {
            bonus += 2;
        }
        if (equalsIgnoreCase(filters.counterparty(), metadata.counterparty())) {
            bonus += 2;
        }
        if (equalsIgnoreCase(filters.businessStatus(), metadata.businessStatus())) {
            bonus += 2;
        }
        if (equalsIgnoreCase(filters.language(), metadata.language())) {
            bonus += 2;
        }
        LocalDate periodStart = metadata.periodStart();
        if (periodStart != null) {
            LocalDate dateFrom = filters.periodStartFrom();
            LocalDate dateTo = filters.periodStartTo();
            if (dateFrom == null && queryHints != null) {
                dateFrom = queryHints.periodStartFrom();
            }
            if (dateTo == null && queryHints != null) {
                dateTo = queryHints.periodStartTo();
            }
            if ((dateFrom != null || dateTo != null)
                && (dateFrom == null || !periodStart.isBefore(dateFrom))
                && (dateTo == null || !periodStart.isAfter(dateTo))) {
                bonus += 4;
            }
        }
        LocalDate periodEnd = metadata.periodEnd();
        if (periodEnd != null) {
            LocalDate dateFrom = filters.periodEndFrom();
            LocalDate dateTo = filters.periodEndTo();
            if (dateFrom == null && queryHints != null) {
                dateFrom = queryHints.periodEndFrom();
            }
            if (dateTo == null && queryHints != null) {
                dateTo = queryHints.periodEndTo();
            }
            if ((dateFrom != null || dateTo != null)
                && (dateFrom == null || !periodEnd.isBefore(dateFrom))
                && (dateTo == null || !periodEnd.isAfter(dateTo))) {
                bonus += 4;
            }
        }
        LocalDate documentDate = metadata.documentDate();
        if (documentDate != null) {
            LocalDate dateFrom = filters.documentDateFrom();
            LocalDate dateTo = filters.documentDateTo();
            if ((dateFrom != null || dateTo != null)
                && (dateFrom == null || !documentDate.isBefore(dateFrom))
                && (dateTo == null || !documentDate.isAfter(dateTo))) {
                bonus += 4;
            }
        }
        return Math.min(bonus, 16);
    }

    private int sourceTrustBoost(SourceTrustLevel sourceTrustLevel) {
        SourceTrustLevel safeLevel = sourceTrustLevel == null ? SourceTrustLevel.UNKNOWN : sourceTrustLevel;
        return switch (safeLevel) {
            case HIGH -> 6;
            case MEDIUM -> 3;
            case LOW -> 0;
            case UNKNOWN -> -2;
        };
    }

    private int appendixPenaltyOf(DocumentBlockType chunkType) {
        DocumentBlockType safeType = chunkType == null ? DocumentBlockType.NARRATIVE : chunkType;
        return switch (safeType) {
            case APPENDIX -> -15;
            case CAPTION -> -6;
            default -> 0;
        };
    }

    private boolean isBoilerplate(HybridChunkRanker.RankedChunk candidate, StoredMaterialChunk storedChunk) {
        String combined = String.join(
            "\n",
            safe(candidate.match().title()),
            safe(candidate.match().chunkText()),
            storedChunk == null ? "" : String.join("\n", storedChunk.headingTrail())
        ).toLowerCase(Locale.ROOT);
        if (combined.contains("appendix")
            || combined.contains("приложение")
            || combined.contains("disclaimer")
            || combined.contains("copyright")
            || combined.contains("all rights reserved")
            || combined.contains("настоящий документ")) {
            return true;
        }
        String normalizedText = safe(candidate.match().chunkText()).replaceAll("\\s+", " ").trim();
        return normalizedText.length() <= 60
            && (normalizedText.toLowerCase(Locale.ROOT).contains("copyright")
                || normalizedText.toLowerCase(Locale.ROOT).contains("настоящий документ"));
    }

    private int lowConfidencePenalty(DocumentBlockConfidence parserConfidence) {
        if (parserConfidence == null) {
            return 0;
        }
        return switch (parserConfidence) {
            case LOW -> -8;
            case MEDIUM -> -3;
            case HIGH -> 0;
        };
    }

    private int bonusForSemanticRank(Integer semanticRank) {
        if (semanticRank == null) {
            return 0;
        }
        if (semanticRank == 1) {
            return 6;
        }
        if (semanticRank <= 3) {
            return 4;
        }
        if (semanticRank <= 6) {
            return 2;
        }
        return 0;
    }

    private int bonusForLexicalRank(Integer lexicalRank) {
        if (lexicalRank == null) {
            return 0;
        }
        if (lexicalRank == 1) {
            return 8;
        }
        if (lexicalRank <= 3) {
            return 5;
        }
        if (lexicalRank <= 6) {
            return 2;
        }
        return 0;
    }

    private Set<String> significantTokens(String value) {
        return contentSupport.tokenize(value).stream()
            .filter(token -> token.length() >= 3)
            .filter(token -> !LOW_SIGNAL_TOKENS.contains(token))
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private StoredMaterialChunk findChunk(List<StoredMaterialChunk> chunks, int chunkIndex) {
        if (chunks == null || chunks.isEmpty()) {
            return null;
        }
        return chunks.stream()
            .filter(chunk -> chunk.index() == chunkIndex)
            .findFirst()
            .orElse(null);
    }

    private String firstNonBlank(String primary, String fallback) {
        if (StringUtils.hasText(primary)) {
            return primary.trim();
        }
        return StringUtils.hasText(fallback) ? fallback.trim() : null;
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return StringUtils.hasText(left) && StringUtils.hasText(right) && left.equalsIgnoreCase(right);
    }

    private boolean containsIgnoreCase(String haystack, String needle) {
        return StringUtils.hasText(haystack)
            && StringUtils.hasText(needle)
            && haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
            .replaceAll("[^\\p{L}\\p{N}]+", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private String normalizeIdentifier(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
            .replaceAll("[^\\p{L}\\p{N}]+", "");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private int clampScore(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private record IdentifierScore(int bonus, boolean exactMatch) {
    }

    private record ScoredCandidate(boolean exactIdentifierMatch, int rawScore, ChunkScoreBreakdown breakdown) {
    }

    private record RerankedCandidate(HybridChunkRanker.RankedChunk chunk, boolean exactIdentifierMatch, int rawScore) {
    }
}
