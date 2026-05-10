package com.example.demo.service.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.demo.config.ContextProperties;
import com.example.demo.llm.LlmClient;
import com.example.demo.model.ConversationSummarySourceRef;
import com.example.demo.model.OllamaModelInfo;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConversationSummaryServiceTest {

    @Test
    void invalidJsonFailsWithoutPartialPayload() {
        ConversationSummaryService service = service(new ContextProperties());

        assertThrows(IllegalArgumentException.class, () -> service.parseAndSanitize("not json"));
    }

    @Test
    void sanitizesCapsAndDeduplicatesOutput() {
        ContextProperties properties = new ContextProperties();
        properties.setSummaryMaxOutputChars(12);
        properties.setSummaryMaxFacts(2);
        properties.setSummaryMaxActiveEntities(1);
        properties.setSummaryMaxSourceRefs(2);
        ConversationSummaryService service = service(properties);

        ConversationSummaryPayload payload = service.parseAndSanitize("""
            {
              "summaryText": "Hello\\u0000 summary text",
              "facts": ["A", "a", "B", "C"],
              "activeEntities": ["Entity", "Other"],
              "sourceRefs": [
                {"materialId": "m1", "title": "Doc", "documentNumber": "D-1", "project": "P", "counterparty": "C", "page": 1},
                {"materialId": "m1", "title": "Doc duplicate", "documentNumber": "D-1", "project": "P", "counterparty": "C", "page": 1},
                {"materialId": "m2", "title": "Doc 2", "documentNumber": "D-2", "project": "P", "counterparty": "C", "page": 2}
              ],
              "coveredTurnNos": [2, 2, 3]
            }
            """);

        assertEquals("Hello summar", payload.summaryText());
        assertEquals(List.of("A", "B"), payload.facts());
        assertEquals(List.of("Entity"), payload.activeEntities());
        assertEquals(2, payload.sourceRefs().size());
        assertEquals(new ConversationSummarySourceRef("m1", "Doc", "D-1", "P", "C", 1), payload.sourceRefs().getFirst());
        assertEquals(List.of(2, 3), payload.coveredTurnNos());
    }

    @Test
    void redactsSecretsFromSummaryPayloadBeforePersistence() {
        ConversationSummaryService service = service(new ContextProperties());

        ConversationSummaryPayload payload = service.parseAndSanitize("""
            {
              "summaryText": "Authorization: Bearer summary-secret",
              "facts": ["api_key=fact-secret"],
              "activeEntities": ["token=entity-secret"],
              "sourceRefs": [
                {"materialId": "m1", "title": "password=title-secret", "documentNumber": "D-1", "project": "P", "counterparty": "C", "page": 1}
              ],
              "coveredTurnNos": [1]
            }
            """);

        String serialized = payload.toString();
        assertFalse(serialized.contains("summary-secret"));
        assertFalse(serialized.contains("fact-secret"));
        assertFalse(serialized.contains("entity-secret"));
        assertFalse(serialized.contains("title-secret"));
    }

    private ConversationSummaryService service(ContextProperties properties) {
        return new ConversationSummaryService(
            properties,
            new NoopLlmClient(),
            new ConversationSummaryPromptBuilder(JsonMapper.builder().build()),
            JsonMapper.builder().build()
        );
    }

    private static final class NoopLlmClient implements LlmClient {
        @Override
        public List<OllamaModelInfo> listModels() {
            return List.of();
        }

        @Override
        public ChatResult chat(ChatRequest request) {
            return new ChatResult("model", "{}", "2026-05-10T00:00:00Z", 1, 1, 2);
        }
    }
}
