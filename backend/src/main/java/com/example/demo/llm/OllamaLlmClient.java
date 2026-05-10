package com.example.demo.llm;

import com.example.demo.config.LlmProperties;
import com.example.demo.llmprovider.ActiveLlmProvider;

public class OllamaLlmClient extends OpenAiCompatibleLlmClient {

    public OllamaLlmClient(OllamaApiTransport transport, LlmProperties properties) {
        super(transport, () -> ActiveLlmProvider.fromLlmProperties(properties));
    }
}
