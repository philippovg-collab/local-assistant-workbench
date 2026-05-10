package com.example.demo.model;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record ClientEventRequest(
    @NotBlank
    String severity,
    @NotBlank
    String type,
    String message,
    String stack,
    String componentStack,
    String path,
    String requestId,
    Map<String, Object> metadata
) {
}
