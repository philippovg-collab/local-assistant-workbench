package com.example.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.demo.config.SearchSyncProperties;
import com.example.demo.infrastructure.material.MaterialSearchSyncQueueRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class ElasticsearchHealthServiceContextTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(TestConfig.class)
        .withBean(SearchSyncProperties.class, this::disabledProperties)
        .withBean(MaterialSearchSyncQueueRepository.class, this::queueRepository);

    @Test
    void loadsServiceThroughSpringConstructorInjection() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ElasticsearchHealthService.class);
            assertThat(context.getBean(ElasticsearchHealthService.class).currentHealth().clusterStatus())
                .isEqualTo("DISABLED");
        });
    }

    private SearchSyncProperties disabledProperties() {
        SearchSyncProperties properties = new SearchSyncProperties();
        properties.setEnabled(false);
        return properties;
    }

    private MaterialSearchSyncQueueRepository queueRepository() {
        MaterialSearchSyncQueueRepository repository = org.mockito.Mockito.mock(MaterialSearchSyncQueueRepository.class);
        when(repository.getSearchSyncQueueSnapshot()).thenReturn(
            new MaterialSearchSyncQueueRepository.SearchSyncQueueSnapshot(
                0,
                0,
                0,
                null,
                Instant.parse("2026-04-17T10:00:00Z")
            )
        );
        return repository;
    }

    @Configuration(proxyBeanMethods = false)
    @Import(ElasticsearchHealthService.class)
    static class TestConfig {
    }
}
