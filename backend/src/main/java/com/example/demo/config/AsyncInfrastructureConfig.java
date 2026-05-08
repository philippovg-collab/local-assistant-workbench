package com.example.demo.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AsyncInfrastructureConfig {

    @Bean(name = "materialIndexingExecutor", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "materialIndexingExecutor")
    Executor materialIndexingExecutor() {
        return boundedExecutor("material-indexing", 2, 64);
    }

    @Bean(name = "materialAutoTaggingExecutor", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "materialAutoTaggingExecutor")
    Executor materialAutoTaggingExecutor() {
        return boundedExecutor("material-auto-tags", 2, 64);
    }

    @Bean(name = "searchSyncExecutor", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "searchSyncExecutor")
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

    @Bean(name = "chatLeaseHeartbeatExecutor", destroyMethod = "shutdown")
    ScheduledExecutorService chatLeaseHeartbeatExecutor() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(
            1,
            namedThreadFactory("chat-lease-heartbeat")
        );
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }

    private ExecutorService boundedExecutor(String threadPrefix, int threads, int queueCapacity) {
        return new ThreadPoolExecutor(
            threads,
            threads,
            0L,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(queueCapacity),
            namedThreadFactory(threadPrefix),
            new ThreadPoolExecutor.AbortPolicy()
        );
    }

    private java.util.concurrent.ThreadFactory namedThreadFactory(String threadPrefix) {
        AtomicInteger threadCounter = new AtomicInteger(0);
        return runnable -> new Thread(runnable, threadPrefix + "-" + threadCounter.incrementAndGet());
    }
}
