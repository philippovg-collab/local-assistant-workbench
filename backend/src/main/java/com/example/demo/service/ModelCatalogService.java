package com.example.demo.service;

import com.example.demo.llm.LlmClient;
import com.example.demo.model.OllamaModelInfo;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ModelCatalogService {

    private final LlmClient llmClient;

    public ModelCatalogService(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public List<OllamaModelInfo> listModels() {
        return llmClient.listModels();
    }
}
