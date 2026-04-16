package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MaterialPdfUploadPolicyResponse(
    boolean enabled,
    boolean scannedPdfSupport,
    String mode,
    String ocrReasonCode,
    String ocrReasonMessage,
    List<String> ocrLanguages,
    int ocrMaxPages
) {
}
