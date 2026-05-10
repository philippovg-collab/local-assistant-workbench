package com.example.demo.service;

import com.example.demo.config.LlmProperties;
import com.example.demo.llmprovider.ActiveLlmProviderResolver;
import com.example.demo.model.AppliedInstruction;
import com.example.demo.model.AnswerMode;
import com.example.demo.model.ChatRunMessage;
import com.example.demo.model.ChatExecutionRequest;
import com.example.demo.model.ChatMode;
import com.example.demo.model.InstructionCategory;
import com.example.demo.model.InstructionDetail;
import com.example.demo.model.PromptPolicySnapshot;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PromptPolicyResolver {

    private final LlmProperties properties;
    private final ActiveLlmProviderResolver activeProviderResolver;

    @Autowired
    public PromptPolicyResolver(LlmProperties properties, ActiveLlmProviderResolver activeProviderResolver) {
        this.properties = properties;
        this.activeProviderResolver = activeProviderResolver;
    }

    public PromptPolicyResolver(LlmProperties properties) {
        this(properties, null);
    }

    /**
     * Prompt policy precedence:
     * 1) request.model overrides the default configured model
     * 2) resolved runtime instructions are appended in scope-aware order
     * 3) request.systemPrompt is treated as a legacy alias for a temporary request instruction
     * 4) answerMode adds response-shaping rules
     * 5) RAG mode always appends grounding rules that prohibit hallucinating beyond retrieved context
     */
    public ResolvedPromptPolicy resolve(
        ChatExecutionRequest request,
        List<InstructionDetail> instructions,
        String temporaryInstruction
    ) {
        String model = StringUtils.hasText(request.model())
            ? request.model().trim()
            : defaultModel();

        String baseSystemPrompt = properties.getSystemPrompt();
        AnswerMode answerMode = request.answerMode() == null ? AnswerMode.BRIEF : request.answerMode();

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

        String temporaryInstructionBlock = StringUtils.hasText(temporaryInstruction)
            ? "Temporary request instruction:\n" + temporaryInstruction.trim()
            : "";
        String answerModeBlock = answerModeBlock(answerMode, request.mode());
        String systemPrompt = joinBlocks(
            baseSystemPrompt,
            systemInstructions,
            safetyInstructions,
            temporaryInstructionBlock,
            answerModeBlock
        );

        String groundingBlock = "";
        if (request.mode() == ChatMode.RAG) {
            groundingBlock = """
                Grounding rules:
                - Answer only from the retrieved context.
                - If the context is incomplete, say so explicitly.
                - Do not invent facts that are not present in the context.
                """.strip();
            systemPrompt = joinBlocks(systemPrompt, groundingBlock);
        }

        List<AppliedInstruction> appliedInstructions = instructions.stream()
            .map(instruction -> new AppliedInstruction(
                instruction.id(),
                instruction.title(),
                instruction.category(),
                instruction.scopeLevel(),
                instruction.scopeTargetId(),
                instruction.revision()
            ))
            .toList();

        return new ResolvedPromptPolicy(
            model,
            systemPrompt,
            contextInstructions,
            userInstructions,
            appliedInstructions,
            answerMode,
            new PromptPolicySnapshot(
                baseSystemPrompt,
                systemInstructions,
                safetyInstructions,
                contextInstructions,
                userInstructions,
                temporaryInstructionBlock,
                answerModeBlock,
                groundingBlock,
                systemPrompt,
                List.of(),
                sha256(systemPrompt),
                List.of(),
                null,
                StringUtils.hasText(groundingBlock)
            )
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

    private String answerModeBlock(AnswerMode answerMode, ChatMode chatMode) {
        if (answerMode == null) {
            return "";
        }

        return switch (answerMode) {
            case BRIEF -> "Answer mode: brief.\n- Keep the answer concise and high-signal.";
            case WITH_QUOTES -> """
                Answer mode: with quotes.
                - Include short supporting quotes when sources are available.
                - Keep quotes brief and clearly attributable.
                """.strip();
            case DOCUMENTS_ONLY -> """
                Answer mode: documents only.
                - Do not expand beyond the available documents.
                - If the documents are insufficient, say so explicitly.
                """.strip();
            case BROADER_REASONING -> """
                Answer mode: broader reasoning.
                - Ground the answer in the documents first.
                - If you go broader, label that part as broader reasoning.
                """.strip();
            case STRICT_SOURCES_ONLY -> chatMode == ChatMode.RAG
                ? """
                    Answer mode: strict sources only.
                    - If the answer is not fully supported by the retrieved sources, respond with: Не найдено в источниках.
                    - Do not speculate or fill gaps.
                    """.strip()
                : """
                    Answer mode: strict sources only.
                    - Explain that direct mode has no retrieved sources.
                    - Do not pretend that external documents were used.
                    """.strip();
        };
    }

    private String defaultModel() {
        return activeProviderResolver == null
            ? properties.getModel()
            : activeProviderResolver.defaultChatModel();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is not available", exception);
        }
    }

    public record ResolvedPromptPolicy(
        String model,
        String systemPrompt,
        String contextInstructions,
        String userInstructions,
        List<AppliedInstruction> appliedInstructions,
        AnswerMode answerMode,
        PromptPolicySnapshot snapshot
    ) {
        public ResolvedPromptPolicy(
            String model,
            String systemPrompt,
            String contextInstructions,
            String userInstructions,
            List<AppliedInstruction> appliedInstructions,
            AnswerMode answerMode
        ) {
            this(
                model,
                systemPrompt,
                contextInstructions,
                userInstructions,
                appliedInstructions,
                answerMode,
                new PromptPolicySnapshot(
                    "",
                    "",
                    "",
                    contextInstructions,
                    userInstructions,
                    "",
                    "",
                    "",
                    systemPrompt,
                    List.<ChatRunMessage>of(),
                    null,
                    List.of(),
                    null,
                    false
                )
            );
        }
    }
}
