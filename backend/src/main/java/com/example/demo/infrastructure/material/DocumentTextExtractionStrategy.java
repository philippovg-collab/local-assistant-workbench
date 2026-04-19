package com.example.demo.infrastructure.material;

public interface DocumentTextExtractionStrategy {

    boolean supports(String originalFileName, String mediaType);

    DocumentParseResult extract(String originalFileName, String mediaType, byte[] bytes);
}
