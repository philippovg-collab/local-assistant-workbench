package com.example.demo.infrastructure.material;

import java.util.List;

public record DocumentParseResult(
    List<DocumentBlock> blocks,
    MaterialMetadataHints metadataHints,
    List<ParseWarning> warnings,
    Integer pageCount,
    String extractor,
    boolean ocrUsed,
    DocumentParserProfile parserProfile
) {
    public DocumentParseResult {
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
        metadataHints = metadataHints == null ? MaterialMetadataHints.empty() : metadataHints;
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        extractor = extractor == null || extractor.isBlank() ? "unknown" : extractor.trim();
        parserProfile = parserProfile == null ? DocumentParserProfile.RICH_TEXT : parserProfile;
    }

    public String firstWarningCode() {
        return warnings.isEmpty() ? null : warnings.getFirst().code();
    }

    public String firstWarningMessage() {
        return warnings.isEmpty() ? null : warnings.getFirst().message();
    }
}
