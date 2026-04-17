package com.example.demo.service;

import com.example.demo.model.ChatSource;

public record RetrievedMaterialChunk(
    String contextText,
    ChatSource source
) {
}
