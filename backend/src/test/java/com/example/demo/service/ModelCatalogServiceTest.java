package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.demo.config.EmbeddingProperties;
import com.example.demo.llm.LlmClient;
import com.example.demo.model.OllamaModelInfo;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModelCatalogServiceTest {

    @Test
    void hidesConfiguredEmbeddingModelFromChatCatalog() {
        EmbeddingProperties embeddingProperties = new EmbeddingProperties();
        embeddingProperties.setModel("nomic-embed-text");
        ModelCatalogService service = new ModelCatalogService(
            new StaticLlmClient(List.of(
                new OllamaModelInfo("qwen2.5:7b"),
                new OllamaModelInfo("deepseek-r1:8b"),
                new OllamaModelInfo("nomic-embed-text:latest")
            )),
            embeddingProperties
        );

        List<OllamaModelInfo> models = service.listModels();

        assertEquals(2, models.size());
        assertEquals("qwen2.5:7b", models.get(0).name());
        assertEquals("deepseek-r1:8b", models.get(1).name());
    }

    @Test
    void treatsLatestTagAsEquivalentWhenFilteringEmbeddingModel() {
        EmbeddingProperties embeddingProperties = new EmbeddingProperties();
        embeddingProperties.setModel("nomic-embed-text:latest");
        ModelCatalogService service = new ModelCatalogService(
            new StaticLlmClient(List.of(
                new OllamaModelInfo("qwen2.5:7b"),
                new OllamaModelInfo("nomic-embed-text")
            )),
            embeddingProperties
        );

        List<OllamaModelInfo> models = service.listModels();

        assertEquals(1, models.size());
        assertEquals("qwen2.5:7b", models.get(0).name());
    }

    private record StaticLlmClient(List<OllamaModelInfo> models) implements LlmClient {

        @Override
        public List<OllamaModelInfo> listModels() {
            return models;
        }

        @Override
        public ChatResult chat(ChatRequest request) {
            throw new UnsupportedOperationException("chat is not used in this test");
        }
    }
}
