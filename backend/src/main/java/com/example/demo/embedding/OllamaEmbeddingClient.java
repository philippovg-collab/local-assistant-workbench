package com.example.demo.embedding;

import com.example.demo.config.EmbeddingProperties;
import com.example.demo.config.LlmProperties;
import com.example.demo.llm.OllamaApiTransport;
import com.example.demo.llmprovider.ActiveLlmProvider;

public class OllamaEmbeddingClient extends OpenAiCompatibleEmbeddingClient {

    public OllamaEmbeddingClient(
        OllamaApiTransport transport,
        EmbeddingProperties embeddingProperties,
        LlmProperties llmProperties
    ) {
        super(transport, () -> ActiveLlmProvider.fromEmbeddingProperties(embeddingProperties, llmProperties));
    }
}
