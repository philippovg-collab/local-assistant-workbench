package com.example.demo.service;

import com.example.demo.model.ChatExecutionRequest;
import org.springframework.stereotype.Service;

@Service
public class ChatRetrievalStep {

    private final MaterialService materialService;

    public ChatRetrievalStep(MaterialService materialService) {
        this.materialService = materialService;
    }

    public MaterialRetrievalResult retrieve(
        ChatExecutionRequest request,
        KnowledgePresetService.ResolvedKnowledgeScopeContext scopeContext
    ) {
        return retrieve(request, scopeContext, request.prompt());
    }

    public MaterialRetrievalResult retrieve(
        ChatExecutionRequest request,
        KnowledgePresetService.ResolvedKnowledgeScopeContext scopeContext,
        String resolvedRetrievalQuery
    ) {
        return materialService.retrieveContext(
            resolvedRetrievalQuery == null ? request.prompt() : resolvedRetrievalQuery,
            scopeContext.effectiveScope(),
            request.retrievalFilters(),
            request.dismissedRetrievalHintKeys()
        );
    }
}
