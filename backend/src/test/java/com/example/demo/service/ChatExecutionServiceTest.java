package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.config.LlmProperties;
import com.example.demo.config.MaterialProperties;
import com.example.demo.config.OcrProperties;
import com.example.demo.infrastructure.instruction.FileInstructionRepository;
import com.example.demo.infrastructure.material.DocumentTextExtractor;
import com.example.demo.infrastructure.material.FileMaterialRepository;
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
import com.example.demo.model.InstructionSummary;
import com.example.demo.model.OllamaModelInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChatExecutionServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
        .findAndRegisterModules()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @TempDir
    Path tempDir;

    @Test
    void directModeUsesRequestOverridesAndSelectedInstructions() {
        CapturingLlmClient llmClient = new CapturingLlmClient();
        ChatExecutionService chatExecutionService = createChatExecutionService(llmClient);
        InstructionService instructionService = createInstructionService();

        InstructionSummary instruction = instructionService.createInstruction(new CreateInstructionRequest(
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
    void ragModeUsesLatestVersionForTheSameSourceKey() throws Exception {
        CapturingLlmClient llmClient = new CapturingLlmClient();
        ChatExecutionService chatExecutionService = createChatExecutionService(llmClient);
        MaterialService materialService = createMaterialService();

        materialService.saveText("Pricing FAQ", "Старая цена: 9000 тенге.");
        Thread.sleep(5);
        materialService.saveText("Pricing FAQ", "Новая цена: 12000 тенге.");

        ChatExecutionResponse response = chatExecutionService.execute(new ChatExecutionRequest(
            ChatMode.RAG,
            null,
            "Какая цена?",
            null,
            List.of()
        ));

        String userMessage = llmClient.lastRequest.messages().getLast().content();
        assertTrue(userMessage.contains("12000"));
        assertFalse(userMessage.contains("9000"));
        assertFalse(response.sources().isEmpty());
        assertTrue(response.sources().getFirst().excerpt().contains("12000"));
    }

    @Test
    void ragModeHydratesLegacyStoredMaterials() throws Exception {
        String legacyId = "c6dd6caa-ed4c-4135-8f03-e67d3fbdc4de";
        Files.createDirectories(tempDir.resolve("materials"));
        Files.writeString(
            tempDir.resolve("materials").resolve(legacyId + ".json"),
            """
                {"id":"c6dd6caa-ed4c-4135-8f03-e67d3fbdc4de","title":"Pricing note","sourceType":"file","originalFileName":"smoke-material.txt","mediaType":"text/plain","content":"Тариф Премиум стоит 12000 тенге в месяц и включает приоритетную поддержку.","extractable":true,"createdAt":"2026-04-15T16:12:34.078741Z","updatedAt":"2026-04-15T16:12:34.078742Z"}
                """
        );

        CapturingLlmClient llmClient = new CapturingLlmClient();
        ChatExecutionService chatExecutionService = createChatExecutionService(llmClient);

        ChatExecutionResponse response = chatExecutionService.execute(new ChatExecutionRequest(
            ChatMode.RAG,
            null,
            "Сколько стоит тариф Премиум?",
            null,
            List.of()
        ));

        assertEquals(1, llmClient.chatCalls);
        assertTrue(llmClient.lastRequest.messages().getLast().content().contains("12000"));
        assertFalse(response.sources().isEmpty());

        String upgradedRecord = Files.readString(tempDir.resolve("materials").resolve(legacyId + ".json"));
        assertTrue(upgradedRecord.contains("\"sourceKey\""));
        assertTrue(upgradedRecord.contains("\"chunks\""));
        assertTrue(upgradedRecord.contains("\"contentHash\""));
    }

    @Test
    void ragModeReturnsHelpfulMessageWithoutCallingTheModelWhenThereAreNoMaterials() {
        CapturingLlmClient llmClient = new CapturingLlmClient();
        ChatExecutionService chatExecutionService = createChatExecutionService(llmClient);

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
    }

    @Test
    void ragModeUsesFullChunkInPromptButKeepsShortExcerptInResponse() {
        CapturingLlmClient llmClient = new CapturingLlmClient();
        ChatExecutionService chatExecutionService = createChatExecutionService(llmClient);
        MaterialService materialService = createMaterialService();

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

    private ChatExecutionService createChatExecutionService(CapturingLlmClient llmClient) {
        MaterialService materialService = createMaterialService();
        InstructionService instructionService = createInstructionService();
        PromptPolicyResolver promptPolicyResolver = new PromptPolicyResolver(new LlmProperties());

        return new ChatExecutionService(
            llmClient,
            materialService,
            instructionService,
            promptPolicyResolver
        );
    }

    private MaterialService createMaterialService() {
        MaterialProperties properties = new MaterialProperties();
        FileMaterialRepository repository = new FileMaterialRepository(objectMapper, tempDir.toString());
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
        return new MaterialService(repository, extractor, properties, formatRegistry, ocrCapabilityService);
    }

    private InstructionService createInstructionService() {
        return new InstructionService(new FileInstructionRepository(objectMapper, tempDir.toString()));
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
                request.model(),
                "ok",
                "2026-04-16T10:00:00Z",
                12,
                4,
                16
            );
        }
    }
}
