package com.example.demo.model;

import jakarta.validation.Valid;

public record CreateTextMaterialRequest(
    String title,
    String content,
    @Valid
    MaterialMetadataInput metadata,
    @Valid
    MaterialLineageOverrideInput lineageOverride
) {
    public CreateTextMaterialRequest(String title, String content) {
        this(title, content, null, null);
    }
}
