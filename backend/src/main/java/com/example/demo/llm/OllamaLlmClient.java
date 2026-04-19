package com.example.demo.llm;

import com.example.demo.api.ApiException;
import com.example.demo.config.LlmProperties;
import com.example.demo.model.OllamaModelInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class OllamaLlmClient implements LlmClient {

    private static final ObjectMapper JSON_MAPPER = JsonMapper.builder().findAndAddModules().build();

    private final LlmProperties properties;
    private final OllamaApiTransport transport;

    public OllamaLlmClient(OllamaApiTransport transport, LlmProperties properties) {
        this.transport = transport;
        this.properties = properties;
    }

    @Override
    public List<OllamaModelInfo> listModels() {
        try {
            OllamaTagsResponse payload = transport.get(
                properties.getBaseUrl(),
                "/api/tags",
                Duration.ofSeconds(properties.getTimeoutSeconds()),
                OllamaTagsResponse.class,
                "llm.provider_unavailable",
                "Unable to reach the local LLM provider",
                "llm.provider_bad_response",
                "LLM provider returned an invalid status while listing models",
                "llm.provider_parse_failed",
                "Unable to parse the model list returned by the LLM provider",
                "llm.provider_interrupted",
                "Model list request was interrupted",
                "llm.invalid_configuration",
                "Invalid LLM configuration"
            );
            if (payload == null || payload.models() == null) {
                return List.of();
            }

            return payload.models().stream()
                .map(model -> new OllamaModelInfo(model.name()))
                .toList();
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
        Instant startedAt = Instant.now();
        OpenAiChatCompletionRequest payload = new OpenAiChatCompletionRequest(
            request.model(),
            request.messages().stream()
                .map(message -> new OpenAiMessage(message.role(), message.content()))
                .toList(),
            properties.getTemperature(),
            false
        );

        try {
            OpenAiChatCompletionResponse completion = transport.postJson(
                properties.getBaseUrl(),
                "/v1/chat/completions",
                Duration.ofSeconds(properties.getTimeoutSeconds()),
                payload,
                OpenAiChatCompletionResponse.class,
                "llm.provider_unavailable",
                "Unable to reach the local LLM provider",
                "llm.provider_bad_response",
                "LLM provider returned an invalid status",
                "llm.provider_parse_failed",
                "Unable to parse the LLM provider response",
                "llm.provider_interrupted",
                "LLM request was interrupted",
                "llm.invalid_configuration",
                "Invalid LLM configuration"
            );

            if (completion.choices() == null || completion.choices().isEmpty()) {
                throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "llm.provider_empty_choices",
                    "LLM provider returned no choices"
                );
            }

            OpenAiMessage message = completion.choices().getFirst().message();
            String finishReason = completion.choices().getFirst().finishReason();
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
                usage == null ? null : usage.totalTokens(),
                rawResponseOf(completion),
                finishReason,
                Duration.between(startedAt, Instant.now()).toMillis()
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

    private String rawResponseOf(OpenAiChatCompletionResponse completion) {
        try {
            return JSON_MAPPER.writeValueAsString(completion);
        } catch (JsonProcessingException exception) {
            return null;
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
