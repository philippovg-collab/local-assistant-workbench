package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.demo.config.LlmProperties;
import com.example.demo.llmprovider.ActiveLlmProviderResolver;
import com.example.demo.model.ChatExecutionRequest;
import com.example.demo.model.ChatMode;
import java.util.List;
import org.junit.jupiter.api.Test;

class PromptPolicyResolverTest {

    @Test
    void keepsDeepSeekRequestModelOverride() {
        PromptPolicyResolver resolver = new PromptPolicyResolver(new LlmProperties());

        PromptPolicyResolver.ResolvedPromptPolicy policy = resolver.resolve(
            new ChatExecutionRequest(
                ChatMode.DIRECT,
                "deepseek-r1:8b",
                "Проверь выбранную модель",
                null,
                List.of()
            ),
            List.of(),
            null
        );

        assertEquals("deepseek-r1:8b", policy.model());
    }

    @Test
    void usesActiveChatProviderDefaultWhenRequestModelIsEmpty() {
        LlmProperties llmProperties = new LlmProperties();
        llmProperties.setModel("stale-env-model");
        ActiveLlmProviderResolver activeProviderResolver = mock(ActiveLlmProviderResolver.class);
        when(activeProviderResolver.defaultChatModel()).thenReturn("active-chat-model");
        PromptPolicyResolver resolver = new PromptPolicyResolver(llmProperties, activeProviderResolver);

        PromptPolicyResolver.ResolvedPromptPolicy policy = resolver.resolve(
            new ChatExecutionRequest(
                ChatMode.DIRECT,
                null,
                "Проверь active provider",
                null,
                List.of()
            ),
            List.of(),
            null
        );

        assertEquals("active-chat-model", policy.model());
    }
}
