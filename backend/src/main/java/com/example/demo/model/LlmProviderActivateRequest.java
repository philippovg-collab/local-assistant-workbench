package com.example.demo.model;

import jakarta.validation.constraints.NotNull;

public record LlmProviderActivateRequest(
    @NotNull
    LlmProviderPurpose purpose
) {
}
