package com.example.demo.model;

import jakarta.validation.constraints.Size;

public record ConversationPatchRequest(
    @Size(max = 160)
    String title,
    @Size(max = 32)
    String status
) {
}
