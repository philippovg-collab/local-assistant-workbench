package com.example.demo.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AsyncInfrastructureConfig {

    @Bean(name = "materialIndexingExecutor", destroyMethod = "shutdown")
    Executor materialIndexingExecutor() {
        ExecutorService executorService = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "material-indexing");
            return thread;
        });
        return executorService;
    }

    @Bean(name = "searchSyncExecutor", destroyMethod = "shutdown")
    Executor searchSyncExecutor() {
        ExecutorService executorService = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "search-sync");
            return thread;
        });
        return executorService;
    }
}
