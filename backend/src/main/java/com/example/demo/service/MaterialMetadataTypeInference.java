package com.example.demo.service;

import com.example.demo.model.DocumentType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.util.StringUtils;

final class MaterialMetadataTypeInference {

    DocumentType inferDocumentType(String searchableTitle, String originalFileName, String mediaType) {
        String combined = joinSearchText(searchableTitle, normalizeFileStem(originalFileName), mediaType);
        if (!StringUtils.hasText(combined)) {
            return null;
        }
        String normalized = combined.toLowerCase(Locale.ROOT);
        if (normalized.contains("faq") || normalized.contains("вопрос") || normalized.contains("ответ")) {
            return DocumentType.FAQ;
        }
        if (normalized.contains("policy") || normalized.contains("политик") || normalized.contains("регламент")) {
            return DocumentType.POLICY;
        }
        if (normalized.contains("contract") || normalized.contains("agreement") || normalized.contains("договор")) {
            return DocumentType.CONTRACT;
        }
        if (normalized.contains("report") || normalized.contains("отчет") || normalized.contains("отчёт")) {
            return DocumentType.REPORT;
        }
        if (normalized.contains("procedure") || normalized.contains("процедур") || normalized.contains("порядок")) {
            return DocumentType.PROCEDURE;
        }
        if (normalized.contains("manual") || normalized.contains("guide") || normalized.contains("руководств")) {
            return DocumentType.MANUAL;
        }
        if (normalized.contains("letter") || normalized.contains("letterhead") || normalized.contains("письмо")) {
            return DocumentType.LETTER;
        }
        if (normalized.contains(".ppt") || normalized.contains(".pptx")
            || normalized.contains("presentation") || normalized.contains("презентац")) {
            return DocumentType.PRESENTATION;
        }
        if (normalized.contains(".xls") || normalized.contains(".xlsx") || normalized.contains(".csv")
            || normalized.contains("spreadsheet") || normalized.contains("table") || normalized.contains("таблиц")) {
            return DocumentType.SPREADSHEET;
        }
        return null;
    }

    private String normalizeFileStem(String originalFileName) {
        if (!StringUtils.hasText(originalFileName)) {
            return null;
        }
        String trimmed = originalFileName.trim();
        int extensionSeparator = trimmed.lastIndexOf('.');
        return extensionSeparator > 0 ? trimmed.substring(0, extensionSeparator) : trimmed;
    }

    private String joinSearchText(String... values) {
        List<String> parts = new ArrayList<>();
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                parts.add(value);
            }
        }
        return String.join(" ", parts);
    }
}
