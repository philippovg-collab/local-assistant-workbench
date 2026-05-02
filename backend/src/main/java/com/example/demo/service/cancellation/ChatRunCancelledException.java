package com.example.demo.service.cancellation;

public class ChatRunCancelledException extends RuntimeException {

    public ChatRunCancelledException() {
        super("Chat run was cancelled.");
    }
}
