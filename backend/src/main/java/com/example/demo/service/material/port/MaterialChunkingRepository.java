package com.example.demo.service.material.port;

import com.example.demo.service.material.StoredMaterialChunk;
import com.example.demo.service.material.StoredMaterialSegment;

import java.time.Instant;
import java.util.List;

public interface MaterialChunkingRepository {

    List<StoredMaterialChunk> findChunks(String materialId);

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
