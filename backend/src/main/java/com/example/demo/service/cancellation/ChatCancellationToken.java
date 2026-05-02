package com.example.demo.service.cancellation;

public interface ChatCancellationToken {

    ChatCancellationToken NONE = new ChatCancellationToken() {
        @Override
        public boolean isCancellationRequested() {
            return false;
        }

        @Override
        public boolean canBeCancelled() {
            return false;
        }

        @Override
        public Registration onCancellationRequested(Runnable callback) {
            return () -> {
            };
        }
    };

    static ChatCancellationToken none() {
        return NONE;
    }

    boolean isCancellationRequested();

    default boolean canBeCancelled() {
        return true;
    }

    default void throwIfCancellationRequested() {
        if (isCancellationRequested()) {
            throw new ChatRunCancelledException();
        }
    }

    default Registration onCancellationRequested(Runnable callback) {
        return () -> {
        };
    }

    @FunctionalInterface
    interface Registration extends AutoCloseable {
        @Override
        void close();
    }
}
