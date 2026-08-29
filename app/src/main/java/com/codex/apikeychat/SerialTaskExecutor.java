package com.codex.apikeychat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class SerialTaskExecutor {
    private final ExecutorService executor;

    SerialTaskExecutor(String threadName) {
        executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, threadName);
            thread.setDaemon(true);
            return thread;
        });
    }

    void execute(Runnable task) {
        executor.execute(task);
    }

    void shutdown() {
        executor.shutdownNow();
    }
}
