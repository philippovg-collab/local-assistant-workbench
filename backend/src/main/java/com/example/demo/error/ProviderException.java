package com.example.demo.error;

public class ProviderException extends CodedException {

    public ProviderException(ErrorType type, String code, String message) {
        super(type, code, message);
    }

    public ProviderException(ErrorType type, String code, String message, Throwable cause) {
        super(type, code, message, cause);
    }
}
