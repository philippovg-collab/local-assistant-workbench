package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.demo.config.LlmProperties;
import com.example.demo.config.MaterialProperties;
import com.example.demo.llm.LlmClient;
import com.example.demo.llm.LlmTracingClient;
import com.example.demo.llmprovider.ActiveLlmProviderResolver;
import com.example.demo.model.OllamaModelInfo;
import com.example.demo.service.material.MaterialMetadataHints;
import java.util.List;
import org.junit.jupiter.api.Test;

class MaterialAutoTaggingServiceTest {

    @Test
    void returnsNormalizedLlmTagsFromJsonWithoutManualDuplicatesOrGenericTags() {
        CapturingLlmClient llmClient = new CapturingLlmClient();
        llmClient.nextAnswer = """
            {"tags":["Manual Grid","Document","Transformer Protection","North Upgrade","North Upgrade","  "]}
            """;
        MaterialAutoTaggingService service = createService(llmClient);

        List<String> tags = service.suggestTags(new MaterialAutoTaggingService.TaggingRequest(
            "Generic title",
            "text",
            null,
            "text/plain",
            "Материал описывает релейную защиту трансформатора и проект North Upgrade.",
            List.of("manual grid"),
            MaterialMetadataHints.empty()
        ));

        assertEquals(List.of("transformer protection", "north upgrade"), tags);
        assertNotNull(llmClient.lastRequest);
        assertEquals("test-model", llmClient.lastRequest.model());
        assertEquals(8, llmClient.lastRequest.timeoutSeconds());
        String prompt = llmClient.lastRequest.messages().getLast().content();
        assertTrue(prompt.contains("Главный источник: текст материала"));
        assertTrue(prompt.contains("Материал описывает релейную защиту трансформатора"));
        assertTrue(prompt.contains("Ручные теги: manual grid"));
    }

    @Test
    void usesActiveChatProviderModelForAutoTagging() {
        CapturingLlmClient llmClient = new CapturingLlmClient();
        llmClient.nextAnswer = "{\"tags\":[\"active model\"]}";
        ActiveLlmProviderResolver activeProviderResolver = mock(ActiveLlmProviderResolver.class);
        when(activeProviderResolver.defaultChatModel()).thenReturn("active-chat-model");
        MaterialAutoTaggingService service = createService(llmClient, new MaterialProperties(), activeProviderResolver);

        List<String> tags = service.suggestTags(new MaterialAutoTaggingService.TaggingRequest(
            "Grid memo",
            "text",
            null,
            "text/plain",
            "Материал описывает релейную защиту трансформатора и диспетчерский процесс.",
            List.of(),
            MaterialMetadataHints.empty()
        ));

        assertEquals(List.of("active model"), tags);
        assertEquals("active-chat-model", llmClient.lastRequest.model());
    }

    @Test
    void throwsParseFailureWhenLlmReturnsInvalidJson() {
        CapturingLlmClient llmClient = new CapturingLlmClient();
        llmClient.nextAnswer = "теги: защита, трансформатор";
        MaterialAutoTaggingService service = createService(llmClient);

        MaterialAutoTaggingService.AutoTaggingException exception = assertThrows(
            MaterialAutoTaggingService.AutoTaggingException.class,
            () -> service.suggestTags(new MaterialAutoTaggingService.TaggingRequest(
                "Grid memo",
                "text",
                null,
                "text/plain",
                "Описание защиты трансформатора и диспетчерского процесса.",
                List.of(),
                MaterialMetadataHints.empty()
            ))
        );

        assertEquals(MaterialAutoTaggingService.FailureKind.PARSE, exception.kind());
    }

    @Test
    void throwsProviderFailureWhenLlmThrows() {
        CapturingLlmClient llmClient = new CapturingLlmClient();
        llmClient.failure = new IllegalStateException("provider down");
        MaterialAutoTaggingService service = createService(llmClient);

        MaterialAutoTaggingService.AutoTaggingException exception = assertThrows(
            MaterialAutoTaggingService.AutoTaggingException.class,
            () -> service.suggestTags(new MaterialAutoTaggingService.TaggingRequest(
                "Grid memo",
                "text",
                null,
                "text/plain",
                "Описание защиты трансформатора и диспетчерского процесса.",
                List.of(),
                MaterialMetadataHints.empty()
            ))
        );

        assertEquals(MaterialAutoTaggingService.FailureKind.PROVIDER, exception.kind());
    }

    @Test
    void samplesLargeContentAndCapsTagsByLargeBudget() {
        CapturingLlmClient llmClient = new CapturingLlmClient();
        llmClient.nextAnswer = """
            {"tags":["tag01","tag02","tag03","tag04","tag05","tag06","tag07","tag08","tag09","tag10","tag11","tag12","tag13","tag14","tag15","tag16","tag17","tag18"]}
            """;
        MaterialProperties materialProperties = new MaterialProperties();
        materialProperties.getAutoTags().setMaxInputChars(1_200);
        materialProperties.getAutoTags().setTimeoutSeconds(3);
        MaterialAutoTaggingService service = createService(llmClient, materialProperties);
        String content = "начало защита трансформатора\n"
            + "промежуточный диспетчерский текст ".repeat(1_200)
            + "конец аварийная автоматика";

        List<String> tags = service.suggestTags(new MaterialAutoTaggingService.TaggingRequest(
            "Large report",
            "file",
            "large-report.pdf",
            "application/pdf",
            content,
            List.of(),
            MaterialMetadataHints.empty()
        ));

        assertEquals(16, tags.size());
        assertEquals(3, llmClient.lastRequest.timeoutSeconds());
        String prompt = llmClient.lastRequest.messages().getLast().content();
        assertTrue(prompt.contains("Верни от 10 до 16"));
        assertTrue(prompt.contains("[НАЧАЛО МАТЕРИАЛА]"));
        assertTrue(prompt.contains("[СЕРЕДИНА МАТЕРИАЛА]"));
        assertTrue(prompt.contains("[КОНЕЦ МАТЕРИАЛА]"));
        assertTrue(prompt.length() < content.length());
    }

    private MaterialAutoTaggingService createService(CapturingLlmClient llmClient) {
        return createService(llmClient, new MaterialProperties());
    }

    private MaterialAutoTaggingService createService(
        CapturingLlmClient llmClient,
        MaterialProperties materialProperties
    ) {
        return createService(llmClient, materialProperties, null);
    }

    private MaterialAutoTaggingService createService(
        CapturingLlmClient llmClient,
        MaterialProperties materialProperties,
        ActiveLlmProviderResolver activeProviderResolver
    ) {
        LlmProperties llmProperties = new LlmProperties();
        llmProperties.setModel("test-model");
        if (activeProviderResolver == null) {
            return new MaterialAutoTaggingService(
                new LlmTracingClient(llmClient),
                llmProperties,
                materialProperties
            );
        }
        return new MaterialAutoTaggingService(
            new LlmTracingClient(llmClient),
            llmProperties,
            activeProviderResolver,
            materialProperties
        );
    }

    private static final class CapturingLlmClient implements LlmClient {

        private String nextAnswer = "{\"tags\":[]}";
        private RuntimeException failure;
        private ChatRequest lastRequest;

        @Override
        public List<OllamaModelInfo> listModels() {
            return List.of(new OllamaModelInfo("test-model"));
        }

        @Override
        public ChatResult chat(ChatRequest request) {
            lastRequest = request;
            if (failure != null) {
                throw failure;
            }
            return new ChatResult(
                request.model(),
                nextAnswer,
                "2026-04-21T00:00:00Z",
                null,
                null,
                null
            );
        }
    }
}
