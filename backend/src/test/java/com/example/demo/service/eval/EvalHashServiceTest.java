package com.example.demo.service.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EvalHashServiceTest {

    private final EvalHashService hashService = new EvalHashService(JsonMapper.builder().findAndAddModules().build());

    @Test
    void canonicalJsonHashIsStableAcrossMapOrder() {
        String left = hashService.hash(Map.of(
            "b", List.of(Map.of("z", 1, "a", 2)),
            "a", Map.of("d", "x", "c", true)
        ));
        String right = hashService.hash(Map.of(
            "a", Map.of("c", true, "d", "x"),
            "b", List.of(Map.of("a", 2, "z", 1))
        ));

        assertEquals(left, right);
    }

    @Test
    void itemHashChangesWhenMetadataOrChunkStructureChanges() {
        String original = hashService.hash(Map.of(
            "contentHash", "content-1",
            "metadata", Map.of("documentNumber", "DOC-1"),
            "chunks", List.of(Map.of("chunkIndex", 0, "chunkHash", "chunk-1"))
        ));
        String metadataChanged = hashService.hash(Map.of(
            "contentHash", "content-1",
            "metadata", Map.of("documentNumber", "DOC-2"),
            "chunks", List.of(Map.of("chunkIndex", 0, "chunkHash", "chunk-1"))
        ));
        String chunkChanged = hashService.hash(Map.of(
            "contentHash", "content-1",
            "metadata", Map.of("documentNumber", "DOC-1"),
            "chunks", List.of(Map.of("chunkIndex", 1, "chunkHash", "chunk-1"))
        ));

        assertNotEquals(original, metadataChanged);
        assertNotEquals(original, chunkChanged);
    }
}
