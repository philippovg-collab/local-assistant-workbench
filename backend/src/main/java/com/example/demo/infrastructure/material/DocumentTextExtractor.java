package com.example.demo.infrastructure.material;

public interface DocumentTextExtractor {

    DocumentParseResult extract(String originalFileName, String mediaType, byte[] bytes);
}
