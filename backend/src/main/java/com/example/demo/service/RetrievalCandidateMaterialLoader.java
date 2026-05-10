package com.example.demo.service;

import com.example.demo.service.material.StoredMaterialChunk;
import com.example.demo.service.material.StoredMaterialRecord;
import com.example.demo.service.material.port.MaterialCatalogRepository;
import com.example.demo.service.material.port.MaterialChunkingRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class RetrievalCandidateMaterialLoader {

    private final MaterialCatalogRepository catalogRepository;
    private final MaterialChunkingRepository chunkingRepository;

    RetrievalCandidateMaterialLoader(
        MaterialCatalogRepository catalogRepository,
        MaterialChunkingRepository chunkingRepository
    ) {
        this.catalogRepository = catalogRepository;
        this.chunkingRepository = chunkingRepository;
    }

    Map<String, List<StoredMaterialChunk>> loadChunksByMaterialId(
        List<HybridChunkRanker.RankedChunk> rankedMatches
    ) {
        if (rankedMatches == null || rankedMatches.isEmpty()) {
            return Map.of();
        }
        List<String> materialIds = materialIdsFrom(rankedMatches);
        Map<String, List<StoredMaterialChunk>> chunksByMaterialId = chunkingRepository.findChunksByMaterialIds(materialIds);
        Map<String, List<StoredMaterialChunk>> orderedChunksByMaterialId = new LinkedHashMap<>();
        Map<String, List<StoredMaterialChunk>> safeChunksByMaterialId = chunksByMaterialId == null
            ? Map.of()
            : chunksByMaterialId;
        materialIds.forEach(materialId -> orderedChunksByMaterialId.put(
            materialId,
            safeChunksByMaterialId.getOrDefault(materialId, List.of())
        ));
        return orderedChunksByMaterialId;
    }

    Map<String, StoredMaterialRecord> loadRecordsByMaterialId(List<HybridChunkRanker.RankedChunk> rankedMatches) {
        if (rankedMatches == null || rankedMatches.isEmpty()) {
            return Map.of();
        }
        List<String> materialIds = materialIdsFrom(rankedMatches);
        List<StoredMaterialRecord> records = catalogRepository.findByIds(materialIds);
        records = records == null ? List.of() : records;
        return records.stream()
            .collect(Collectors.toMap(
                StoredMaterialRecord::id,
                record -> record,
                (left, right) -> left,
                LinkedHashMap::new
            ));
    }

    private List<String> materialIdsFrom(List<HybridChunkRanker.RankedChunk> rankedMatches) {
        return rankedMatches.stream()
            .map(rankedChunk -> rankedChunk.match().materialId())
            .distinct()
            .toList();
    }
}
