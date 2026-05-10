package com.example.demo.llm;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.error.ProviderException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OllamaApiTransportTest {

    @Test
    void nonSuccessfulResponsesDoNotExposeRemoteBodyInExceptionMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/fail", exchange -> {
            byte[] body = "{\"error\":\"Authorization: Bearer leaked-remote-secret\"}"
                .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(401, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            OllamaApiTransport transport = new OllamaApiTransport(new ObjectMapper());
            String baseUrl = "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort();

            ProviderException exception = assertThrows(
                ProviderException.class,
                () -> transport.get(
                    baseUrl,
                    "/fail",
                    Duration.ofSeconds(1),
                    Map.of("Authorization", "Bearer request-secret"),
                    JsonNode.class,
                    "llm.provider_unavailable",
                    "Unable to reach provider",
                    "llm.provider_bad_response",
                    "Provider returned invalid status",
                    "llm.provider_parse_failed",
                    "Unable to parse provider response",
                    "llm.provider_interrupted",
                    "Provider request was interrupted",
                    "llm.invalid_configuration",
                    "Invalid provider configuration"
                )
            );

            assertTrue(exception.getMessage().contains("HTTP 401"));
            assertFalse(exception.getMessage().contains("leaked-remote-secret"));
            assertFalse(exception.getMessage().contains("request-secret"));
            assertFalse(exception.getMessage().contains("Authorization"));
        } finally {
            server.stop(0);
        }
    }
}
