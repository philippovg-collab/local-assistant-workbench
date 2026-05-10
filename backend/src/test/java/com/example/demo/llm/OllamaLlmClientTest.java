package com.example.demo.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.demo.error.ErrorType;
import com.example.demo.error.ProviderException;
import com.example.demo.config.LlmProperties;
import com.example.demo.model.OllamaModelInfo;
import com.example.demo.service.cancellation.ChatCancellationHandle;
import com.example.demo.service.cancellation.ChatCancellationToken;
import com.example.demo.service.cancellation.ChatRunCancelledException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OllamaLlmClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void chatSendsSystemAndUserMessagesSeparately() throws Exception {
        RecordingTransport transport = new RecordingTransport(objectMapper);
        transport.chatResponseJson = """
            {
              "id": "chatcmpl-test",
              "model": "qwen2.5:7b",
              "created": 1710000000,
              "choices": [
                {
                  "index": 0,
                  "message": {
                    "role": "assistant",
                    "content": "ok"
                  },
                  "finish_reason": "stop"
                }
              ],
              "usage": {
                "prompt_tokens": 10,
                "completion_tokens": 2,
                "total_tokens": 12
              }
            }
            """;

        OllamaLlmClient client = new OllamaLlmClient(transport, defaultProperties());
        client.chat(new LlmClient.ChatRequest(
            "qwen2.5:7b",
            List.of(
                new LlmClient.Message("system", "Системная инструкция"),
                new LlmClient.Message("user", "Пользовательский запрос")
            )
        ));

        JsonNode payload = objectMapper.valueToTree(transport.lastPostPayload);
        JsonNode messages = payload.get("messages");

        assertEquals("/v1/chat/completions", transport.lastPostPath);
        assertEquals(Duration.ofSeconds(5), transport.lastPostTimeout);
        assertEquals(2, messages.size());
        assertEquals("system", messages.get(0).get("role").asText());
        assertEquals("user", messages.get(1).get("role").asText());
        assertEquals(0.9d, payload.get("top_p").asDouble());
    }

    @Test
    void chatUsesRequestTimeoutWhenProvided() {
        RecordingTransport transport = new RecordingTransport(objectMapper);
        transport.chatResponseJson = successfulChatResponse();

        OllamaLlmClient client = new OllamaLlmClient(transport, defaultProperties());
        client.chat(new LlmClient.ChatRequest(
            "qwen2.5:7b",
            List.of(new LlmClient.Message("user", "Автотеги")),
            2
        ));

        assertEquals(Duration.ofSeconds(2), transport.lastPostTimeout);
    }

    @Test
    void chatMapsMalformedResponsesToBadGateway() {
        RecordingTransport transport = new RecordingTransport(objectMapper);
        transport.chatResponseJson = "{bad-json";

        OllamaLlmClient client = new OllamaLlmClient(transport, defaultProperties());

        ProviderException exception = assertThrows(ProviderException.class, () -> client.chat(new LlmClient.ChatRequest(
            "qwen2.5:7b",
            List.of(new LlmClient.Message("user", "Привет"))
        )));

        assertEquals(ErrorType.PROVIDER_BAD_RESPONSE, exception.getType());
        assertEquals("llm.provider_parse_failed", exception.getCode());
    }

    @Test
    void chatMapsUnavailableProviderToServiceUnavailable() {
        RecordingTransport transport = new RecordingTransport(objectMapper);
        transport.postFailure = new ProviderException(
            ErrorType.PROVIDER_UNAVAILABLE,
            "llm.provider_unavailable",
            "Unable to reach the local LLM provider"
        );

        OllamaLlmClient client = new OllamaLlmClient(transport, defaultProperties());

        ProviderException exception = assertThrows(ProviderException.class, () -> client.chat(new LlmClient.ChatRequest(
            "qwen2.5:7b",
            List.of(new LlmClient.Message("user", "Привет"))
        )));

        assertEquals(ErrorType.PROVIDER_UNAVAILABLE, exception.getType());
        assertEquals("llm.provider_unavailable", exception.getCode());
    }

    @Test
    void chatUsesCancellableTransportPathAndStopsWhenTokenIsCancelled() {
        RecordingTransport transport = new RecordingTransport(objectMapper);
        transport.chatResponseJson = successfulChatResponse();
        ChatCancellationHandle cancellationHandle = new ChatCancellationHandle();
        transport.onCancellablePost = cancellationHandle::requestCancellation;
        OllamaLlmClient client = new OllamaLlmClient(transport, defaultProperties());

        assertThrows(ChatRunCancelledException.class, () -> client.chat(new LlmClient.ChatRequest(
            "qwen2.5:7b",
            List.of(new LlmClient.Message("user", "Привет"))
        ), cancellationHandle));

        assertEquals("/v1/chat/completions", transport.lastPostPath);
        assertSame(cancellationHandle, transport.lastCancellationToken);
    }

    @Test
    void chatDoesNotSendWhenTokenIsAlreadyCancelled() {
        RecordingTransport transport = new RecordingTransport(objectMapper);
        ChatCancellationHandle cancellationHandle = new ChatCancellationHandle();
        cancellationHandle.requestCancellation();
        OllamaLlmClient client = new OllamaLlmClient(transport, defaultProperties());

        assertThrows(ChatRunCancelledException.class, () -> client.chat(new LlmClient.ChatRequest(
            "qwen2.5:7b",
            List.of(new LlmClient.Message("user", "Привет"))
        ), cancellationHandle));

        assertEquals(null, transport.lastPostPath);
    }

    @Test
    void listModelsReadsTagsEndpoint() {
        RecordingTransport transport = new RecordingTransport(objectMapper);
        transport.tagsResponseJson = """
            {
              "data": [
                { "id": "qwen2.5:7b" },
                { "id": "deepseek-r1:8b" }
              ]
            }
            """;

        OllamaLlmClient client = new OllamaLlmClient(transport, defaultProperties());

        assertEquals("/v1/models", transport.captureNextGetPath(() -> client.listModels()));
        List<OllamaModelInfo> models = client.listModels();
        assertEquals(2, models.size());
        assertEquals("qwen2.5:7b", models.get(0).name());
        assertEquals("deepseek-r1:8b", models.get(1).name());
    }

    @Test
    void listModelsFallsBackToConfiguredModelWhenModelsEndpointIsUnsupported() {
        RecordingTransport transport = new RecordingTransport(objectMapper);
        transport.getFailure = new ProviderException(
            ErrorType.PROVIDER_BAD_RESPONSE,
            "llm.provider_bad_response",
            "LLM provider returned an invalid status while listing models: returned HTTP 404"
        );
        LlmProperties properties = defaultProperties();
        properties.setModel("qwen2.5:7b");

        OllamaLlmClient client = new OllamaLlmClient(transport, properties);

        List<OllamaModelInfo> models = client.listModels();

        assertEquals(1, models.size());
        assertEquals("qwen2.5:7b", models.getFirst().name());
    }

    private LlmProperties defaultProperties() {
        LlmProperties properties = new LlmProperties();
        properties.setBaseUrl("http://127.0.0.1:11434");
        properties.setApiKey("EMPTY");
        properties.setTimeoutSeconds(5);
        properties.setTopP(0.9d);
        return properties;
    }

    private String successfulChatResponse() {
        return """
            {
              "id": "chatcmpl-test",
              "model": "qwen2.5:7b",
              "created": 1710000000,
              "choices": [
                {
                  "index": 0,
                  "message": {
                    "role": "assistant",
                    "content": "ok"
                  },
                  "finish_reason": "stop"
                }
              ]
            }
            """;
    }

    private static final class RecordingTransport extends OllamaApiTransport {

        private final ObjectMapper objectMapper;
        private Object lastPostPayload;
        private String lastPostPath;
        private String lastGetPath;
        private Duration lastPostTimeout;
        private String chatResponseJson;
        private String tagsResponseJson;
        private ProviderException getFailure;
        private ProviderException postFailure;
        private ChatCancellationToken lastCancellationToken;
        private Runnable onCancellablePost = () -> {
        };

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
            lastGetPath = path;
            if (getFailure != null) {
                throw getFailure;
            }
            try {
                return objectMapper.readValue(tagsResponseJson, responseType);
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
            lastPostPath = path;
            lastPostTimeout = timeout;
            lastPostPayload = payload;
            if (postFailure != null) {
                throw postFailure;
            }

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

        @Override
        public <T> T postJson(
            String baseUrl,
            String path,
            Duration timeout,
            Map<String, String> headers,
            Object payload,
            Class<T> responseType,
            ChatCancellationToken cancellationToken,
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
            lastCancellationToken = cancellationToken;
            onCancellablePost.run();
            return postJson(
                baseUrl,
                path,
                timeout,
                headers,
                payload,
                responseType,
                unavailableCode,
                unavailableMessage,
                badResponseCode,
                badResponseMessage,
                parseFailedCode,
                parseFailedMessage,
                interruptedCode,
                interruptedMessage,
                invalidConfigurationCode,
                invalidConfigurationMessage
            );
        }

        private String captureNextGetPath(Runnable action) {
            action.run();
            return lastGetPath;
        }
    }
}
