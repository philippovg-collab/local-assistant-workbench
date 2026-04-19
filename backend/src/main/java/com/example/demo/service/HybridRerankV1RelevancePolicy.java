package com.example.demo.service;

public class HybridRerankV1RelevancePolicy extends HybridV1RelevancePolicy {

    @Override
    public RelevanceProfile profile() {
        return RelevanceProfile.HYBRID_RERANK_V1;
    }
}
