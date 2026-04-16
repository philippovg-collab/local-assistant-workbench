package com.example.demo.llm;

import com.example.demo.api.ApiException;
import com.example.demo.config.LlmProperties;
import com.example.demo.model.OllamaModelInfo;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class OllamaLlmClient implements LlmClient {

    private final ObjectMapper objectMapper;
    private final LlmProperties properties;
    private final HttpClient httpClient;

    public OllamaLlmClient(ObjectMapper objectMapper, LlmProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
            .build();
    }

    @Override
    public List<OllamaModelInfo> listModels() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.getBaseUrl() + "/api/tags"))
                .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .GET()
                .build();

            HttpResponse<String> response;
            try {
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException exception) {
                throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "llm.provider_unavailable",
                    "Unable to reach the local LLM provider",
                    exception
                );
            }

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "llm.provider_bad_response",
                    "LLM provider returned status " + response.statusCode() + " while listing models"
                );
            }

            OllamaTagsResponse payload = objectMapper.readValue(response.body(), OllamaTagsResponse.class);
            if (payload == null || payload.models() == null) {
                return List.of();
            }

            return payload.models().stream()
                .map(model -> new OllamaModelInfo(model.name()))
                .toList();
        } catch (IOException exception) {
            throw new ApiException(
                HttpStatus.BAD_GATEWAY,
                "llm.provider_parse_failed",
                "Unable to parse the model list returned by the LLM provider",
                exception
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApiException(
                HttpStatus.GATEWAY_TIMEOUT,
                "llm.provider_interrupted",
                "Model list request was interrupted",
                exception
            );
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "llm.invalid_configuration",
                "Invalid LLM configuration: " + exception.getMessage(),
                exception
            );
        }
    }

    @Override
    public ChatResult chat(ChatRequest request) {
        OpenAiChatCompletionRequest payload = new OpenAiChatCompletionRequest(
            request.model(),
            request.messages().stream()
                .map(message -> new OpenAiMessage(message.role(), message.content()))
                .toList(),
            properties.getTemperature(),
            false
        );

        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(properties.getBaseUrl() + "/v1/chat/completions"))
                .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();

            HttpResponse<String> response;
            try {
                response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            } catch (IOException exception) {
                throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "llm.provider_unavailable",
                    "Unable to reach the local LLM provider",
                    exception
                );
            }

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "llm.provider_bad_response",
                    "LLM provider returned status " + response.statusCode() + ": " + response.body()
                );
            }

            OpenAiChatCompletionResponse completion = objectMapper.readValue(
                response.body(),
                OpenAiChatCompletionResponse.class
            );

            if (completion.choices() == null || completion.choices().isEmpty()) {
                throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "llm.provider_empty_choices",
                    "LLM provider returned no choices"
                );
            }

            OpenAiMessage message = completion.choices().getFirst().message();
            OpenAiUsage usage = completion.usage();
            String createdAt = completion.created() == null
                ? Instant.now().toString()
                : Instant.ofEpochSecond(completion.created()).toString();

            return new ChatResult(
                completion.model(),
                message == null ? "" : message.content(),
                createdAt,
                usage == null ? null : usage.promptTokens(),
                usage == null ? null : usage.completionTokens(),
                usage == null ? null : usage.totalTokens()
            );
        } catch (IOException exception) {
            throw new ApiException(
                HttpStatus.BAD_GATEWAY,
                "llm.provider_parse_failed",
                "Unable to parse the LLM provider response",
                exception
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApiException(
                HttpStatus.GATEWAY_TIMEOUT,
                "llm.provider_interrupted",
                "LLM request was interrupted",
                exception
            );
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "llm.invalid_configuration",
                "Invalid LLM configuration: " + exception.getMessage(),
                exception
            );
        }
    }

    private record OllamaTagsResponse(List<OllamaTagModel> models) {
    }

    private record OllamaTagModel(String name) {
    }

    private record OpenAiChatCompletionRequest(
        String model,
        List<OpenAiMessage> messages,
        double temperature,
        boolean stream
    ) {
    }

    private record OpenAiMessage(
        String role,
        String content
    ) {
    }

    private record OpenAiChatCompletionResponse(
        String id,
        String model,
        Long created,
        List<OpenAiChoice> choices,
        OpenAiUsage usage
    ) {
    }

    private record OpenAiChoice(
        int index,
        OpenAiMessage message,
        @JsonProperty("finish_reason") String finishReason
    ) {
    }

    private record OpenAiUsage(
        @JsonProperty("prompt_tokens") Integer promptTokens,
        @JsonProperty("completion_tokens") Integer completionTokens,
        @JsonProperty("total_tokens") Integer totalTokens
    ) {
    }
}
