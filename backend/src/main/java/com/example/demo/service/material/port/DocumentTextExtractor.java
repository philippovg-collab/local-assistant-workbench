package com.example.demo.service.material.port;

import com.example.demo.service.material.DocumentParseResult;

public interface DocumentTextExtractor {

    DocumentParseResult extract(String originalFileName, String mediaType, byte[] bytes);
}
