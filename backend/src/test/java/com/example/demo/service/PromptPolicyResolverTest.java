package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.demo.config.LlmProperties;
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
}
