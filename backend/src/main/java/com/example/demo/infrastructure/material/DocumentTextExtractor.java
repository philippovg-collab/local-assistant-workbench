package com.example.demo.infrastructure.material;

public interface DocumentTextExtractor {

    ExtractedDocument extract(String originalFileName, String mediaType, byte[] bytes);
}
