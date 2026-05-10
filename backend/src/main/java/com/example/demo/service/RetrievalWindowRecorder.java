package com.example.demo.service;

import com.example.demo.model.ChatSource;
import com.example.demo.model.SourceTrustLevel;
import com.example.demo.model.DocumentBlockType;
import com.example.demo.service.material.StoredMaterialRecord;
import java.util.List;
import java.util.Map;

final class RetrievalWindowRecorder {

    private final QualityLayerHealthService qualityLayerHealthService;

    RetrievalWindowRecorder(QualityLayerHealthService qualityLayerHealthService) {
        this.qualityLayerHealthService = qualityLayerHealthService;
    }

    void record(
        List<RetrievedMaterialChunk> matches,
        Map<String, StoredMaterialRecord> scopedReadyRecordsById,
        List<HybridChunkRanker.RankedChunk> preRerankMatches,
        List<HybridChunkRanker.RankedChunk> finalRankedMatches
    ) {
        HybridChunkRanker.RankedChunk preTop = preRerankMatches == null || preRerankMatches.isEmpty() ? null : preRerankMatches.getFirst();
        HybridChunkRanker.RankedChunk finalTop = finalRankedMatches == null || finalRankedMatches.isEmpty() ? null : finalRankedMatches.getFirst();
        boolean top1Changed = preTop != null && finalTop != null
            && !RetrievalTraceBuilder.chunkKeyOf(preTop.match()).equals(RetrievalTraceBuilder.chunkKeyOf(finalTop.match()));
        boolean top1Improved = top1Changed
            && finalTop.scoreBreakdown() != null
            && finalTop.scoreBreakdown().finalScore() > preTop.score();
        boolean appendixDemotion = top1Changed
            && preTop != null
            && (preTop.match().chunkType() == DocumentBlockType.APPENDIX
                || preTop.match().chunkType() == DocumentBlockType.CAPTION);
        boolean highTrustPromotion = top1Changed
            && trustLevelOf(scopedReadyRecordsById.get(finalTop == null ? null : finalTop.match().materialId())) == SourceTrustLevel.HIGH
            && trustLevelOf(scopedReadyRecordsById.get(preTop == null ? null : preTop.match().materialId())) != SourceTrustLevel.HIGH;
        List<DocumentBlockType> finalChunkTypes = matches == null
            ? List.of()
            : matches.stream()
                .map(RetrievedMaterialChunk::source)
                .map(ChatSource::chunkType)
                .toList();
        qualityLayerHealthService.recordRetrieval(
            finalChunkTypes,
            matches == null || matches.isEmpty(),
            top1Changed,
            top1Improved,
            appendixDemotion,
            highTrustPromotion
        );
    }

    private SourceTrustLevel trustLevelOf(StoredMaterialRecord record) {
        if (record == null || record.metadata() == null || record.metadata().sourceTrust() == null) {
            return SourceTrustLevel.UNKNOWN;
        }
        return record.metadata().sourceTrust();
    }
}
