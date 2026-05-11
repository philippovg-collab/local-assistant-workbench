package com.example.demo.service;

import com.example.demo.model.ChatSource;
import com.example.demo.model.EvidenceLocator;
import com.example.demo.model.MaterialMetadataSnapshot;
import com.example.demo.model.MaterialSearchHit;
import com.example.demo.model.MaterialSearchHitNeighbor;
import com.example.demo.service.material.MaterialChunkSearchMatch;
import com.example.demo.service.material.StoredMaterialChunk;
import com.example.demo.service.material.StoredMaterialRecord;
import com.example.demo.service.material.port.MaterialChunkingRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class RetrievalResultMapper {

    private final MaterialChunkingRepository chunkingRepository;
    private final MaterialContentSupport contentSupport;
    private final AnswerModePostProcessor answerModePostProcessor;

    RetrievalResultMapper(
        MaterialChunkingRepository chunkingRepository,
        MaterialContentSupport contentSupport,
        AnswerModePostProcessor answerModePostProcessor
    ) {
        this.chunkingRepository = chunkingRepository;
        this.contentSupport = contentSupport;
        this.answerModePostProcessor = answerModePostProcessor;
    }

    List<MaterialSearchHit> buildSearchHits(RetrievalSearchExecution execution, boolean includeNeighbors) {
        Map<String, List<StoredMaterialChunk>> chunksByMaterialId = includeNeighbors
            ? loadChunksForNeighbors(execution.rankedMatches(), execution.chunksByMaterialId())
            : execution.chunksByMaterialId();
        List<MaterialSearchHit> hits = new ArrayList<>();
        for (HybridChunkRanker.RankedChunk rankedChunk : execution.rankedMatches()) {
            MaterialChunkSearchMatch match = rankedChunk.match();
            StoredMaterialRecord record = execution.recordsById().get(match.materialId());
            MaterialMetadataSnapshot metadata = record == null ? MaterialMetadataSnapshot.empty() : record.metadata();
            StoredMaterialChunk storedChunk = chunkFor(match.materialId(), match.chunkIndex(), chunksByMaterialId);
            List<MaterialSearchHitNeighbor> neighbors = includeNeighbors
                ? neighborsFor(match.materialId(), match.chunkIndex(), chunksByMaterialId)
                : null;
            hits.add(new MaterialSearchHit(
                match.materialId(),
                match.materialId() + ":" + match.chunkIndex(),
                match.title(),
                match.chunkText(),
                match.chunkIndex(),
                match.page(),
                match.chunkType(),
                rankedChunk.score(),
                match.semanticDistance(),
                match.lexicalScore(),
                matchedTerms(execution.queryTokens(), match),
                answerModePostProcessor.buildOpenSourceUrl(
                    match.materialId(),
                    match.materialId() + ":" + match.chunkIndex(),
                    match.chunkIndex(),
                    match.page()
                ),
                metadata,
                neighbors,
                execution.relevanceProfile() == RelevanceProfile.HYBRID_RERANK_V1 ? rankedChunk.scoreBreakdown() : null,
                evidenceLocator(record, match, storedChunk)
            ));
        }
        return List.copyOf(hits);
    }

    private Map<String, List<StoredMaterialChunk>> loadChunksForNeighbors(
        List<HybridChunkRanker.RankedChunk> rankedMatches,
        Map<String, List<StoredMaterialChunk>> preloadedChunksByMaterialId
    ) {
        if (rankedMatches == null || rankedMatches.isEmpty()) {
            return Map.of();
        }
        List<String> materialIds = rankedMatches.stream()
            .map(rankedChunk -> rankedChunk.match().materialId())
            .distinct()
            .toList();
        Map<String, List<StoredMaterialChunk>> chunksByMaterialId = new LinkedHashMap<>();
        Map<String, List<StoredMaterialChunk>> safePreloaded = preloadedChunksByMaterialId == null
            ? Map.of()
            : preloadedChunksByMaterialId;
        safePreloaded.forEach((materialId, chunks) -> chunksByMaterialId.put(
            materialId,
            chunks == null ? List.of() : chunks
        ));

        List<String> missingMaterialIds = materialIds.stream()
            .filter(materialId -> !chunksByMaterialId.containsKey(materialId))
            .toList();
        if (!missingMaterialIds.isEmpty()) {
            Map<String, List<StoredMaterialChunk>> loadedChunks = chunkingRepository.findChunksByMaterialIds(missingMaterialIds);
            Map<String, List<StoredMaterialChunk>> safeLoadedChunks = loadedChunks == null ? Map.of() : loadedChunks;
            missingMaterialIds.forEach(materialId -> chunksByMaterialId.put(
                materialId,
                safeLoadedChunks.getOrDefault(materialId, List.of())
            ));
        }
        return chunksByMaterialId;
    }

    ChatSource buildChatSource(
        HybridChunkRanker.RankedChunk rankedChunk,
        Set<String> queryTokens,
        StoredMaterialRecord record
    ) {
        MaterialChunkSearchMatch match = rankedChunk.match();
        MaterialMetadataSnapshot metadata = record == null ? MaterialMetadataSnapshot.empty() : record.metadata();
        return new ChatSource(
            match.materialId(),
            match.materialId() + ":" + match.chunkIndex(),
            match.title(),
            contentSupport.clip(match.chunkText(), 280),
            rankedChunk.score(),
            confidenceOf(match, rankedChunk.score()),
            matchedTerms(queryTokens, match),
            answerModePostProcessor.buildOpenSourceUrl(
                match.materialId(),
                match.materialId() + ":" + match.chunkIndex(),
                match.chunkIndex(),
                match.page()
            ),
            match.chunkIndex(),
            match.page(),
            match.extractor(),
            match.ocrUsed(),
            match.chunkType(),
            metadata,
            match.semanticDistance(),
            match.lexicalScore(),
            rankedChunk.scoreBreakdown(),
            evidenceLocator(record, match, chunkFor(match.materialId(), match.chunkIndex(), Map.of()))
        );
    }

    ChatSource buildChatSource(
        HybridChunkRanker.RankedChunk rankedChunk,
        Set<String> queryTokens,
        StoredMaterialRecord record,
        StoredMaterialChunk storedChunk
    ) {
        MaterialChunkSearchMatch match = rankedChunk.match();
        MaterialMetadataSnapshot metadata = record == null ? MaterialMetadataSnapshot.empty() : record.metadata();
        return new ChatSource(
            match.materialId(),
            match.materialId() + ":" + match.chunkIndex(),
            match.title(),
            contentSupport.clip(match.chunkText(), 280),
            rankedChunk.score(),
            confidenceOf(match, rankedChunk.score()),
            matchedTerms(queryTokens, match),
            answerModePostProcessor.buildOpenSourceUrl(
                match.materialId(),
                match.materialId() + ":" + match.chunkIndex(),
                match.chunkIndex(),
                match.page()
            ),
            match.chunkIndex(),
            match.page(),
            match.extractor(),
            match.ocrUsed(),
            match.chunkType(),
            metadata,
            match.semanticDistance(),
            match.lexicalScore(),
            rankedChunk.scoreBreakdown(),
            evidenceLocator(record, match, storedChunk)
        );
    }

    private List<MaterialSearchHitNeighbor> neighborsFor(
        String materialId,
        int chunkIndex,
        Map<String, List<StoredMaterialChunk>> chunksByMaterialId
    ) {
        List<StoredMaterialChunk> chunks = chunksByMaterialId.getOrDefault(materialId, List.of());
        if (chunks.isEmpty()) {
            return List.of();
        }
        List<MaterialSearchHitNeighbor> neighbors = new ArrayList<>();
        chunks.stream()
            .filter(chunk -> chunk.index() == chunkIndex - 1)
            .findFirst()
            .ifPresent(chunk -> neighbors.add(toNeighbor(materialId, chunk)));
        chunks.stream()
            .filter(chunk -> chunk.index() == chunkIndex + 1)
            .findFirst()
            .ifPresent(chunk -> neighbors.add(toNeighbor(materialId, chunk)));
        return neighbors.isEmpty() ? List.of() : List.copyOf(neighbors);
    }

    private MaterialSearchHitNeighbor toNeighbor(String materialId, StoredMaterialChunk chunk) {
        return new MaterialSearchHitNeighbor(
            materialId + ":" + chunk.index(),
            chunk.index(),
            chunk.text(),
            chunk.page(),
            chunk.chunkType()
        );
    }

    private StoredMaterialChunk chunkFor(
        String materialId,
        int chunkIndex,
        Map<String, List<StoredMaterialChunk>> chunksByMaterialId
    ) {
        if (chunksByMaterialId == null || chunksByMaterialId.isEmpty()) {
            return null;
        }
        return chunksByMaterialId.getOrDefault(materialId, List.of()).stream()
            .filter(chunk -> chunk.index() == chunkIndex)
            .findFirst()
            .orElse(null);
    }

    private EvidenceLocator evidenceLocator(
        StoredMaterialRecord record,
        MaterialChunkSearchMatch match,
        StoredMaterialChunk chunk
    ) {
        if (match == null) {
            return null;
        }
        MaterialMetadataSnapshot metadata = record == null ? MaterialMetadataSnapshot.empty() : record.metadata();
        return new EvidenceLocator(
            record == null ? null : record.sourceKey(),
            match.materialId(),
            metadata.documentNumber(),
            metadata.versionLabel(),
            record == null ? null : record.versionState(),
            record == null ? null : record.lineageVersion(),
            match.chunkIndex(),
            chunk == null ? match.page() : chunk.page(),
            chunk == null ? List.of() : chunk.sectionPath(),
            chunk == null ? List.of() : chunk.headingTrail(),
            chunk == null ? null : chunk.tableId(),
            chunk == null ? null : chunk.slideId(),
            null,
            null,
            null,
            null
        );
    }

    private double confidenceOf(MaterialChunkSearchMatch match, int score) {
        double scoreConfidence = Math.max(0.0d, Math.min(1.0d, score / 100.0d));
        if (match.semanticDistance() == null) {
            return scoreConfidence;
        }
        double semanticConfidence = Math.max(0.0d, Math.min(1.0d, 1.0d - match.semanticDistance()));
        return Math.max(scoreConfidence, semanticConfidence);
    }

    private List<String> matchedTerms(Set<String> queryTokens, MaterialChunkSearchMatch match) {
        if (queryTokens == null || queryTokens.isEmpty()) {
            return List.of();
        }
        Set<String> contextTokens = contentSupport.tokenize(match.title() + "\n" + match.chunkText());
        return queryTokens.stream()
            .filter(contextTokens::contains)
            .limit(8)
            .toList();
    }
}
