package com.example.demo.model;

import jakarta.validation.constraints.Size;

public record MemoryReviewActionRequest(
    @Size(max = 500)
    String reason
) {
}
