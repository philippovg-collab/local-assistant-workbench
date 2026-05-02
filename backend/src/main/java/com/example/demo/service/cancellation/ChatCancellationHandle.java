package com.example.demo.service.cancellation;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChatCancellationHandle implements ChatCancellationToken {

    private final AtomicBoolean cancellationRequested = new AtomicBoolean(false);
    private final List<Runnable> callbacks = new CopyOnWriteArrayList<>();

    public void requestCancellation() {
        if (!cancellationRequested.compareAndSet(false, true)) {
            return;
        }
        for (Runnable callback : callbacks) {
            runQuietly(callback);
        }
    }

    @Override
    public boolean isCancellationRequested() {
        return cancellationRequested.get();
    }

    @Override
    public Registration onCancellationRequested(Runnable callback) {
        if (callback == null) {
            return () -> {
            };
        }
        if (isCancellationRequested()) {
            runQuietly(callback);
            return () -> {
            };
        }
        callbacks.add(callback);
        if (isCancellationRequested() && callbacks.remove(callback)) {
            runQuietly(callback);
            return () -> {
            };
        }
        return () -> callbacks.remove(callback);
    }

    private void runQuietly(Runnable callback) {
        try {
            callback.run();
        } catch (RuntimeException ignored) {
            // Cancellation callbacks are best-effort cleanup hooks.
        }
    }
}
