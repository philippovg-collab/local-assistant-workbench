package com.example.demo.model;

public record CreateTextMaterialRequest(
    String title,
    String content,
    MaterialMetadataInput metadata
) {
    public CreateTextMaterialRequest(String title, String content) {
        this(title, content, null);
    }
}
