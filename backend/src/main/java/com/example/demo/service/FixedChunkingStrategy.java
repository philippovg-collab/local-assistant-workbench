package com.example.demo.service;

import com.example.demo.service.material.ChunkProfile;
import com.example.demo.service.material.StoredMaterialChunk;
import com.example.demo.service.material.StoredMaterialSegment;

import com.example.demo.config.MaterialProperties;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

public class FixedChunkingStrategy implements ChunkingStrategy {

    @Override
    public ChunkProfile profile() {
        return ChunkProfile.FIXED_V1;
    }

    @Override
    public List<StoredMaterialChunk> buildChunks(
        List<StoredMaterialSegment> segments,
        MaterialProperties properties,
        MaterialContentSupport contentSupport
    ) {
        List<StoredMaterialChunk> chunks = new ArrayList<>();
        int index = 0;

        for (StoredMaterialSegment segment : segments) {
            String text = contentSupport.normalizeStoredContent(segment.text());
            if (!StringUtils.hasText(text)) {
                continue;
            }

            int cursor = 0;
            while (cursor < text.length() && chunks.size() < properties.getMaxChunks()) {
                int end = Math.min(text.length(), cursor + properties.getChunkSize());
                String slice = text.substring(cursor, end).trim();
                if (!slice.isEmpty()) {
                    chunks.add(new StoredMaterialChunk(
                        index++,
                        slice,
                        List.copyOf(contentSupport.tokenize(slice)),
                        segment.page(),
                        contentSupport.normalizeExtractor(segment.extractor()),
                        Boolean.TRUE.equals(segment.ocrUsed())
                    ));
                }

                if (end == text.length()) {
                    break;
                }

                cursor = Math.max(end - properties.getChunkOverlap(), cursor + 1);
            }

            if (chunks.size() >= properties.getMaxChunks()) {
                break;
            }
        }

        return chunks;
    }
}
