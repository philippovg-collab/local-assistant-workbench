package com.example.demo.controller;

import com.example.demo.api.ApiException;
import com.example.demo.api.ErrorResponse;
import com.example.demo.config.ChatExecutionProperties;
import com.example.demo.error.CodedException;
import com.example.demo.error.ErrorType;
import com.example.demo.model.ChatExecutionRequest;
import com.example.demo.model.ChatExecutionResponse;
import com.example.demo.model.OllamaModelInfo;
import com.example.demo.service.ChatCompatibilityUsageTelemetry;
import com.example.demo.service.ChatRunExecutionService;
import com.example.demo.service.ChatRunSubmissionCoordinator;
import com.example.demo.service.ModelCatalogService;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final ChatRunSubmissionCoordinator chatRunSubmissionCoordinator;
    private final ModelCatalogService modelCatalogService;
    private final ChatExecutionProperties chatExecutionProperties;
    private final ChatCompatibilityUsageTelemetry compatibilityUsageTelemetry;

    @Autowired
    public ChatController(
        ChatRunSubmissionCoordinator chatRunSubmissionCoordinator,
        ChatRunExecutionService chatRunExecutionService,
        ModelCatalogService modelCatalogService,
        ChatExecutionProperties chatExecutionProperties,
        ChatCompatibilityUsageTelemetry compatibilityUsageTelemetry
    ) {
        this.chatRunSubmissionCoordinator = chatRunSubmissionCoordinator;
        this.chatRunExecutionService = chatRunExecutionService;
        this.modelCatalogService = modelCatalogService;
        this.chatExecutionProperties = chatExecutionProperties;
        this.compatibilityUsageTelemetry = compatibilityUsageTelemetry;
    }

    public ChatController(
        ChatRunExecutionService chatRunExecutionService,
        ModelCatalogService modelCatalogService,
        ChatExecutionProperties chatExecutionProperties,
        ChatCompatibilityUsageTelemetry compatibilityUsageTelemetry
    ) {
        this.chatRunSubmissionCoordinator = null;
        this.chatRunExecutionService = chatRunExecutionService;
        this.modelCatalogService = modelCatalogService;
        this.chatExecutionProperties = chatExecutionProperties;
        this.compatibilityUsageTelemetry = compatibilityUsageTelemetry;
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
        compatibilityUsageTelemetry.recordRequest();
        try {
            ChatExecutionResponse response = chatRunSubmissionCoordinator == null
                ? chatRunExecutionService.submitAndWait(
                    request,
                    Duration.ofSeconds(chatExecutionProperties.getCompatibilityWaitTimeoutSeconds())
                )
                : chatRunSubmissionCoordinator.submitAndWait(
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
        } catch (CodedException exception) {
            if (exception.getType() == ErrorType.REQUEST_TIMEOUT) {
                compatibilityUsageTelemetry.recordTimeoutResponse();
            }
            return compatibilityHeaders(ResponseEntity.status(statusOf(exception.getType())))
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

    private HttpStatus statusOf(ErrorType type) {
        return switch (type) {
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case PAYLOAD_TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
            case REQUEST_TIMEOUT -> HttpStatus.REQUEST_TIMEOUT;
            case PROVIDER_BAD_RESPONSE -> HttpStatus.BAD_GATEWAY;
            case PROVIDER_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case PROVIDER_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case INTERNAL, STORAGE_FAILURE, INVALID_CONFIGURATION -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
