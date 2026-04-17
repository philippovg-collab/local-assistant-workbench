package com.example.demo.service;

import com.example.demo.config.LlmProperties;
import com.example.demo.model.AppliedInstruction;
import com.example.demo.model.ChatExecutionRequest;
import com.example.demo.model.ChatMode;
import com.example.demo.model.InstructionCategory;
import com.example.demo.model.InstructionDetail;
import java.util.List;
import java.util.function.Predicate;
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
        List<InstructionDetail> instructions
    ) {
        String model = StringUtils.hasText(request.model())
            ? request.model().trim()
            : properties.getModel();

        String baseSystemPrompt = StringUtils.hasText(request.systemPrompt())
            ? request.systemPrompt().trim()
            : properties.getSystemPrompt();

        String systemInstructions = renderInstructionBlock(
            "System instructions",
            instructions,
            instruction -> instruction.category() == InstructionCategory.SYSTEM
        );
        String safetyInstructions = renderInstructionBlock(
            "Safety restrictions",
            instructions,
            instruction -> instruction.category() == InstructionCategory.SAFETY
        );
        String contextInstructions = renderInstructionBlock(
            "Context instructions",
            instructions,
            instruction -> instruction.category() == InstructionCategory.CONTEXT
        );
        String userInstructions = renderInstructionBlock(
            "User instructions",
            instructions,
            instruction -> instruction.category() == InstructionCategory.USER
        );

        String systemPrompt = joinBlocks(baseSystemPrompt, systemInstructions, safetyInstructions);

        if (request.mode() == ChatMode.RAG) {
            systemPrompt = joinBlocks(systemPrompt, """
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

        return new ResolvedPromptPolicy(
            model,
            systemPrompt,
            contextInstructions,
            userInstructions,
            appliedInstructions
        );
    }

    private String renderInstructionBlock(
        String heading,
        List<InstructionDetail> instructions,
        Predicate<InstructionDetail> filter
    ) {
        List<InstructionDetail> matchingInstructions = instructions.stream()
            .filter(filter)
            .toList();
        if (matchingInstructions.isEmpty()) {
            return "";
        }

        StringBuilder block = new StringBuilder(heading).append(":\n");
        for (int index = 0; index < matchingInstructions.size(); index++) {
            InstructionDetail instruction = matchingInstructions.get(index);
            block.append(index + 1)
                .append(". ")
                .append(instruction.title())
                .append('\n')
                .append(instruction.content().trim())
                .append("\n\n");
        }

        return block.toString().trim();
    }

    private String joinBlocks(String... blocks) {
        return java.util.Arrays.stream(blocks)
            .filter(StringUtils::hasText)
            .map(String::trim)
            .reduce((left, right) -> left + "\n\n" + right)
            .orElse("");
    }

    public record ResolvedPromptPolicy(
        String model,
        String systemPrompt,
        String contextInstructions,
        String userInstructions,
        List<AppliedInstruction> appliedInstructions
    ) {
    }
}
