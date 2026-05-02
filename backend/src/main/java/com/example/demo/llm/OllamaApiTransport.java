package com.example.demo.llm;

import com.example.demo.api.ApiException;
import com.example.demo.service.cancellation.ChatCancellationToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class OllamaApiTransport {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OllamaApiTransport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().build();
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
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .timeout(timeout)
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
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();
            return exchange(request, responseType, unavailableCode, unavailableMessage, badResponseCode, badResponseMessage, parseFailedCode, parseFailedMessage, interruptedCode, interruptedMessage, invalidConfigurationCode, invalidConfigurationMessage);
        } catch (IOException exception) {
            throw new ApiException(
                HttpStatus.BAD_GATEWAY,
                parseFailedCode,
                parseFailedMessage,
                exception
            );
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
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
        ChatCancellationToken effectiveToken = cancellationToken == null
            ? ChatCancellationToken.none()
            : cancellationToken;
        if (!effectiveToken.canBeCancelled()) {
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
        try {
            effectiveToken.throwIfCancellationRequested();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(timeout)
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
            throw new ApiException(
                HttpStatus.BAD_GATEWAY,
                parseFailedCode,
                parseFailedMessage,
                exception
            );
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "llm.invalid_configuration",
                "Invalid Ollama transport configuration: " + exception.getMessage(),
                exception
            );
        }
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
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, unavailableCode, unavailableMessage, exception);
            }

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    badResponseCode,
                    badResponseMessage + ": " + response.statusCode() + ": " + response.body()
                );
            }

            return objectMapper.readValue(response.body(), responseType);
        } catch (IOException exception) {
            throw new ApiException(
                HttpStatus.BAD_GATEWAY,
                parseFailedCode,
                parseFailedMessage,
                exception
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApiException(
                HttpStatus.GATEWAY_TIMEOUT,
                interruptedCode,
                interruptedMessage,
                exception
            );
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
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
                throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    badResponseCode,
                    badResponseMessage + ": " + response.statusCode() + ": " + response.body()
                );
            }
            return objectMapper.readValue(response.body(), responseType);
        } catch (CancellationException exception) {
            cancellationToken.throwIfCancellationRequested();
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, unavailableCode, unavailableMessage, exception);
        } catch (CompletionException exception) {
            cancellationToken.throwIfCancellationRequested();
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            if (cause instanceof ApiException apiException) {
                throw apiException;
            }
            if (cause instanceof IOException) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, unavailableCode, unavailableMessage, cause);
            }
            throw exception;
        } catch (IOException exception) {
            throw new ApiException(
                HttpStatus.BAD_GATEWAY,
                parseFailedCode,
                parseFailedMessage,
                exception
            );
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                invalidConfigurationCode,
                invalidConfigurationMessage + ": " + exception.getMessage(),
                exception
            );
        } finally {
            registration.close();
        }
    }
}
