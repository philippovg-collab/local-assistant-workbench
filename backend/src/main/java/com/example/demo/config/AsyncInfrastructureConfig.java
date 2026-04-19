package com.example.demo.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AsyncInfrastructureConfig {

    @Bean(name = "materialIndexingExecutor", destroyMethod = "shutdown")
    Executor materialIndexingExecutor() {
        return boundedExecutor("material-indexing", 2, 64);
    }

    @Bean(name = "searchSyncExecutor", destroyMethod = "shutdown")
    Executor searchSyncExecutor() {
        return boundedExecutor("search-sync", 2, 64);
    }

    @Bean(name = "chatExecutionExecutor", destroyMethod = "shutdown")
    ExecutorService chatExecutionExecutor(ChatExecutionProperties properties) {
        return boundedExecutor(
            "chat-execution",
            Math.max(1, properties.getThreads()),
            Math.max(1, properties.getQueueCapacity())
        );
    }

    private ExecutorService boundedExecutor(String threadPrefix, int threads, int queueCapacity) {
        AtomicInteger threadCounter = new AtomicInteger(0);
        return new ThreadPoolExecutor(
            threads,
            threads,
            0L,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(queueCapacity),
            runnable -> new Thread(runnable, threadPrefix + "-" + threadCounter.incrementAndGet()),
            new ThreadPoolExecutor.AbortPolicy()
        );
    }
}
