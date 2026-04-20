package com.example.demo.model;

import jakarta.validation.Valid;

public record CreateTextMaterialRequest(
    String title,
    String content,
    @Valid
    MaterialMetadataInput metadata
) {
    public CreateTextMaterialRequest(String title, String content) {
        this(title, content, null);
    }
}
