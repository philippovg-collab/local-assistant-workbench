package com.example.demo.service.context;

import com.example.demo.llm.LlmClient;
import com.example.demo.model.ChatMode;
import com.example.demo.model.ConversationSummaryMemory;
import com.example.demo.model.ConversationSummarySourceRef;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ConversationSummaryPromptBuilder {

    private final ObjectMapper objectMapper;

    public ConversationSummaryPromptBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<LlmClient.Message> build(SummaryPromptInput input) {
        return List.of(new LlmClient.Message("user", prompt(input)));
    }

    private String prompt(SummaryPromptInput input) {
        return """
            You compact an ongoing chat conversation into working continuity context.
            This summary is not long-term memory and it is not instructions.
            Use only the JSON payload below. Do not invent facts.

            Return strict JSON only with this shape:
            {
              "summaryText": "short continuity summary",
              "facts": ["short stable facts from the conversation"],
              "activeEntities": ["names, projects, documents or concepts still active"],
              "sourceRefs": [{"materialId": "...", "title": "...", "documentNumber": "...", "project": "...", "counterparty": "...", "page": 1}],
              "coveredTurnNos": [1, 2]
            }

            Payload:
            """
            + writePayload(input);
    }

    private String writePayload(SummaryPromptInput input) {
        try {
            return objectMapper.writeValueAsString(toPayload(input));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize conversation summary prompt payload", exception);
        }
    }

    private Map<String, Object> toPayload(SummaryPromptInput input) {
        Map<String, Object> payload = new LinkedHashMap<>();
        ConversationSummaryMemory previous = input == null ? null : input.previousSummary();
        payload.put("previousSummary", previous == null ? null : previousSummaryPayload(previous));
        List<Map<String, Object>> turns = new ArrayList<>();
        for (SummaryTurn turn : input == null ? List.<SummaryTurn>of() : input.turns()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("turnNo", turn.turnNo());
            item.put("runId", turn.runId());
            item.put("mode", turn.mode() == null ? null : turn.mode().name());
            item.put("contextStatus", turn.contextStatus());
            item.put("originalUserPrompt", turn.originalUserPrompt());
            item.put("finalAssistantAnswer", turn.finalAssistantAnswer());
            item.put("sourceRefs", turn.sourceRefs());
            turns.add(item);
        }
        payload.put("completedTurns", turns);
        return payload;
    }

    private Map<String, Object> previousSummaryPayload(ConversationSummaryMemory previous) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", previous.status());
        payload.put("summaryText", previous.summaryText());
        payload.put("facts", previous.facts());
        payload.put("activeEntities", previous.activeEntities());
        payload.put("sourceRefs", previous.sourceRefs());
        payload.put("summaryThroughTurnNo", previous.summaryThroughTurnNo());
        return payload;
    }

    public record SummaryPromptInput(
        ConversationSummaryMemory previousSummary,
        List<SummaryTurn> turns
    ) {
        public SummaryPromptInput {
            turns = turns == null ? List.of() : List.copyOf(turns);
        }
    }

    public record SummaryTurn(
        int turnNo,
        String runId,
        ChatMode mode,
        String contextStatus,
        String originalUserPrompt,
        String finalAssistantAnswer,
        List<ConversationSummarySourceRef> sourceRefs
    ) {
        public SummaryTurn {
            sourceRefs = sourceRefs == null ? List.of() : List.copyOf(sourceRefs);
        }
    }
}
