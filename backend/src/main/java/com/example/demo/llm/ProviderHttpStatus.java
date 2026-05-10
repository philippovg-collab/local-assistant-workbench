package com.example.demo.llm;

import com.example.demo.error.CodedException;

public final class ProviderHttpStatus {

    private ProviderHttpStatus() {
    }

    public static boolean hasHttpStatus(CodedException exception, String code, int... statuses) {
        if (exception == null || !code.equals(exception.getCode()) || exception.getMessage() == null) {
            return false;
        }
        String message = exception.getMessage();
        for (int status : statuses) {
            if (message.contains("HTTP " + status)) {
                return true;
            }
        }
        return false;
    }
}
