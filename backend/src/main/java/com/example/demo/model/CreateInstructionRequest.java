package com.example.demo.model;

public record CreateInstructionRequest(
    String title,
    String category,
    String content
) {
}
