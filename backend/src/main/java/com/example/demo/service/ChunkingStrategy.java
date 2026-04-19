package com.example.demo.service;

import com.example.demo.config.MaterialProperties;
import com.example.demo.infrastructure.material.ChunkProfile;
import com.example.demo.infrastructure.material.DocumentBlock;
import com.example.demo.infrastructure.material.StoredMaterialChunk;
import com.example.demo.infrastructure.material.StoredMaterialSegment;
import java.util.List;

public interface ChunkingStrategy {

    ChunkProfile profile();

    List<StoredMaterialChunk> buildChunks(
        List<StoredMaterialSegment> segments,
        MaterialProperties properties,
        MaterialContentSupport contentSupport
    );

    default List<StoredMaterialChunk> buildChunksFromBlocks(
        List<DocumentBlock> blocks,
        MaterialProperties properties,
        MaterialContentSupport contentSupport
    ) {
        return buildChunks(contentSupport.toStoredSegments(blocks), properties, contentSupport);
    }
}
