package com.example.demo.service;

import com.example.demo.config.RolloutProperties;
import com.example.demo.error.ApplicationException;
import com.example.demo.error.ErrorType;
import com.example.demo.model.StructuredV1ProofStatus;

public interface StructuredV1ProofService {

    String REQUIRED_CODE = "material.structured_rollout_proof_required";

    StructuredV1ProofStatus status();

    default void requireWriteAllowed() {
        StructuredV1ProofStatus currentStatus = status();
        if ("DISABLED".equals(currentStatus.status()) || "COMPATIBLE".equals(currentStatus.status())) {
            return;
        }

        throw new ApplicationException(
            ErrorType.CONFLICT,
            REQUIRED_CODE,
            currentStatus.reasonMessage()
        );
    }

    static StructuredV1ProofService allowAllForTests(RolloutProperties rolloutProperties) {
        return new AllowAllStructuredV1ProofService(rolloutProperties);
    }
}

final class AllowAllStructuredV1ProofService implements StructuredV1ProofService {

    private final RolloutProperties rolloutProperties;

    AllowAllStructuredV1ProofService(RolloutProperties rolloutProperties) {
        this.rolloutProperties = rolloutProperties == null ? new RolloutProperties() : rolloutProperties;
    }

    @Override
    public StructuredV1ProofStatus status() {
        if (!rolloutProperties.isStructuredV1()) {
            return StructuredV1ProofStatus.disabled();
        }
        return StructuredV1ProofStatus.compatible("test-override");
    }
}
