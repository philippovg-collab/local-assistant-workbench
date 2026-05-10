package com.example.demo.service;

import com.example.demo.llm.LlmClient;
import com.example.demo.model.ChatMode;
import com.example.demo.model.ContextAssemblyHistoryItem;
import com.example.demo.model.ContextAssemblyMemoryItem;
import com.example.demo.service.ChatPromptAssemblyService.PromptAssembly;
import com.example.demo.service.ChatResultFactory.PreparedChatResult;
import com.example.demo.service.ChatResultRecorder.ChatExecutionContext;
import com.example.demo.service.context.ConversationStickyStateService.StickyResolution;
import com.example.demo.service.context.ContextAssemblyService;
import com.example.demo.service.context.PreparedContextAssembly;
import java.util.List;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ChatContextAssemblyCoordinator {

    private final ContextAssemblyService contextAssemblyService;

    @Autowired
    public ChatContextAssemblyCoordinator(ContextAssemblyService contextAssemblyService) {
        this.contextAssemblyService = contextAssemblyService;
    }

    ChatContextAssemblyCoordinator() {
        this.contextAssemblyService = null;
    }

    PreparedContextAssembly prepare(
        PromptAssembly assembly,
        ChatMode mode,
        ChatExecutionContext context,
        StickyResolution stickyResolution
    ) {
        if (contextAssemblyService == null) {
            return PreparedContextAssembly.inactive();
        }
        return contextAssemblyService.prepare(
            context.traceContext().id(),
            assembly.requestWithResolvedScope(),
            mode,
            stickyResolution == null ? null : stickyResolution.metadata()
        );
    }

    ContextMessages emptyMessages(
        PreparedContextAssembly contextAssembly,
        ChatExecutionContext executionContext
    ) {
        return persist(contextAssembly, List.of(), executionContext, ignored -> List.of());
    }

    ContextMessages directMessages(
        ChatPromptAssemblyService promptAssemblyService,
        PromptAssembly assembly,
        PreparedContextAssembly contextAssembly,
        ChatExecutionContext executionContext
    ) {
        List<LlmClient.Message> messages = promptAssemblyService.directMessages(
            assembly.promptPolicy(),
            assembly.requestWithResolvedScope().prompt(),
            contextAssembly.selectedHistory(),
            contextAssembly.selectedMemory()
        );
        return persist(
            contextAssembly,
            messages,
            executionContext,
            context -> promptAssemblyService.directMessages(
                assembly.promptPolicy(),
                assembly.requestWithResolvedScope().prompt(),
                context.history(),
                context.memory()
            )
        );
    }

    ContextMessages ragMessages(
        ChatPromptAssemblyService promptAssemblyService,
        PromptAssembly assembly,
        MaterialRetrievalResult retrievalResult,
        PreparedContextAssembly contextAssembly,
        ChatExecutionContext executionContext
    ) {
        String prompt = assembly.requestWithResolvedScope().prompt();
        List<LlmClient.Message> messages = promptAssemblyService.ragMessages(
            assembly.promptPolicy(),
            prompt,
            retrievalResult.matches(),
            contextAssembly.selectedHistory(),
            contextAssembly.selectedMemory()
        );
        return persist(
            contextAssembly,
            messages,
            executionContext,
            context -> promptAssemblyService.ragMessages(
                assembly.promptPolicy(),
                prompt,
                retrievalResult.matches(),
                context.history(),
                context.memory()
            )
        );
    }

    PreparedChatResult withContext(
        PreparedChatResult preparedResult,
        PreparedContextAssembly contextAssembly
    ) {
        if (contextAssembly == null || !contextAssembly.active()) {
            return preparedResult;
        }
        return preparedResult.withResponse(preparedResult.response().withConversationMetadata(
            contextAssembly.conversationId(),
            contextAssembly.turnNo(),
            contextAssembly.snapshotId(),
            contextAssembly.summary()
        ));
    }

    private ContextMessages persist(
        PreparedContextAssembly contextAssembly,
        List<LlmClient.Message> messages,
        ChatExecutionContext executionContext,
        Function<SelectedContext, List<LlmClient.Message>> messageBuilder
    ) {
        if (contextAssemblyService == null || contextAssembly == null || !contextAssembly.active()) {
            return new ContextMessages(PreparedContextAssembly.inactive(), messages);
        }
        PreparedContextAssembly persisted = contextAssemblyService.persistOrDegrade(
            contextAssembly,
            messages,
            executionContext.traceContext()
        );
        if (!persisted.selectedHistory().equals(contextAssembly.selectedHistory())
            || !persisted.selectedMemory().equals(contextAssembly.selectedMemory())) {
            return new ContextMessages(persisted, messageBuilder.apply(new SelectedContext(
                persisted.selectedHistory(),
                persisted.selectedMemory()
            )));
        }
        return new ContextMessages(persisted, messages);
    }

    private record SelectedContext(
        List<ContextAssemblyHistoryItem> history,
        List<ContextAssemblyMemoryItem> memory
    ) {
    }

    record ContextMessages(
        PreparedContextAssembly contextAssembly,
        List<LlmClient.Message> messages
    ) {
    }
}
