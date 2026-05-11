package com.example.demo.service.eval;

import com.example.demo.config.RolloutProperties;
import com.example.demo.model.StructuredV1ProofStatus;
import com.example.demo.model.eval.EvalCompareStatus;
import com.example.demo.model.eval.EvalCompatibilityStatus;
import com.example.demo.model.eval.EvalRunCompare;
import com.example.demo.service.StructuredV1ProofService;
import com.example.demo.service.eval.port.EvalCompareRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class EvalStructuredV1ProofService implements StructuredV1ProofService {

    private final RolloutProperties rolloutProperties;
    private final ObjectProvider<EvalCompareRepository> compareRepositoryProvider;

    @Autowired
    public EvalStructuredV1ProofService(
        RolloutProperties rolloutProperties,
        ObjectProvider<EvalCompareRepository> compareRepositoryProvider
    ) {
        this.rolloutProperties = rolloutProperties == null ? new RolloutProperties() : rolloutProperties;
        this.compareRepositoryProvider = compareRepositoryProvider;
    }

    @Override
    public StructuredV1ProofStatus status() {
        if (!rolloutProperties.isStructuredV1()) {
            return StructuredV1ProofStatus.disabled();
        }

        String compareId = rolloutProperties.getStructuredV1ProofCompareId();
        if (!StringUtils.hasText(compareId)) {
            return proofBlocked(
                "PROOF_MISSING",
                null,
                "structured_v1.proof_missing",
                "APP_ROLLOUT_STRUCTURED_V1=true requires APP_ROLLOUT_STRUCTURED_V1_PROOF_COMPARE_ID."
            );
        }

        EvalCompareRepository repository = compareRepositoryProvider == null ? null : compareRepositoryProvider.getIfAvailable();
        if (repository == null || !repository.isStorageReady()) {
            return proofBlocked(
                "PROOF_FAILED",
                compareId,
                "structured_v1.proof_storage_unavailable",
                "Eval compare storage is not available for structured-v1 proof validation."
            );
        }

        String normalizedCompareId = compareId.trim();
        return repository.findCompare(normalizedCompareId)
            .map(compare -> statusForCompare(normalizedCompareId, compare))
            .orElseGet(() -> proofBlocked(
                "PROOF_NOT_FOUND",
                normalizedCompareId,
                "structured_v1.proof_not_found",
                "Configured structured-v1 proof compare id was not found."
            ));
    }

    private StructuredV1ProofStatus statusForCompare(String compareId, EvalRunCompare compare) {
        if (compare.status() == EvalCompareStatus.COMPATIBLE
            && compare.compatibilityStatus() == EvalCompatibilityStatus.COMPATIBLE
            && "PASS".equals(String.valueOf(compare.summary().get("overallVerdict")))) {
            return StructuredV1ProofStatus.compatible(compareId);
        }

        if (compare.status() == EvalCompareStatus.COMPATIBLE
            && compare.compatibilityStatus() == EvalCompatibilityStatus.COMPATIBLE) {
            return proofBlocked(
                "PROOF_FAILED",
                compareId,
                "structured_v1.proof_failed",
                "Configured structured-v1 proof compare is compatible but did not pass the eval verdict."
            );
        }

        String code = compare.status() == EvalCompareStatus.COMPATIBLE
            ? "structured_v1.proof_warning_only"
            : "structured_v1.proof_incompatible";
        return proofBlocked(
            "PROOF_INCOMPATIBLE",
            compareId,
            code,
            "Configured structured-v1 proof compare is not a successful compatible snapshot compare."
        );
    }

    private StructuredV1ProofStatus proofBlocked(String status, String compareId, String reasonCode, String reasonMessage) {
        return new StructuredV1ProofStatus(
            status,
            StringUtils.hasText(compareId) ? compareId.trim() : null,
            reasonCode,
            reasonMessage
        );
    }
}
