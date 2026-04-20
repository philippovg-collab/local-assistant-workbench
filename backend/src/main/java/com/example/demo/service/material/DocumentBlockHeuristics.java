package com.example.demo.service.material;

import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

public final class DocumentBlockHeuristics {

    private static final int SHORT_TITLE_LIMIT = 120;
    private static final Pattern TITLE_PATTERN = Pattern.compile(
        "^(?:\\d+(?:\\.\\d+)*[.)]?\\s+)?[\\p{Lu}0-9][^\\n]{0,120}$"
    );
    private static final Pattern LIST_PATTERN = Pattern.compile(
        "^(?:[-*•▪‣◦]|\\d+[.)]|[a-zа-яё][.)])\\s+.+$",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern TABLE_ROW_PATTERN = Pattern.compile(
        "^.+(?:\\t|\\s{2,}|\\|).+$"
    );
    private static final Pattern QA_PATTERN = Pattern.compile(
        "^(?:q|a|question|answer|вопрос|ответ)\\s*[:\\-].+$",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern APPENDIX_PATTERN = Pattern.compile(
        "^(?:appendix|annex|attachment|приложение)\\b.*$",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern CAPTION_PATTERN = Pattern.compile(
        "^(?:figure|fig\\.|caption|table\\s+\\d+|рис\\.|рисунок|таблица)\\b.*$",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private DocumentBlockHeuristics() {
    }

    public static String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r\n", "\n").trim();
    }

    public static DocumentBlockConfidence confidenceFor(boolean ocrUsed) {
        return ocrUsed ? DocumentBlockConfidence.LOW : DocumentBlockConfidence.HIGH;
    }

    public static DocumentBlockType classify(String text, DocumentBlockType fallbackType) {
        String normalized = normalizeText(text);
        if (!StringUtils.hasText(normalized)) {
            return fallbackType == null ? DocumentBlockType.NARRATIVE : fallbackType;
        }
        if (looksLikeAppendix(normalized)) {
            return DocumentBlockType.APPENDIX;
        }
        if (looksLikeQa(normalized)) {
            return DocumentBlockType.QA;
        }
        if (looksLikeCaption(normalized)) {
            return DocumentBlockType.CAPTION;
        }
        if (looksLikeTitle(normalized)) {
            return DocumentBlockType.TITLE;
        }
        if (looksLikeTable(normalized)) {
            return DocumentBlockType.TABLE;
        }
        if (looksLikeList(normalized)) {
            return DocumentBlockType.LIST;
        }
        return fallbackType == null ? DocumentBlockType.NARRATIVE : fallbackType;
    }

    public static boolean looksLikeTitle(String text) {
        String normalized = normalizeText(text);
        if (!StringUtils.hasText(normalized) || normalized.contains("\n") || normalized.length() > SHORT_TITLE_LIMIT) {
            return false;
        }
        if (TABLE_ROW_PATTERN.matcher(normalized).matches()) {
            return false;
        }
        if (normalized.endsWith(".") || normalized.endsWith("!") || normalized.endsWith("?") || normalized.endsWith(";")) {
            return false;
        }
        return TITLE_PATTERN.matcher(normalized).matches();
    }

    public static boolean looksLikeList(String text) {
        String normalized = normalizeText(text);
        if (!StringUtils.hasText(normalized)) {
            return false;
        }
        String[] lines = normalized.split("\\n");
        return lines.length > 1
            ? java.util.Arrays.stream(lines).allMatch(DocumentBlockHeuristics::looksLikeListLine)
            : looksLikeListLine(normalized);
    }

    static boolean looksLikeListLine(String line) {
        return LIST_PATTERN.matcher(normalizeText(line)).matches();
    }

    static boolean looksLikeTable(String text) {
        String normalized = normalizeText(text);
        if (!StringUtils.hasText(normalized)) {
            return false;
        }
        String[] lines = normalized.split("\\n");
        if (lines.length > 1) {
            long matchingRows = java.util.Arrays.stream(lines)
                .filter(line -> TABLE_ROW_PATTERN.matcher(normalizeText(line)).matches())
                .count();
            return matchingRows >= Math.max(2, lines.length - 1);
        }
        return TABLE_ROW_PATTERN.matcher(normalized).matches();
    }

    static boolean looksLikeQa(String text) {
        return QA_PATTERN.matcher(normalizeText(text)).matches();
    }

    static boolean looksLikeAppendix(String text) {
        return APPENDIX_PATTERN.matcher(normalizeText(text)).matches();
    }

    public static boolean looksLikeCaption(String text) {
        return CAPTION_PATTERN.matcher(normalizeText(text)).matches();
    }

    public static Integer levelFor(DocumentBlockType type, String text) {
        if (type == DocumentBlockType.TITLE) {
            String normalized = normalizeText(text);
            if (!StringUtils.hasText(normalized)) {
                return 1;
            }
            int dots = 0;
            for (int index = 0; index < normalized.length(); index++) {
                if (normalized.charAt(index) == '.') {
                    dots++;
                } else if (!Character.isDigit(normalized.charAt(index))) {
                    break;
                }
            }
            return Math.max(1, dots + 1);
        }
        if (type == DocumentBlockType.LIST) {
            return 1;
        }
        return null;
    }

    static boolean containsMostlyCyrillic(String text) {
        String normalized = normalizeText(text).toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(normalized)) {
            return false;
        }
        long cyrillic = normalized.chars()
            .filter(ch -> Character.UnicodeBlock.of(ch) == Character.UnicodeBlock.CYRILLIC)
            .count();
        return cyrillic * 2 >= normalized.length();
    }
}
