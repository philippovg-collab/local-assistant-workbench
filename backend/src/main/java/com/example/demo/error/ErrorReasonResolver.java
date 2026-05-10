package com.example.demo.error;

public final class ErrorReasonResolver {

    private ErrorReasonResolver() {
    }

    public static String reasonCode(Throwable throwable, String fallback) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof CodedException codedException) {
                return codedException.getCode();
            }
            current = current.getCause();
        }
        return fallback;
    }
}
