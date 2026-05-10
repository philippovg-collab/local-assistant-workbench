package com.example.demo.error;

public abstract class CodedException extends RuntimeException {

    private final ErrorType type;
    private final String code;

    protected CodedException(ErrorType type, String code, String message) {
        super(message);
        this.type = type;
        this.code = code;
    }

    protected CodedException(ErrorType type, String code, String message, Throwable cause) {
        super(message, cause);
        this.type = type;
        this.code = code;
    }

    public ErrorType getType() {
        return type;
    }

    public String getCode() {
        return code;
    }
}
