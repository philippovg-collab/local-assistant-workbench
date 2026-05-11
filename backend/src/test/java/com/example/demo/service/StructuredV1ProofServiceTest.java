package com.example.demo.service;

import com.example.demo.config.RolloutProperties;
import com.example.demo.error.ApplicationException;
import com.example.demo.model.eval.EvalCompareStatus;
import com.example.demo.model.eval.EvalCompatibilityStatus;
import com.example.demo.model.eval.EvalRunCompare;
import com.example.demo.service.eval.EvalStructuredV1ProofService;
import com.example.demo.service.eval.port.EvalCompareRepository;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StructuredV1ProofServiceTest {

    @Test
    void blocksStructuredWritesWhenProofCompareIdIsMissing() {
        RolloutProperties rolloutProperties = RolloutProperties.enabledForTests();
        rolloutProperties.setStructuredV1(true);
        rolloutProperties.setStructuredV1ProofCompareId(null);
        StructuredV1ProofService service = new EvalStructuredV1ProofService(
            rolloutProperties,
            new FixedProvider(null)
        );

        ApplicationException exception = assertThrows(ApplicationException.class, service::requireWriteAllowed);

        assertEquals("material.structured_rollout_proof_required", exception.getCode());
        assertEquals("PROOF_MISSING", service.status().status());
        assertEquals("structured_v1.proof_missing", service.status().reasonCode());
    }

    @Test
    void allowsStructuredWritesForSuccessfulCompatibleCompare() {
        RolloutProperties rolloutProperties = RolloutProperties.enabledForTests();
        rolloutProperties.setStructuredV1(true);
        rolloutProperties.setStructuredV1ProofCompareId("11111111-1111-1111-1111-111111111111");
        StructuredV1ProofService service = new EvalStructuredV1ProofService(
            rolloutProperties,
            new FixedProvider(new SingleCompareRepository(compare(
                "11111111-1111-1111-1111-111111111111",
                EvalCompareStatus.COMPATIBLE,
                EvalCompatibilityStatus.COMPATIBLE
            )))
        );

        service.requireWriteAllowed();

        assertEquals("COMPATIBLE", service.status().status());
    }

    @Test
    void blocksWarningOnlyCompareForStructuredRolloutProof() {
        RolloutProperties rolloutProperties = RolloutProperties.enabledForTests();
        rolloutProperties.setStructuredV1(true);
        rolloutProperties.setStructuredV1ProofCompareId("11111111-1111-1111-1111-111111111111");
        StructuredV1ProofService service = new EvalStructuredV1ProofService(
            rolloutProperties,
            new FixedProvider(new SingleCompareRepository(compare(
                "11111111-1111-1111-1111-111111111111",
                EvalCompareStatus.COMPATIBLE,
                EvalCompatibilityStatus.WARNING_ONLY
            )))
        );

        ApplicationException exception = assertThrows(ApplicationException.class, service::requireWriteAllowed);

        assertEquals("material.structured_rollout_proof_required", exception.getCode());
        assertEquals("PROOF_INCOMPATIBLE", service.status().status());
        assertEquals("structured_v1.proof_warning_only", service.status().reasonCode());
    }

    @Test
    void blocksCompatibleCompareWhenOverallVerdictDidNotPass() {
        RolloutProperties rolloutProperties = RolloutProperties.enabledForTests();
        rolloutProperties.setStructuredV1(true);
        rolloutProperties.setStructuredV1ProofCompareId("11111111-1111-1111-1111-111111111111");
        StructuredV1ProofService service = new EvalStructuredV1ProofService(
            rolloutProperties,
            new FixedProvider(new SingleCompareRepository(compare(
                "11111111-1111-1111-1111-111111111111",
                EvalCompareStatus.COMPATIBLE,
                EvalCompatibilityStatus.COMPATIBLE,
                "FAIL"
            )))
        );

        ApplicationException exception = assertThrows(ApplicationException.class, service::requireWriteAllowed);

        assertEquals("material.structured_rollout_proof_required", exception.getCode());
        assertEquals("PROOF_FAILED", service.status().status());
        assertEquals("structured_v1.proof_failed", service.status().reasonCode());
    }

    private static EvalRunCompare compare(
        String id,
        EvalCompareStatus status,
        EvalCompatibilityStatus compatibilityStatus
    ) {
        return compare(id, status, compatibilityStatus, "PASS");
    }

    private static EvalRunCompare compare(
        String id,
        EvalCompareStatus status,
        EvalCompatibilityStatus compatibilityStatus,
        String overallVerdict
    ) {
        return new EvalRunCompare(
            id,
            "22222222-2222-2222-2222-222222222222",
            "33333333-3333-3333-3333-333333333333",
            status,
            compatibilityStatus,
            List.of(),
            null,
            "golden:v1",
            "golden:v1",
            "44444444-4444-4444-4444-444444444444",
            "55555555-5555-5555-5555-555555555555",
            "materials",
            "materials",
            "search",
            "search",
            "config",
            "config",
            Map.of("overallVerdict", overallVerdict),
            Instant.parse("2026-05-01T00:00:00Z"),
            Instant.parse("2026-05-01T00:00:00Z")
        );
    }

    private record SingleCompareRepository(EvalRunCompare compare) implements EvalCompareRepository {

        @Override
        public List<EvalRunCompare> findCompares() {
            return List.of(compare);
        }

        @Override
        public Optional<EvalRunCompare> findCompare(String id) {
            return compare.id().equals(id) ? Optional.of(compare) : Optional.empty();
        }

        @Override
        public EvalRunCompare saveCompare(EvalRunCompare compare) {
            return compare;
        }

        @Override
        public boolean isStorageReady() {
            return true;
        }
    }

    private record FixedProvider(EvalCompareRepository repository) implements ObjectProvider<EvalCompareRepository> {

        @Override
        public EvalCompareRepository getObject(Object... args) throws BeansException {
            return repository;
        }

        @Override
        public EvalCompareRepository getIfAvailable() throws BeansException {
            return repository;
        }

        @Override
        public EvalCompareRepository getIfUnique() throws BeansException {
            return repository;
        }

        @Override
        public EvalCompareRepository getObject() throws BeansException {
            return repository;
        }

        @Override
        public Iterator<EvalCompareRepository> iterator() {
            return stream().iterator();
        }

        @Override
        public Stream<EvalCompareRepository> stream() {
            return repository == null ? Stream.empty() : Stream.of(repository);
        }

        @Override
        public Stream<EvalCompareRepository> orderedStream() {
            return stream();
        }
    }
}
