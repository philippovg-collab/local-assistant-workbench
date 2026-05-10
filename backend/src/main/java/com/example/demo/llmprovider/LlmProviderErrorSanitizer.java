package com.example.demo.llmprovider;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class LlmProviderErrorSanitizer {

    private static final int MAX_MESSAGE_LENGTH = 500;
    private static final String REDACTED = "[REDACTED]";
    private static final List<Replacement> REPLACEMENTS = List.of(
        new Replacement(
            Pattern.compile("(?i)(Authorization\\s*:\\s*Bearer\\s+)[^\\s,;]+"),
            "$1" + REDACTED
        ),
        new Replacement(
            Pattern.compile("(?i)(Authorization\\s*:\\s*Basic\\s+)[A-Za-z0-9+/=._~-]+"),
            "$1" + REDACTED
        ),
        new Replacement(
            Pattern.compile("(?i)\\b(Bearer\\s+)[A-Za-z0-9._~+\\-/]+=*"),
            "$1" + REDACTED
        ),
        new Replacement(
            Pattern.compile("(?i)\\b(api[_-]?key|apikey|x-api-key|access[_-]?token|refresh[_-]?token|secret|token)(\\s*[=:]\\s*)([^\\s,;&\"']+)"),
            "$1$2" + REDACTED
        ),
        new Replacement(
            Pattern.compile("(?i)(\"(?:api[_-]?key|apikey|x-api-key|access[_-]?token|refresh[_-]?token|secret|token|authorization)\"\\s*:\\s*\")[^\"]*(\")"),
            "$1" + REDACTED + "$2"
        )
    );

    public String sanitize(String message) {
        if (message == null) {
            return null;
        }
        String sanitized = message;
        for (Replacement replacement : REPLACEMENTS) {
            sanitized = replacement.pattern().matcher(sanitized).replaceAll(replacement.replacement());
        }
        if (sanitized.length() <= MAX_MESSAGE_LENGTH) {
            return sanitized;
        }
        return sanitized.substring(0, MAX_MESSAGE_LENGTH) + "...";
    }

    public String rootMessage(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
        return sanitize(message);
    }

    private record Replacement(Pattern pattern, String replacement) {
    }
}
