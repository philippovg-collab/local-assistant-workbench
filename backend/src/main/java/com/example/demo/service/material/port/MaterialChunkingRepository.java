package com.example.demo.service.material.port;

import com.example.demo.service.material.StoredMaterialChunk;
import com.example.demo.service.material.StoredMaterialSegment;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface MaterialChunkingRepository {

    List<StoredMaterialChunk> findChunks(String materialId);

    Map<String, List<StoredMaterialChunk>> findChunksByMaterialIds(Collection<String> materialIds);

    List<StoredMaterialSegment> findSegments(String materialId);

    String findChunkProfile(String materialId);

    void replaceChunking(
        String materialId,
        String chunkProfile,
        List<StoredMaterialChunk> chunks,
        List<StoredMaterialSegment> segments,
        Instant updatedAt
    );
}
