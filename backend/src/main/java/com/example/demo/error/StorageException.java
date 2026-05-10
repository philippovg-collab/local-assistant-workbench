package com.example.demo.error;

public class StorageException extends CodedException {

    public StorageException(ErrorType type, String code, String message) {
        super(type, code, message);
    }

    public StorageException(ErrorType type, String code, String message, Throwable cause) {
        super(type, code, message, cause);
    }
}
