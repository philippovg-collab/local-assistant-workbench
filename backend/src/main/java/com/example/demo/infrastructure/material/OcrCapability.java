package com.example.demo.infrastructure.material;

import java.util.List;

public record OcrCapability(
    boolean scannedPdfSupport,
    String mode,
    String reasonCode,
    String reasonMessage,
    List<String> languages,
    int maxPages
) {

    public static OcrCapability embeddedTextOnly(
        String reasonCode,
        String reasonMessage,
        List<String> languages,
        int maxPages
    ) {
        return new OcrCapability(
            false,
            "embedded_text_only",
            reasonCode,
            reasonMessage,
            List.copyOf(languages),
            maxPages
        );
    }

    public static OcrCapability embeddedTextAndOcr(List<String> languages, int maxPages) {
        return new OcrCapability(
            true,
            "embedded_text_and_ocr",
            null,
            null,
            List.copyOf(languages),
            maxPages
        );
    }
}
