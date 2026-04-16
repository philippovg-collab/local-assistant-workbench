package com.example.demo.service;

import com.example.demo.config.LlmProperties;
import com.example.demo.model.AppliedInstruction;
import com.example.demo.model.ChatExecutionRequest;
import com.example.demo.model.ChatMode;
import com.example.demo.model.InstructionSummary;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PromptPolicyResolver {

    private final LlmProperties properties;

    public PromptPolicyResolver(LlmProperties properties) {
        this.properties = properties;
    }

    /**
     * Prompt policy precedence:
     * 1) request.model overrides the default configured model
     * 2) request.systemPrompt overrides the default configured system prompt
     * 3) selected instruction snippets are appended in request order
     * 4) RAG mode always appends grounding rules that prohibit hallucinating beyond retrieved context
     */
    public ResolvedPromptPolicy resolve(
        ChatExecutionRequest request,
        List<InstructionSummary> instructions
    ) {
        String model = StringUtils.hasText(request.model())
            ? request.model().trim()
            : properties.getModel();

        String baseSystemPrompt = StringUtils.hasText(request.systemPrompt())
            ? request.systemPrompt().trim()
            : properties.getSystemPrompt();

        StringBuilder systemPrompt = new StringBuilder();
        if (StringUtils.hasText(baseSystemPrompt)) {
            systemPrompt.append(baseSystemPrompt.trim());
        }

        if (!instructions.isEmpty()) {
            if (systemPrompt.length() > 0) {
                systemPrompt.append("\n\n");
            }

            systemPrompt.append("Reusable instruction snippets (apply in this exact order):\n");
            for (int index = 0; index < instructions.size(); index++) {
                InstructionSummary instruction = instructions.get(index);
                systemPrompt.append(index + 1)
                    .append(". ")
                    .append(instruction.title())
                    .append(" [")
                    .append(instruction.category())
                    .append("]\n")
                    .append(instruction.content())
                    .append("\n\n");
            }
        }

        if (request.mode() == ChatMode.RAG) {
            if (systemPrompt.length() > 0) {
                systemPrompt.append('\n');
            }

            systemPrompt.append("""
                Grounding rules:
                - Answer only from the retrieved context.
                - If the context is incomplete, say so explicitly.
                - Do not invent facts that are not present in the context.
                """.strip());
        }

        List<AppliedInstruction> appliedInstructions = instructions.stream()
            .map(instruction -> new AppliedInstruction(
                instruction.id(),
                instruction.title(),
                instruction.category()
            ))
            .toList();

        return new ResolvedPromptPolicy(model, systemPrompt.toString().trim(), appliedInstructions);
    }

    public record ResolvedPromptPolicy(
        String model,
        String systemPrompt,
        List<AppliedInstruction> appliedInstructions
    ) {
    }
}
