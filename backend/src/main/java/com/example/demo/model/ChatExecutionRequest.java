package com.example.demo.model;

import java.util.List;

public record ChatExecutionRequest(
    ChatMode mode,
    String model,
    String prompt,
    String systemPrompt,
    List<String> instructionIds
) {
}
