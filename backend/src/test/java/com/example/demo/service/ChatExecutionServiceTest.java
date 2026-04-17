package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.api.ApiException;
import com.example.demo.config.LlmProperties;
import com.example.demo.config.MaterialProperties;
import com.example.demo.config.OcrProperties;
import com.example.demo.config.RagProperties;
import com.example.demo.infrastructure.material.DocumentTextExtractor;
import com.example.demo.infrastructure.material.MaterialFormatRegistry;
import com.example.demo.infrastructure.material.OcrCapabilityService;
import com.example.demo.infrastructure.material.OcrClient;
import com.example.demo.infrastructure.material.PdfDocumentExtractionStrategy;
import com.example.demo.infrastructure.material.PlainTextDocumentExtractionStrategy;
import com.example.demo.infrastructure.material.RoutingDocumentTextExtractor;
import com.example.demo.infrastructure.material.TesseractRuntimeProbe;
import com.example.demo.infrastructure.material.TikaDocumentTextExtractor;
import com.example.demo.llm.LlmClient;
import com.example.demo.model.ChatExecutionRequest;
import com.example.demo.model.ChatExecutionResponse;
import com.example.demo.model.ChatMode;
import com.example.demo.model.CreateInstructionRequest;
import com.example.demo.model.InstructionDetail;
import com.example.demo.model.OllamaModelInfo;
import com.example.demo.support.DeterministicEmbeddingClient;
import com.example.demo.support.InMemoryInstructionRepository;
import com.example.demo.support.InMemoryMaterialRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChatExecutionServiceTest {

    @Test
    void directModeUsesRequestOverridesAndSelectedInstructions() {
        CapturingLlmClient llmClient = new CapturingLlmClient();
        ChatExecutionFixture fixture = createChatExecutionFixture(llmClient);
        ChatExecutionService chatExecutionService = fixture.chatExecutionService();
        InstructionService instructionService = fixture.instructionService();

        InstructionDetail instruction = instructionService.createInstruction(new CreateInstructionRequest(
            "Stay concise",
            "system",
            "Отвечай кратко."
        ));

        ChatExecutionResponse response = chatExecutionService.execute(new ChatExecutionRequest(
            ChatMode.DIRECT,
            "override-model",
            "Объясни кратко",
            "Используй деловой тон",
            List.of(instruction.id())
        ));

        assertEquals("override-model", llmClient.lastRequest.model());
        assertEquals(2, llmClient.lastRequest.messages().size());
        assertEquals("system", llmClient.lastRequest.messages().getFirst().role());
        assertTrue(llmClient.lastRequest.messages().getFirst().content().contains("Используй деловой тон"));
        assertTrue(llmClient.lastRequest.messages().getFirst().content().contains("Отвечай кратко."));
        assertEquals(ChatMode.DIRECT, response.mode());
        assertEquals(1, response.appliedInstructions().size());
    }

    @Test
    void rejectsBlankPromptsBeforeCallingTheModel() {
        CapturingLlmClient llmClient = new CapturingLlmClient();
        ChatExecutionFixture fixture = createChatExecutionFixture(llmClient);

        ApiException exception = assertThrows(ApiException.class, () -> fixture.chatExecutionService().execute(
            new ChatExecutionRequest(ChatMode.RAG, null, "   ", null, List.of())
        ));

        assertEquals("chat.invalid_request", exception.getCode());
        assertEquals(0, llmClient.chatCalls);
    }

    @Test
    void ragModeUsesOnlyActiveMaterialVersionWhenSourceKeyMatches() {
        CapturingLlmClient llmClient = new CapturingLlmClient();
        ChatExecutionFixture fixture = createChatExecutionFixture(llmClient);
        ChatExecutionService chatExecutionService = fixture.chatExecutionService();
        MaterialService materialService = fixture.materialService();

        materialService.saveText("Pricing FAQ", "Старая цена: 9000 тенге.");
        materialService.saveText("Pricing FAQ", "Новая цена: 12000 тенге.");

        ChatExecutionResponse response = chatExecutionService.execute(new ChatExecutionRequest(
            ChatMode.RAG,
            null,
            "Какая старая цена?",
            null,
            List.of()
        ));

        String userMessage = llmClient.lastRequest.messages().getLast().content();
        assertFalse(userMessage.contains("9000"));
        assertTrue(userMessage.contains("12000"));
        assertFalse(response.sources().isEmpty());
        assertTrue(response.sources().stream().allMatch(source -> !source.excerpt().contains("9000")));
    }

    @Test
    void promptPolicySeparatesSystemSafetyContextAndUserInstructions() {
        CapturingLlmClient llmClient = new CapturingLlmClient();
        ChatExecutionFixture fixture = createChatExecutionFixture(llmClient);
        InstructionService instructionService = fixture.instructionService();

        InstructionDetail system = instructionService.createInstruction(new CreateInstructionRequest(
            "System role",
            "system",
            "Говори формально."
        ));
        InstructionDetail safety = instructionService.createInstruction(new CreateInstructionRequest(
            "Safety block",
            "safety",
            "Не раскрывай конфиденциальные данные."
        ));
        InstructionDetail context = instructionService.createInstruction(new CreateInstructionRequest(
            "Context block",
            "context",
            "Учитывай только внутренние регламенты."
        ));
        InstructionDetail user = instructionService.createInstruction(new CreateInstructionRequest(
            "User block",
            "user",
            "Ответ начни с короткого вывода."
        ));

        fixture.materialService().saveText("Policy", "Внутренний регламент: срок ответа 2 дня.");

        fixture.chatExecutionService().execute(new ChatExecutionRequest(
            ChatMode.RAG,
            null,
            "Какой срок ответа?",
            "Базовый системный промпт",
            List.of(system.id(), safety.id(), context.id(), user.id())
        ));

        assertTrue(llmClient.lastRequest.messages().getFirst().content().contains("Базовый системный промпт"));
        assertTrue(llmClient.lastRequest.messages().getFirst().content().contains("System instructions"));
        assertTrue(llmClient.lastRequest.messages().getFirst().content().contains("Safety restrictions"));
        String userMessage = llmClient.lastRequest.messages().getLast().content();
        assertTrue(userMessage.contains("Context instructions"));
        assertTrue(userMessage.contains("User instructions"));
        assertTrue(userMessage.indexOf("Context instructions") < userMessage.indexOf("Retrieved context"));
        assertTrue(userMessage.indexOf("User instructions") < userMessage.indexOf("User request"));
    }

    @Test
    void ragModeFindsSemanticMatchForParaphrasedQuestion() {
        CapturingLlmClient llmClient = new CapturingLlmClient();
        ChatExecutionFixture fixture = createChatExecutionFixture(llmClient);
        ChatExecutionService chatExecutionService = fixture.chatExecutionService();
        MaterialService materialService = fixture.materialService();

        materialService.saveText("Pricing FAQ", "Тариф Премиум стоит 12000 тенге в месяц.");

        ChatExecutionResponse response = chatExecutionService.execute(new ChatExecutionRequest(
            ChatMode.RAG,
            null,
            "Сколько стоит премиальный план?",
            null,
            List.of()
        ));

        assertEquals(1, llmClient.chatCalls);
        assertFalse(response.sources().isEmpty());
        assertTrue(response.sources().getFirst().excerpt().contains("12000"));
        assertTrue(response.sources().getFirst().score() > 0);
        assertEquals("ready", response.contextStatus());
    }

    @Test
    void ragModeReturnsHelpfulMessageWithoutCallingTheModelWhenThereAreNoMaterials() {
        CapturingLlmClient llmClient = new CapturingLlmClient();
        ChatExecutionFixture fixture = createChatExecutionFixture(llmClient);
        ChatExecutionService chatExecutionService = fixture.chatExecutionService();

        ChatExecutionResponse response = chatExecutionService.execute(new ChatExecutionRequest(
            ChatMode.RAG,
            null,
            "Какая цена?",
            null,
            List.of()
        ));

        assertEquals(0, llmClient.chatCalls);
        assertTrue(response.answer().contains("Сначала добавьте материалы"));
        assertTrue(response.sources().isEmpty());
        assertEquals("no-context", response.contextStatus());
    }

    @Test
    void ragModeReturnsNoContextForIrrelevantQuestions() {
        CapturingLlmClient llmClient = new CapturingLlmClient();
        ChatExecutionFixture fixture = createChatExecutionFixture(llmClient);
        ChatExecutionService chatExecutionService = fixture.chatExecutionService();
        MaterialService materialService = fixture.materialService();

        materialService.saveText("Pricing FAQ", "Тариф Премиум стоит 12000 тенге в месяц.");

        ChatExecutionResponse response = chatExecutionService.execute(new ChatExecutionRequest(
            ChatMode.RAG,
            null,
            "Какая сегодня погода в Алматы?",
            null,
            List.of()
        ));

        assertEquals(0, llmClient.chatCalls);
        assertEquals("no-context", response.contextStatus());
        assertTrue(response.sources().isEmpty());
    }

    @Test
    void ragModeUsesFullChunkInPromptButKeepsShortExcerptInResponse() {
        CapturingLlmClient llmClient = new CapturingLlmClient();
        ChatExecutionFixture fixture = createChatExecutionFixture(llmClient);
        ChatExecutionService chatExecutionService = fixture.chatExecutionService();
        MaterialService materialService = fixture.materialService();

        String tailMarker = "секретный_хвост_контекста";
        String content = "длинный контекст ".repeat(40) + tailMarker;
        materialService.saveText("Long note", content);

        ChatExecutionResponse response = chatExecutionService.execute(new ChatExecutionRequest(
            ChatMode.RAG,
            null,
            tailMarker,
            null,
            List.of()
        ));

        String userMessage = llmClient.lastRequest.messages().getLast().content();
        assertTrue(userMessage.contains(tailMarker));
        assertFalse(response.sources().isEmpty());
        assertFalse(response.sources().getFirst().excerpt().contains(tailMarker));
    }

    private ChatExecutionFixture createChatExecutionFixture(CapturingLlmClient llmClient) {
        MaterialService materialService = createMaterialService();
        InstructionService instructionService = createInstructionService();
        PromptPolicyResolver promptPolicyResolver = new PromptPolicyResolver(new LlmProperties());

        return new ChatExecutionFixture(
            new ChatExecutionService(
                llmClient,
                materialService,
                instructionService,
                promptPolicyResolver
            ),
            materialService,
            instructionService
        );
    }

    private MaterialService createMaterialService() {
        MaterialProperties properties = new MaterialProperties();
        MaterialFormatRegistry formatRegistry = new MaterialFormatRegistry();
        OcrProperties ocrProperties = new OcrProperties();
        OcrClient ocrClient = (imagePath, pageNumber) -> "OCR fallback text for page " + pageNumber;
        OcrCapabilityService ocrCapabilityService = new OcrCapabilityService(
            ocrProperties,
            (binaryPath, timeoutSeconds) -> new TesseractRuntimeProbe.CommandResult(
                0,
                """
                List of available languages in "/tmp/tessdata" (3):
                kaz
                rus
                eng
                """,
                "",
                false
            )
        );
        DocumentTextExtractor extractor = new RoutingDocumentTextExtractor(List.of(
            new PlainTextDocumentExtractionStrategy(formatRegistry),
            new PdfDocumentExtractionStrategy(formatRegistry, ocrProperties, ocrClient, ocrCapabilityService),
            new TikaDocumentTextExtractor(properties, formatRegistry)
        ));
        InMemoryMaterialRepository repository = new InMemoryMaterialRepository();
        DeterministicEmbeddingClient embeddingClient = new DeterministicEmbeddingClient();
        MaterialContentSupport contentSupport = new MaterialContentSupport(properties);
        MaterialIndexingService indexingService = new MaterialIndexingService(
            repository,
            repository,
            contentSupport,
            embeddingClient,
            properties,
            Runnable::run
        );
        return new MaterialService(
            new MaterialQueryService(
                repository,
                repository,
                properties,
                formatRegistry,
                ocrCapabilityService,
                contentSupport,
                indexingService
            ),
            new MaterialIngestionService(
                repository,
                repository,
                extractor,
                properties,
                contentSupport,
                indexingService
            ),
            new MaterialRetrievalService(
                repository,
                repository,
                embeddingClient,
                new RagProperties(),
                new HybridChunkRanker(),
                contentSupport
            )
        );
    }

    private InstructionService createInstructionService() {
        return new InstructionService(new InMemoryInstructionRepository());
    }

    private static final class CapturingLlmClient implements LlmClient {

        private ChatRequest lastRequest;
        private int chatCalls = 0;

        @Override
        public List<OllamaModelInfo> listModels() {
            return List.of(new OllamaModelInfo("qwen2.5:7b"));
        }

        @Override
        public ChatResult chat(ChatRequest request) {
            chatCalls++;
            lastRequest = request;
            return new ChatResult(
                request.model() == null ? "qwen2.5:7b" : request.model(),
                "ok",
                "2026-04-16T10:00:00Z",
                1,
                1,
                2
            );
        }
    }

    private record ChatExecutionFixture(
        ChatExecutionService chatExecutionService,
        MaterialService materialService,
        InstructionService instructionService
    ) {
    }
}
