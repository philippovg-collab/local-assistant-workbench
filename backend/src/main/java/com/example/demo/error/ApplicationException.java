package com.example.demo.error;

public class ApplicationException extends CodedException {

    public ApplicationException(ErrorType type, String code, String message) {
        super(type, code, message);
    }

    public ApplicationException(ErrorType type, String code, String message, Throwable cause) {
        super(type, code, message, cause);
    }
}
