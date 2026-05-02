package com.example.demo.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.demo.api.ApiException;
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
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

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

        ApiException exception = assertThrows(ApiException.class, () -> client.chat(new LlmClient.ChatRequest(
            "qwen2.5:7b",
            List.of(new LlmClient.Message("user", "Привет"))
        )));

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatus());
        assertEquals("llm.provider_parse_failed", exception.getCode());
    }

    @Test
    void chatMapsUnavailableProviderToServiceUnavailable() {
        RecordingTransport transport = new RecordingTransport(objectMapper);
        transport.postFailure = new ApiException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "llm.provider_unavailable",
            "Unable to reach the local LLM provider"
        );

        OllamaLlmClient client = new OllamaLlmClient(transport, defaultProperties());

        ApiException exception = assertThrows(ApiException.class, () -> client.chat(new LlmClient.ChatRequest(
            "qwen2.5:7b",
            List.of(new LlmClient.Message("user", "Привет"))
        )));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatus());
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
              "models": [
                { "name": "qwen2.5:7b" },
                { "name": "deepseek-r1:8b" }
              ]
            }
            """;

        OllamaLlmClient client = new OllamaLlmClient(transport, defaultProperties());

        assertEquals("/api/tags", transport.captureNextGetPath(() -> client.listModels()));
        List<OllamaModelInfo> models = client.listModels();
        assertEquals(2, models.size());
        assertEquals("qwen2.5:7b", models.get(0).name());
        assertEquals("deepseek-r1:8b", models.get(1).name());
    }

    private LlmProperties defaultProperties() {
        LlmProperties properties = new LlmProperties();
        properties.setBaseUrl("http://127.0.0.1:11434");
        properties.setTimeoutSeconds(5);
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
        private ApiException postFailure;
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
            try {
                return objectMapper.readValue(tagsResponseJson, responseType);
            } catch (IOException exception) {
                throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
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
                throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
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
