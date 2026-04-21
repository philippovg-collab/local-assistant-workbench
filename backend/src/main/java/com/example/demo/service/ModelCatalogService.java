package com.example.demo.service;

import com.example.demo.config.EmbeddingProperties;
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

    @Autowired
    public ModelCatalogService(LlmClient llmClient, EmbeddingProperties embeddingProperties) {
        this.llmClient = llmClient;
        this.embeddingProperties = embeddingProperties;
    }

    public ModelCatalogService(LlmClient llmClient) {
        this(llmClient, null);
    }

    public List<OllamaModelInfo> listModels() {
        return llmClient.listModels().stream()
            .filter(model -> !isEmbeddingModel(model))
            .toList();
    }

    private boolean isEmbeddingModel(OllamaModelInfo model) {
        if (model == null || !StringUtils.hasText(model.name()) || embeddingProperties == null) {
            return false;
        }
        String embeddingModel = embeddingProperties.getModel();
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
