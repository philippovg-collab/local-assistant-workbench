package com.example.demo.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.example.demo.model.AnswerMode;
import com.example.demo.model.ChatExecutionRequest;
import com.example.demo.model.ChatExecutionResponse;
import com.example.demo.model.ChatMode;
import com.example.demo.model.ChatSource;
import com.example.demo.model.DocumentBlockConfidence;
import com.example.demo.model.DocumentBlockType;
import com.example.demo.model.HealthResponse;
import com.example.demo.model.InstructionCategory;
import com.example.demo.model.InstructionScopeLevel;
import com.example.demo.model.MaterialMetadataSnapshot;
import com.example.demo.model.MaterialSearchHit;
import com.example.demo.model.MaterialSearchHitNeighbor;
import com.example.demo.model.MaterialSearchResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class DtoSerializationCompatibilityTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesContractEnumsWithWireValues() throws Exception {
        assertEquals("\"direct\"", objectMapper.writeValueAsString(ChatMode.DIRECT));
        assertEquals("\"strict_sources_only\"", objectMapper.writeValueAsString(AnswerMode.STRICT_SOURCES_ONLY));
        assertEquals("\"safety\"", objectMapper.writeValueAsString(InstructionCategory.SAFETY));
        assertEquals("\"workspace_project\"", objectMapper.writeValueAsString(InstructionScopeLevel.WORKSPACE_PROJECT));
        assertEquals("\"TABLE\"", objectMapper.writeValueAsString(DocumentBlockType.TABLE));
        assertEquals("\"LOW\"", objectMapper.writeValueAsString(DocumentBlockConfidence.LOW));
    }

    @Test
    void serializesSearchDtosWithoutPackageSpecificShape() throws Exception {
        ChatSource source = new ChatSource(
            "material-1",
            "material-1:2",
            "Dispatch matrix",
            "Table row excerpt",
            91,
            0.91d,
            List.of("dispatch"),
            "/api/materials/material-1",
            2,
            5,
            "tika",
            false,
            DocumentBlockType.TABLE,
            MaterialMetadataSnapshot.empty(),
            0.12d,
            2.4d,
            null
        );
        MaterialSearchHit hit = new MaterialSearchHit(
            "material-1",
            "material-1:2",
            "Dispatch matrix",
            "Table row excerpt",
            2,
            5,
            DocumentBlockType.TABLE,
            91,
            0.12d,
            2.4d,
            List.of("dispatch"),
            "/api/materials/material-1",
            MaterialMetadataSnapshot.empty(),
            List.of(new MaterialSearchHitNeighbor("material-1:1", 1, "Neighbor excerpt", 4, DocumentBlockType.NARRATIVE))
        );

        JsonNode sourceJson = objectMapper.valueToTree(source);
        JsonNode responseJson = objectMapper.valueToTree(new MaterialSearchResponse("dispatch", List.of(hit), null));

        assertEquals("TABLE", sourceJson.get("chunkType").asText());
        assertEquals("TABLE", responseJson.at("/hits/0/chunkType").asText());
        assertEquals("NARRATIVE", responseJson.at("/hits/0/neighbors/0/chunkType").asText());
        assertFalse(responseJson.has("debug"), "null debug stays omitted from the wire contract");
    }

    @Test
    void serializesChatRequestAndResponseWireNames() throws Exception {
        ChatExecutionRequest request = new ChatExecutionRequest(
            ChatMode.RAG,
            "llama3",
            "Summarize dispatch rules",
            "Use internal instructions",
            List.of("instruction-1"),
            AnswerMode.WITH_QUOTES,
            null,
            List.of("scenario-1"),
            "temporary guardrail"
        );
        ChatExecutionResponse response = new ChatExecutionResponse(
            ChatMode.RAG,
            "llama3",
            "Summarize dispatch rules",
            "Answer",
            "ready",
            "2026-05-09T12:00:00Z",
            10,
            20,
            30,
            AnswerMode.WITH_QUOTES,
            List.of(),
            List.of(),
            null,
            null,
            null,
            List.of(),
            "audit-1"
        );

        JsonNode requestJson = objectMapper.valueToTree(request);
        JsonNode responseJson = objectMapper.valueToTree(response);

        assertEquals("rag", requestJson.get("mode").asText());
        assertEquals("with_quotes", requestJson.get("answerMode").asText());
        assertEquals("with_quotes", responseJson.get("answerModeApplied").asText());
        assertEquals("audit-1", responseJson.get("auditRunId").asText());
    }

    @Test
    void serializesHealthResponseWithExistingWireNames() throws Exception {
        HealthResponse response = new HealthResponse(
            "demo",
            "UP",
            "2026-05-09T12:00:00Z",
            "UP",
            null,
            null,
            null,
            "UP",
            "READY",
            null,
            null,
            7,
            6,
            1,
            5,
            "UP",
            "postgres",
            "postgres",
            null,
            null,
            new HealthResponse.SearchSyncBacklog(1, 2, 3, null, null, "2026-05-09T11:59:00Z"),
            "UP",
            null,
            null,
            "UP",
            null,
            null,
            "2026-05-09T12:00:00Z",
            "DISABLED",
            null,
            null,
            List.of("rus", "eng"),
            "UP",
            null,
            "UP",
            null
        );

        JsonNode json = objectMapper.valueToTree(response);

        assertEquals("UP", json.get("status").asText());
        assertEquals(1, json.at("/searchSyncBacklog/pendingCount").asInt());
        assertEquals("DISABLED", json.get("ocrStatus").asText());
        assertFalse(json.has("directReasonCode"), "null health fields stay omitted from the wire contract");
    }
}
