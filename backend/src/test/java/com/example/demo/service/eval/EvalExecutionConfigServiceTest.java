package com.example.demo.service.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.demo.model.eval.EvalRuntimeStateSnapshot;
import com.example.demo.model.eval.ResolveEvalExecutionConfigRequest;
import com.example.demo.service.eval.port.CorpusSnapshotRepository;
import com.example.demo.service.eval.port.EvalDatasetRepository;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EvalExecutionConfigServiceTest {

    private final EvalRuntimeStateService runtimeStateService = mock(EvalRuntimeStateService.class);
    private final CorpusSnapshotRepository snapshotRepository = mock(CorpusSnapshotRepository.class);
    private final EvalDatasetRepository datasetRepository = mock(EvalDatasetRepository.class);
    private final EvalExecutionConfigService service = new EvalExecutionConfigService(
        runtimeStateService,
        snapshotRepository,
        datasetRepository,
        new EvalHashService(JsonMapper.builder().findAndAddModules().build())
    );

    @Test
    void configHashChangesWhenOnlyGitCommitShaChanges() {
        when(runtimeStateService.capture()).thenReturn(runtimeState());

        String first = service.resolve(request("sha-1")).configHash();
        String second = service.resolve(request("sha-2")).configHash();

        assertNotEquals(first, second);
    }

    @Test
    void nullGitCommitShaIsStableAndPartOfHashPayload() {
        when(runtimeStateService.capture()).thenReturn(runtimeState());

        String nullHash = service.resolve(request(null)).configHash();
        String repeatedNullHash = service.resolve(request(null)).configHash();
        String nonNullHash = service.resolve(request("sha-1")).configHash();

        assertEquals(nullHash, repeatedNullHash);
        assertNotEquals(nullHash, nonNullHash);
    }

    private ResolveEvalExecutionConfigRequest request(String gitCommitSha) {
        return new ResolveEvalExecutionConfigRequest(
            gitCommitSha,
            "dataset-id",
            "v1",
            null,
            Instant.parse("2026-05-10T00:00:00Z"),
            null,
            null,
            Map.of("temperature", 0)
        );
    }

    private EvalRuntimeStateSnapshot runtimeState() {
        return new EvalRuntimeStateSnapshot(
            Map.of(
                "chatProvider", Map.of("provider", "test-chat"),
                "embeddingProvider", Map.of("provider", "test-embedding"),
                "rag", Map.of("topK", 5),
                "rolloutFlags", Map.of("structuredV1", false)
            ),
            "search-state-hash",
            Instant.parse("2026-05-10T00:00:00Z")
        );
    }
}
