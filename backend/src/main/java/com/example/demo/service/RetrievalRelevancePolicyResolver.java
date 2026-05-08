package com.example.demo.service;

import com.example.demo.config.RagProperties;
import com.example.demo.config.RolloutProperties;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class RetrievalRelevancePolicyResolver {

    private static final Logger logger = LoggerFactory.getLogger(RetrievalRelevancePolicyResolver.class);

    private final RagProperties ragProperties;
    private final RolloutProperties rolloutProperties;
    private final Map<RelevanceProfile, RelevancePolicy> relevancePolicies;

    RetrievalRelevancePolicyResolver(RagProperties ragProperties, RolloutProperties rolloutProperties) {
        this.ragProperties = ragProperties;
        this.rolloutProperties = rolloutProperties;
        this.relevancePolicies = Map.of(
            RelevanceProfile.LEGACY, new LegacyRelevancePolicy(),
            RelevanceProfile.HYBRID_V1, new HybridV1RelevancePolicy(),
            RelevanceProfile.HYBRID_RERANK_V1, new HybridRerankV1RelevancePolicy()
        );
    }

    RelevancePolicy configuredRelevancePolicy() {
        RelevanceProfile profile = configuredRelevanceProfile();
        RelevancePolicy policy = relevancePolicies.get(profile);
        if (policy == null) {
            throw new IllegalArgumentException(
                "No relevance policy registered for profile '" + profile.propertyValue() + "'."
            );
        }
        return policy;
    }

    private RelevanceProfile configuredRelevanceProfile() {
        RelevanceProfile configuredProfile = RelevanceProfile.fromProperty(ragProperties.getRelevanceProfile());
        if (!rolloutProperties.isRerankerV1() && configuredProfile == RelevanceProfile.HYBRID_RERANK_V1) {
            logger.info(
                "Quality-layer capability suppressed: capability={} reason={}",
                "reranker-v1",
                "relevance profile forced to hybrid-v1"
            );
            return RelevanceProfile.HYBRID_V1;
        }
        return configuredProfile;
    }
}
