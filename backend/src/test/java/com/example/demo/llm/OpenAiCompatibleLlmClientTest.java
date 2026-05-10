package com.example.demo.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.demo.error.ErrorType;
import com.example.demo.error.ProviderException;
import com.example.demo.llmprovider.ActiveLlmProvider;
import com.example.demo.model.LlmProviderStatus;
import com.example.demo.model.LlmProviderType;
import com.example.demo.model.OllamaModelInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenAiCompatibleLlmClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void chatUsesActiveProviderDefaultsHeadersAndTimeout() {
        RecordingTransport transport = new RecordingTransport(objectMapper);
        transport.chatResponseJson = successfulChatResponse("active-chat-model");
        OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(
            transport,
            () -> activeProvider(
                "http://active-chat.local",
                "/chat/completions",
                "/models",
                "active-chat-model",
                "X-Api-Key",
                "",
                "secret-key",
                17
            )
        );

        client.chat(new LlmClient.ChatRequest(null, List.of(new LlmClient.Message("user", "hello"))));

        JsonNode payload = objectMapper.valueToTree(transport.lastPostPayload);
        assertEquals("http://active-chat.local", transport.lastPostBaseUrl);
        assertEquals("/chat/completions", transport.lastPostPath);
        assertEquals(Duration.ofSeconds(17), transport.lastPostTimeout);
        assertEquals("secret-key", transport.lastPostHeaders.get("X-Api-Key"));
        assertEquals("active-chat-model", payload.get("model").asText());
        assertEquals(0.4d, payload.get("temperature").asDouble());
    }

    @Test
    void requestModelAndTimeoutOverrideActiveProviderDefaults() {
        RecordingTransport transport = new RecordingTransport(objectMapper);
        transport.chatResponseJson = successfulChatResponse("request-model");
        OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(
            transport,
            () -> activeProvider(
                "http://active-chat.local",
                "/chat/completions",
                "/models",
                "active-chat-model",
                "Authorization",
                "Bearer",
                "secret-key",
                17
            )
        );

        client.chat(new LlmClient.ChatRequest(
            "request-model",
            List.of(new LlmClient.Message("user", "hello")),
            3
        ));

        JsonNode payload = objectMapper.valueToTree(transport.lastPostPayload);
        assertEquals(Duration.ofSeconds(3), transport.lastPostTimeout);
        assertEquals("Bearer secret-key", transport.lastPostHeaders.get("Authorization"));
        assertEquals("request-model", payload.get("model").asText());
    }

    @Test
    void listModelsUsesActiveProviderModelsPathAndHeaders() {
        RecordingTransport transport = new RecordingTransport(objectMapper);
        transport.modelsResponseJson = """
            {"data":[{"id":"qwen2.5:7b"},{"id":"deepseek-r1:8b"}]}
            """;
        OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(
            transport,
            () -> activeProvider(
                "http://active-chat.local",
                "/chat/completions",
                "/catalog",
                "active-chat-model",
                "X-Provider-Token",
                "Token",
                "secret-key",
                9
            )
        );

        List<OllamaModelInfo> models = client.listModels();

        assertEquals("http://active-chat.local", transport.lastGetBaseUrl);
        assertEquals("/catalog", transport.lastGetPath);
        assertEquals(Duration.ofSeconds(9), transport.lastGetTimeout);
        assertEquals("Token secret-key", transport.lastGetHeaders.get("X-Provider-Token"));
        assertEquals(List.of("qwen2.5:7b", "deepseek-r1:8b"), models.stream().map(OllamaModelInfo::name).toList());
    }

    private ActiveLlmProvider activeProvider(
        String baseUrl,
        String chatPath,
        String modelsPath,
        String defaultModel,
        String authHeaderName,
        String authScheme,
        String apiKey,
        int timeoutSeconds
    ) {
        return new ActiveLlmProvider(
            "provider-id",
            "Provider",
            LlmProviderType.OPENAI_COMPATIBLE,
            baseUrl,
            apiKey,
            authHeaderName,
            authScheme,
            chatPath,
            modelsPath,
            "/embeddings",
            defaultModel,
            "embedding-model",
            0.4d,
            0.9d,
            timeoutSeconds,
            768,
            LlmProviderStatus.UP,
            false
        );
    }

    private String successfulChatResponse(String model) {
        return """
            {
              "id": "chatcmpl-test",
              "model": "%s",
              "choices": [
                {
                  "index": 0,
                  "message": {"role": "assistant", "content": "ok"},
                  "finish_reason": "stop"
                }
              ]
            }
            """.formatted(model);
    }

    private static final class RecordingTransport extends OllamaApiTransport {

        private final ObjectMapper objectMapper;
        private String chatResponseJson;
        private String modelsResponseJson;
        private String lastPostBaseUrl;
        private String lastPostPath;
        private Duration lastPostTimeout;
        private Map<String, String> lastPostHeaders;
        private Object lastPostPayload;
        private String lastGetBaseUrl;
        private String lastGetPath;
        private Duration lastGetTimeout;
        private Map<String, String> lastGetHeaders;

        private RecordingTransport(ObjectMapper objectMapper) {
            super(objectMapper);
            this.objectMapper = objectMapper;
        }

        @Override
        public <T> T get(
            String baseUrl,
            String path,
            Duration timeout,
            Map<String, String> headers,
            Class<T> responseType,
            String unavailableCode,
            String unavailableMessage,
            String badResponseCode,
            String badResponseMessage,
            String parseFailedCode,
            String parseFailedMessage,
            String interruptedCode,
            String interruptedMessage,
            String invalidConfigurationCode,
            String invalidConfigurationMessage
        ) {
            lastGetBaseUrl = baseUrl;
            lastGetPath = path;
            lastGetTimeout = timeout;
            lastGetHeaders = headers;
            try {
                return objectMapper.readValue(modelsResponseJson, responseType);
            } catch (IOException exception) {
                throw new ProviderException(
                    ErrorType.PROVIDER_BAD_RESPONSE,
                    parseFailedCode,
                    parseFailedMessage,
                    exception
                );
            }
        }

        @Override
        public <T> T postJson(
            String baseUrl,
            String path,
            Duration timeout,
            Map<String, String> headers,
            Object payload,
            Class<T> responseType,
            String unavailableCode,
            String unavailableMessage,
            String badResponseCode,
            String badResponseMessage,
            String parseFailedCode,
            String parseFailedMessage,
            String interruptedCode,
            String interruptedMessage,
            String invalidConfigurationCode,
            String invalidConfigurationMessage
        ) {
            lastPostBaseUrl = baseUrl;
            lastPostPath = path;
            lastPostTimeout = timeout;
            lastPostHeaders = headers;
            lastPostPayload = payload;
            try {
                return objectMapper.readValue(chatResponseJson, responseType);
            } catch (IOException exception) {
                throw new ProviderException(
                    ErrorType.PROVIDER_BAD_RESPONSE,
                    parseFailedCode,
                    parseFailedMessage,
                    exception
                );
            }
        }
    }
}
