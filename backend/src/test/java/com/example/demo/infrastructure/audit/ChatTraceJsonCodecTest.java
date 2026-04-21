package com.example.demo.infrastructure.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.model.ChatExecutionResponse;
import com.example.demo.model.KnowledgeScopeResolved;
import com.example.demo.model.RetrievalDebug;
import com.example.demo.model.RetrievalTrace;
import org.junit.jupiter.api.Test;

class ChatTraceJsonCodecTest {

    private final ChatTraceJsonCodec codec = new ChatTraceJsonCodec();

    @Test
    void toleratesLegacyTracePayloadsAndUnknownFields() {
        RetrievalDebug debug = codec.read(
            """
                {
                  "queryHints": {},
                  "manualFilters": {},
                  "effectiveFilters": {},
                  "semanticCandidateCount": 1,
                  "lexicalCandidateCount": 1,
                  "rerankCandidateCount": 1,
                  "finalChunkCount": 1,
                  "supportVerdict": "weak",
                  "relevanceProfile": "hybrid-rerank-v1",
                  "activeRolloutFlags": {},
                  "appliedCapabilities": [],
                  "suppressedCapabilities": [],
                  "futureField": true
                }
                """,
            RetrievalDebug.class,
            "chat_run_retrieval_summaries.debug_jsonb"
        );
        assertEquals(1, debug.finalChunkCount());

        RetrievalTrace trace = codec.read(
            """
                {
                  "totalMaterials": 1,
                  "totalActiveMaterials": 1,
                  "totalReadyMaterials": 1,
                  "scopedMaterials": 1,
                  "scopedActiveMaterials": 1,
                  "scopedReadyMaterials": 1,
                  "semanticCandidates": 1,
                  "lexicalCandidates": 1,
                  "finalChunks": 1
                }
                """,
            RetrievalTrace.class,
            "chat_run_retrieval_summaries.trace_jsonb"
        );
        assertEquals("weak", trace.supportVerdict());

        KnowledgeScopeResolved scope = codec.read(
            "{}",
            KnowledgeScopeResolved.class,
            "chat_run_prompt_snapshots.knowledge_scope_resolved_jsonb"
        );
        assertTrue(scope.presets().isEmpty());
        assertTrue(scope.documentClasses().isEmpty());
        assertTrue(scope.tags().isEmpty());
    }

    @Test
    void defaultsResponseCollectionsAndUnknownEnums() {
        ChatExecutionResponse response = codec.read(
            """
                {
                  "mode": "direct",
                  "model": "qwen2.5:7b",
                  "prompt": "Prompt",
                  "answer": "Answer",
                  "createdAt": "2026-04-19T00:00:01Z",
                  "answerModeApplied": "future_mode",
                  "appliedInstructions": null,
                  "instructionTrace": null,
                  "knowledgeScopeResolved": null,
                  "retrievalTrace": null,
                  "sources": null
                }
                """,
            ChatExecutionResponse.class,
            "chat_run_results.response_jsonb"
        );

        assertNull(response.answerModeApplied());
        assertNotNull(response.retrievalTrace());
        assertEquals("none", response.retrievalTrace().supportVerdict());
        assertTrue(response.appliedInstructions().isEmpty());
        assertTrue(response.instructionTrace().isEmpty());
        assertTrue(response.sources().isEmpty());
        assertTrue(response.knowledgeScopeResolved().tags().isEmpty());
    }
}
