package com.example.demo.model;

import jakarta.validation.Valid;

public record UpdateMaterialRequest(
    String title,
    String content,
    @Valid
    MaterialMetadataInput metadata
) {
}
