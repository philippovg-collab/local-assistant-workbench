package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record HealthResponse(
    String application,
    String status,
    String timestamp,
    String ocrStatus,
    String ocrReasonCode,
    String ocrReasonMessage,
    List<String> ocrLanguages
) {
}
