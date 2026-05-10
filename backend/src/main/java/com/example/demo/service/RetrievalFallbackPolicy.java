package com.example.demo.service;

import com.example.demo.error.ApplicationException;
import com.example.demo.error.ErrorType;
import com.example.demo.service.material.LexicalProviderMode;
import com.example.demo.service.material.LexicalProviderType;
import java.util.List;

final class RetrievalFallbackPolicy {

    private final ProductionLexicalSearchRouter productionLexicalSearchRouter;

    RetrievalFallbackPolicy(ProductionLexicalSearchRouter productionLexicalSearchRouter) {
        this.productionLexicalSearchRouter = productionLexicalSearchRouter;
    }

    ProductionLexicalSearchRouter.LexicalSearchResult emptyLexicalResult() {
        ProductionLexicalSearchRouter.LexicalRoutingDecision decision = productionLexicalSearchRouter.currentDecision();
        if (decision == null) {
            return new ProductionLexicalSearchRouter.LexicalSearchResult(
                LexicalProviderMode.POSTGRES,
                LexicalProviderType.POSTGRES,
                false,
                "search.sync_disabled",
                "Elasticsearch search sync is disabled by configuration.",
                null,
                List.of()
            );
        }
        return new ProductionLexicalSearchRouter.LexicalSearchResult(
            decision.configuredMode(),
            decision.effectiveProvider(),
            decision.fallbackApplied(),
            decision.fallbackReasonCode(),
            decision.fallbackReasonMessage(),
            decision.searchHealth(),
            List.of()
        );
    }

    int normalizeSearchLimit(Integer limit, int defaultLimit, int maxLimit) {
        if (limit == null) {
            return defaultLimit;
        }
        if (limit <= 0) {
            throw new ApplicationException(
                ErrorType.INVALID_REQUEST,
                "search.invalid_limit",
                "Field 'limit' must be greater than zero"
            );
        }
        int maxSearchLimit = Math.max(1, maxLimit);
        if (limit > maxSearchLimit) {
            throw new ApplicationException(
                ErrorType.INVALID_REQUEST,
                "search.limit_too_large",
                "Field 'limit' must not exceed " + maxSearchLimit
            );
        }
        return limit;
    }
}
