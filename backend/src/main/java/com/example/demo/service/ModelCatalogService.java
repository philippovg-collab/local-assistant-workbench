package com.example.demo.service;

import com.example.demo.config.EmbeddingProperties;
import com.example.demo.llmprovider.ActiveLlmProviderResolver;
import com.example.demo.llm.LlmClient;
import com.example.demo.model.OllamaModelInfo;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ModelCatalogService {

    private final LlmClient llmClient;
    private final EmbeddingProperties embeddingProperties;
    private final ActiveLlmProviderResolver activeProviderResolver;

    @Autowired
    public ModelCatalogService(
        LlmClient llmClient,
        EmbeddingProperties embeddingProperties,
        ActiveLlmProviderResolver activeProviderResolver
    ) {
        this.llmClient = llmClient;
        this.embeddingProperties = embeddingProperties;
        this.activeProviderResolver = activeProviderResolver;
    }

    public ModelCatalogService(LlmClient llmClient) {
        this(llmClient, null, null);
    }

    public ModelCatalogService(LlmClient llmClient, EmbeddingProperties embeddingProperties) {
        this(llmClient, embeddingProperties, null);
    }

    public List<OllamaModelInfo> listModels() {
        return llmClient.listModels().stream()
            .filter(model -> !isEmbeddingModel(model))
            .toList();
    }

    private boolean isEmbeddingModel(OllamaModelInfo model) {
        if (model == null
            || !StringUtils.hasText(model.name())
            || (embeddingProperties == null && activeProviderResolver == null)) {
            return false;
        }
        String embeddingModel = activeProviderResolver == null
            ? embeddingProperties.getModel()
            : activeProviderResolver.activeEmbeddingModel();
        if (!StringUtils.hasText(embeddingModel)) {
            return false;
        }
        return normalizeModelName(model.name()).equals(normalizeModelName(embeddingModel));
    }

    private String normalizeModelName(String modelName) {
        String normalized = modelName.trim();
        if (normalized.endsWith(":latest")) {
            return normalized.substring(0, normalized.length() - ":latest".length());
        }
        return normalized;
    }
}
