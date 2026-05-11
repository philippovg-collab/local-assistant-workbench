package com.example.demo.service.eval;

public class EvalOutputFormatException extends RuntimeException {

    public EvalOutputFormatException(String message) {
        super(message);
    }

    public EvalOutputFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
