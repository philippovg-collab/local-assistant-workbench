package com.example.demo.model;

import java.util.List;

public record MaterialUploadPolicyResponse(
    int maxUploadBytes,
    List<String> acceptedExtensions,
    List<String> acceptedMimeHints,
    boolean richDocumentSupport,
    MaterialPdfUploadPolicyResponse pdf
) {
}
