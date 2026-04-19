package com.example.demo.support;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.example.demo.config.RagProperties;
import com.example.demo.config.SearchSyncProperties;
import com.example.demo.infrastructure.material.LexicalSearchProvider;
import com.example.demo.infrastructure.material.MaterialSearchSyncQueueRepository;
import com.example.demo.service.ElasticsearchHealthService;
import com.example.demo.service.LexicalSearchModeResolver;
import com.example.demo.service.LexicalSearchStrategy;
import com.example.demo.service.ProductionLexicalSearchRouter;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;

public final class TestLexicalRoutingSupport {

    private TestLexicalRoutingSupport() {
    }

    public static ProductionLexicalSearchRouter productionRouter(
        MaterialSearchSyncQueueRepository queueRepository,
        RagProperties ragProperties,
        List<LexicalSearchProvider> providers
    ) {
        return new ProductionLexicalSearchRouter(
            new LexicalSearchStrategy(providers, ragProperties),
            new LexicalSearchModeResolver(ragProperties),
            new ElasticsearchHealthService(
                new SearchSyncProperties(),
                queueRepository,
                emptyElasticsearchClientProvider()
            )
        );
    }

    private static ObjectProvider<ElasticsearchClient> emptyElasticsearchClientProvider() {
        return new ObjectProvider<>() {
            @Override
            public ElasticsearchClient getObject(Object... args) {
                return null;
            }

            @Override
            public ElasticsearchClient getIfAvailable() {
                return null;
            }

            @Override
            public ElasticsearchClient getIfUnique() {
                return null;
            }

            @Override
            public ElasticsearchClient getObject() {
                return null;
            }

            @Override
            public java.util.Iterator<ElasticsearchClient> iterator() {
                return List.<ElasticsearchClient>of().iterator();
            }
        };
    }
}
