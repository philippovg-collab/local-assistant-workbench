package com.example.demo.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.api.ApiException;
import com.example.demo.config.LlmProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class OllamaLlmClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void chatSendsSystemAndUserMessagesSeparately() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        startServer(exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes()));
            byte[] body = """
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
                """.getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        });

        OllamaLlmClient client = new OllamaLlmClient(objectMapper, propertiesForServer());
        client.chat(new LlmClient.ChatRequest(
            "qwen2.5:7b",
            List.of(
                new LlmClient.Message("system", "Системная инструкция"),
                new LlmClient.Message("user", "Пользовательский запрос")
            )
        ));

        JsonNode payload = objectMapper.readTree(requestBody.get());
        JsonNode messages = payload.get("messages");

        assertEquals(2, messages.size());
        assertEquals("system", messages.get(0).get("role").asText());
        assertEquals("user", messages.get(1).get("role").asText());
    }

    @Test
    void chatMapsMalformedResponsesToBadGateway() throws Exception {
        startServer(exchange -> {
            byte[] body = "{bad-json".getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        });

        OllamaLlmClient client = new OllamaLlmClient(objectMapper, propertiesForServer());

        ApiException exception = assertThrows(ApiException.class, () -> client.chat(new LlmClient.ChatRequest(
            "qwen2.5:7b",
            List.of(new LlmClient.Message("user", "Привет"))
        )));

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatus());
        assertEquals("llm.provider_parse_failed", exception.getCode());
    }

    @Test
    void chatMapsUnavailableProviderToServiceUnavailable() {
        LlmProperties properties = new LlmProperties();
        properties.setBaseUrl("http://127.0.0.1:1");
        properties.setTimeoutSeconds(1);

        OllamaLlmClient client = new OllamaLlmClient(objectMapper, properties);

        ApiException exception = assertThrows(ApiException.class, () -> client.chat(new LlmClient.ChatRequest(
            "qwen2.5:7b",
            List.of(new LlmClient.Message("user", "Привет"))
        )));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatus());
        assertEquals("llm.provider_unavailable", exception.getCode());
    }

    @Test
    void listModelsReadsTagsEndpoint() throws Exception {
        startServer(exchange -> {
            byte[] body = """
                {
                  "models": [
                    { "name": "qwen2.5:7b" },
                    { "name": "qwen2.5:3b" }
                  ]
                }
                """.getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        }, "/api/tags");

        OllamaLlmClient client = new OllamaLlmClient(objectMapper, propertiesForServer());
        assertEquals(2, client.listModels().size());
    }

    private LlmProperties propertiesForServer() {
        LlmProperties properties = new LlmProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setTimeoutSeconds(5);
        return properties;
    }

    private void startServer(ExchangeHandler handler) throws IOException {
        startServer(handler, "/v1/chat/completions");
    }

    private void startServer(ExchangeHandler handler, String path) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(path, handler::handle);
        server.start();
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
