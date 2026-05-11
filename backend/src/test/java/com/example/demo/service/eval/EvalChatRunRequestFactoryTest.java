package com.example.demo.service.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.model.AnswerMode;
import com.example.demo.model.ChatMode;
import com.example.demo.model.eval.CreateE2EEvalRunRequest;
import com.example.demo.model.eval.EvalCase;
import com.example.demo.model.eval.EvalCaseOrigin;
import com.example.demo.model.eval.EvalCaseType;
import com.example.demo.model.eval.EvalExpectedMode;
import com.example.demo.model.eval.EvalSeverity;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EvalChatRunRequestFactoryTest {

    private final EvalChatRunRequestFactory factory = new EvalChatRunRequestFactory(JsonMapper.builder().findAndAddModules().build());

    @Test
    void buildsIsolatedDurableChatRunRequest() {
        var request = factory.create(evalCase(), new CreateE2EEvalRunRequest(
            "dataset",
            null,
            List.of(),
            "snapshot",
            "config",
            Instant.parse("2026-05-10T00:00:00Z"),
            "gpt-test",
            null,
            null,
            null,
            List.of()
        ));

        assertEquals(ChatMode.RAG, request.mode());
        assertEquals(AnswerMode.WITH_QUOTES, request.answerMode());
        assertEquals("What is the limit?", request.prompt());
        assertNull(request.conversationId());
        assertNull(request.parentRunId());
        assertNull(request.clientTurnId());
        assertFalse(request.persistConversation());
        assertFalse(request.contextOptions().includeHistory());
        assertFalse(request.contextOptions().useHistory());
        assertFalse(request.contextOptions().useStickyState());
        assertFalse(request.contextOptions().resolveRetrievalQuery());
        assertFalse(request.contextOptions().useSummary());
        assertFalse(request.contextOptions().useLongTermMemory());
        assertTrue(request.temporaryInstruction().contains("Return exactly one JSON object"));
    }

    private EvalCase evalCase() {
        return new EvalCase(
            "case",
            "dataset",
            "case-1",
            1,
            EvalCaseType.EXACT_FACT,
            EvalExpectedMode.ANSWER,
            EvalSeverity.BLOCKER,
            "What is the limit?",
            Map.of("workspaceKey", "general"),
            Map.of("versionLabel", "v1"),
            Map.of("facts", List.of("limit is 10")),
            Map.of("answerMode", "with_quotes"),
            Map.of(),
            new EvalCaseOrigin("manual", "seed", null, Map.of()),
            Instant.parse("2026-05-10T00:00:00Z"),
            Instant.parse("2026-05-10T00:00:00Z")
        );
    }
}
