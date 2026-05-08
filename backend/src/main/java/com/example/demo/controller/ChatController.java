package com.example.demo.controller;

import com.example.demo.api.ApiException;
import com.example.demo.api.ErrorResponse;
import com.example.demo.config.ChatExecutionProperties;
import com.example.demo.model.ChatExecutionRequest;
import com.example.demo.model.ChatExecutionResponse;
import com.example.demo.model.OllamaModelInfo;
import com.example.demo.service.ChatRunExecutionService;
import com.example.demo.service.ModelCatalogService;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ChatController {

    private static final String SUCCESSOR_LINK = "</api/chat-runs>; rel=\"successor-version\"";

    private final ChatRunExecutionService chatRunExecutionService;
    private final ModelCatalogService modelCatalogService;
    private final ChatExecutionProperties chatExecutionProperties;

    public ChatController(
        ChatRunExecutionService chatRunExecutionService,
        ModelCatalogService modelCatalogService,
        ChatExecutionProperties chatExecutionProperties
    ) {
        this.chatRunExecutionService = chatRunExecutionService;
        this.modelCatalogService = modelCatalogService;
        this.chatExecutionProperties = chatExecutionProperties;
    }

    @GetMapping("/models")
    public List<OllamaModelInfo> listModels() {
        return modelCatalogService.listModels();
    }

    @PostMapping("/chat")
    public ResponseEntity<?> chat(@Valid @RequestBody ChatExecutionRequest request) {
        if (request == null) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "request.invalid_payload",
                "Request payload is required"
            );
        }
        try {
            ChatExecutionResponse response = chatRunExecutionService.submitAndWait(
                request,
                Duration.ofSeconds(chatExecutionProperties.getCompatibilityWaitTimeoutSeconds())
            );
            return compatibilityHeaders(ResponseEntity.ok()).body(response);
        } catch (ApiException exception) {
            return compatibilityHeaders(ResponseEntity.status(exception.getStatus()))
                .body(new ErrorResponse(
                    exception.getCode(),
                    exception.getMessage(),
                    Instant.now().toString(),
                    null
                ));
        }
    }

    private ResponseEntity.BodyBuilder compatibilityHeaders(ResponseEntity.BodyBuilder builder) {
        return builder
            .header("Deprecation", "true")
            .header(HttpHeaders.LINK, SUCCESSOR_LINK);
    }
}
