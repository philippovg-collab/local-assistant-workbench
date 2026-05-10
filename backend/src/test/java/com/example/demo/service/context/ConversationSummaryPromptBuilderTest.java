package com.example.demo.service.context;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.model.ChatMode;
import com.example.demo.model.ConversationSummaryMemory;
import com.example.demo.model.ConversationSummarySourceRef;
import com.example.demo.service.context.ConversationSummaryPromptBuilder.SummaryPromptInput;
import com.example.demo.service.context.ConversationSummaryPromptBuilder.SummaryTurn;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConversationSummaryPromptBuilderTest {

    @Test
    void promptContainsOnlyAllowedTurnAndSourceMetadata() {
        ConversationSummaryPromptBuilder builder = new ConversationSummaryPromptBuilder(JsonMapper.builder().build());
        ConversationSummaryMemory previousSummary = new ConversationSummaryMemory(
            "conversation-1",
            "Previous compact summary",
            List.of("Fact A"),
            List.of("Project Alpha"),
            List.of(new ConversationSummarySourceRef("material-1", "Doc", "D-1", "Alpha", "Counterparty", 3)),
            4,
            "run-4",
            Instant.parse("2026-05-10T00:00:00Z"),
            "READY",
            1
        );

        String prompt = builder.build(new SummaryPromptInput(
            previousSummary,
            List.of(new SummaryTurn(
                5,
                "run-5",
                ChatMode.RAG,
                "ready",
                "Original user prompt",
                "Final assistant answer",
                List.of(new ConversationSummarySourceRef("material-2", "Allowed title", "D-2", "Beta", "CP", 7))
            ))
        )).getFirst().content();

        assertTrue(prompt.contains("Original user prompt"));
        assertTrue(prompt.contains("Final assistant answer"));
        assertTrue(prompt.contains("Allowed title"));
        assertTrue(prompt.contains("documentNumber"));
        assertFalse(prompt.contains("contextText"));
        assertFalse(prompt.contains("temporaryInstruction"));
        assertFalse(prompt.contains("systemPrompt"));
    }
}
