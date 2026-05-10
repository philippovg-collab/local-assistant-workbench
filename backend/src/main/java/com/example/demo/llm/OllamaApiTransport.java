package com.example.demo.llm;

import com.example.demo.error.ErrorType;
import com.example.demo.error.ProviderException;
import com.example.demo.service.cancellation.ChatCancellationToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.springframework.stereotype.Component;

@Component
public class OllamaApiTransport {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OllamaApiTransport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        // Some OpenAI-compatible gateways mis-handle Java's default protocol
        // negotiation and intermittently treat POST bodies as missing.
        this.httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();
    }

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
        return get(
            baseUrl,
            path,
            timeout,
            Map.of(),
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
        HttpRequest request = requestBuilder(baseUrl, path, timeout, headers)
            .GET()
            .build();
        return exchange(request, responseType, unavailableCode, unavailableMessage, badResponseCode, badResponseMessage, parseFailedCode, parseFailedMessage, interruptedCode, interruptedMessage, invalidConfigurationCode, invalidConfigurationMessage);
    }

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
        return postJson(
            baseUrl,
            path,
            timeout,
            Map.of(),
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
        try {
            HttpRequest request = requestBuilder(baseUrl, path, timeout, headers)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();
            return exchange(request, responseType, unavailableCode, unavailableMessage, badResponseCode, badResponseMessage, parseFailedCode, parseFailedMessage, interruptedCode, interruptedMessage, invalidConfigurationCode, invalidConfigurationMessage);
        } catch (IOException exception) {
            throw new ProviderException(
                ErrorType.PROVIDER_BAD_RESPONSE,
                parseFailedCode,
                parseFailedMessage,
                exception
            );
        } catch (IllegalArgumentException exception) {
            throw new ProviderException(
                ErrorType.INTERNAL,
                "llm.invalid_configuration",
                "Invalid Ollama transport configuration: " + exception.getMessage(),
                exception
            );
        }
    }

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
        return postJson(
            baseUrl,
            path,
            timeout,
            Map.of(),
            payload,
            responseType,
            cancellationToken,
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
        ChatCancellationToken effectiveToken = cancellationToken == null
            ? ChatCancellationToken.none()
            : cancellationToken;
        if (!effectiveToken.canBeCancelled()) {
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
        try {
            effectiveToken.throwIfCancellationRequested();
            HttpRequest request = requestBuilder(baseUrl, path, timeout, headers)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();
            return exchangeCancellable(
                request,
                responseType,
                effectiveToken,
                unavailableCode,
                unavailableMessage,
                badResponseCode,
                badResponseMessage,
                parseFailedCode,
                parseFailedMessage,
                invalidConfigurationCode,
                invalidConfigurationMessage
            );
        } catch (IOException exception) {
            throw new ProviderException(
                ErrorType.PROVIDER_BAD_RESPONSE,
                parseFailedCode,
                parseFailedMessage,
                exception
            );
        } catch (IllegalArgumentException exception) {
            throw new ProviderException(
                ErrorType.INTERNAL,
                "llm.invalid_configuration",
                "Invalid Ollama transport configuration: " + exception.getMessage(),
                exception
            );
        }
    }

    private HttpRequest.Builder requestBuilder(
        String baseUrl,
        String path,
        Duration timeout,
        Map<String, String> headers
    ) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(resolveUri(baseUrl, path))
            .timeout(timeout);
        if (headers != null) {
            headers.forEach((key, value) -> {
                if (value != null && !value.isBlank()) {
                    builder.header(key, value);
                }
            });
        }
        return builder;
    }

    private URI resolveUri(String baseUrl, String path) {
        if (path != null && (path.startsWith("http://") || path.startsWith("https://"))) {
            return URI.create(path);
        }

        String normalizedBaseUrl = baseUrl == null ? "" : baseUrl.trim();
        String normalizedPath = path == null ? "" : path.trim();
        if (normalizedBaseUrl.endsWith(normalizedPath)) {
            return URI.create(normalizedBaseUrl);
        }
        return URI.create(normalizedBaseUrl + normalizedPath);
    }

    private <T> T exchange(
        HttpRequest request,
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
        try {
            HttpResponse<String> response;
            try {
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException exception) {
                throw new ProviderException(ErrorType.PROVIDER_UNAVAILABLE, unavailableCode, unavailableMessage, exception);
            }

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ProviderException(
                    ErrorType.PROVIDER_BAD_RESPONSE,
                    badResponseCode,
                    providerHttpStatusMessage(badResponseMessage, response.statusCode())
                );
            }

            return objectMapper.readValue(response.body(), responseType);
        } catch (IOException exception) {
            throw new ProviderException(
                ErrorType.PROVIDER_BAD_RESPONSE,
                parseFailedCode,
                parseFailedMessage,
                exception
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ProviderException(
                ErrorType.PROVIDER_TIMEOUT,
                interruptedCode,
                interruptedMessage,
                exception
            );
        } catch (IllegalArgumentException exception) {
            throw new ProviderException(
                ErrorType.INTERNAL,
                invalidConfigurationCode,
                invalidConfigurationMessage + ": " + exception.getMessage(),
                exception
            );
        }
    }

    private <T> T exchangeCancellable(
        HttpRequest request,
        Class<T> responseType,
        ChatCancellationToken cancellationToken,
        String unavailableCode,
        String unavailableMessage,
        String badResponseCode,
        String badResponseMessage,
        String parseFailedCode,
        String parseFailedMessage,
        String invalidConfigurationCode,
        String invalidConfigurationMessage
    ) {
        CompletableFuture<HttpResponse<String>> responseFuture = httpClient.sendAsync(
            request,
            HttpResponse.BodyHandlers.ofString()
        );
        ChatCancellationToken.Registration registration =
            cancellationToken.onCancellationRequested(() -> responseFuture.cancel(true));
        try {
            HttpResponse<String> response = responseFuture.join();
            cancellationToken.throwIfCancellationRequested();
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ProviderException(
                    ErrorType.PROVIDER_BAD_RESPONSE,
                    badResponseCode,
                    providerHttpStatusMessage(badResponseMessage, response.statusCode())
                );
            }
            return objectMapper.readValue(response.body(), responseType);
        } catch (CancellationException exception) {
            cancellationToken.throwIfCancellationRequested();
            throw new ProviderException(ErrorType.PROVIDER_UNAVAILABLE, unavailableCode, unavailableMessage, exception);
        } catch (CompletionException exception) {
            cancellationToken.throwIfCancellationRequested();
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            if (cause instanceof ProviderException apiException) {
                throw apiException;
            }
            if (cause instanceof IOException) {
                throw new ProviderException(ErrorType.PROVIDER_UNAVAILABLE, unavailableCode, unavailableMessage, cause);
            }
            throw exception;
        } catch (IOException exception) {
            throw new ProviderException(
                ErrorType.PROVIDER_BAD_RESPONSE,
                parseFailedCode,
                parseFailedMessage,
                exception
            );
        } catch (IllegalArgumentException exception) {
            throw new ProviderException(
                ErrorType.INTERNAL,
                invalidConfigurationCode,
                invalidConfigurationMessage + ": " + exception.getMessage(),
                exception
            );
        } finally {
            registration.close();
        }
    }

    private String providerHttpStatusMessage(String message, int statusCode) {
        return message + ": returned HTTP " + statusCode;
    }
}
