package com.example.demo.support;

import com.example.demo.embedding.EmbeddingClient;
import java.util.concurrent.Executor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class IntegrationTestOverrides {

    @Bean
    @Primary
    EmbeddingClient embeddingClient() {
        return new DeterministicEmbeddingClient();
    }

    @Bean(name = "materialIndexingExecutor")
    @Primary
    Executor materialIndexingExecutor() {
        return Runnable::run;
    }
}
