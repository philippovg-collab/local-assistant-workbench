package com.example.demo.infrastructure.material;

import com.example.demo.service.material.DocumentParseResult;

public interface DocumentTextExtractionStrategy {

    boolean supports(String originalFileName, String mediaType);

    DocumentParseResult extract(String originalFileName, String mediaType, byte[] bytes);
}
