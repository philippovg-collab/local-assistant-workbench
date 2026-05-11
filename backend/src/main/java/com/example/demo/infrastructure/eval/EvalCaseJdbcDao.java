package com.example.demo.infrastructure.eval;

import com.example.demo.error.ErrorType;
import com.example.demo.error.StorageException;
import com.example.demo.model.eval.EvalCase;
import com.example.demo.model.eval.EvalCaseReview;
import com.example.demo.model.eval.EvalCaseRevision;
import com.example.demo.model.eval.EvalDatasetVersionCaseRef;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

final class EvalCaseJdbcDao {

    private static final String CASE_COLUMNS = """
        id,
        dataset_id,
        case_key,
        revision,
        case_type,
        expected_mode,
        severity,
        question,
        knowledge_scope_jsonb,
        retrieval_filters_jsonb,
        gold_facts_jsonb,
        gold_answers_jsonb,
        gold_evidence_jsonb,
        accepted_answers_jsonb,
        gold_evidence_locators_jsonb,
        required_doc_groups_jsonb,
        forbidden_document_refs_jsonb,
        tags_jsonb,
        origin_jsonb,
        review_status,
        active,
        created_at,
        updated_at
        """;

    private static final String REVISION_COLUMNS = """
        id,
        case_id,
        dataset_id,
        case_key,
        revision,
        case_type,
        expected_mode,
        severity,
        question,
        knowledge_scope_jsonb,
        retrieval_filters_jsonb,
        gold_facts_jsonb,
        accepted_answers_jsonb,
        gold_evidence_locators_jsonb,
        required_doc_groups_jsonb,
        forbidden_document_refs_jsonb,
        tags_jsonb,
        origin_jsonb,
        review_status,
        active,
        content_hash,
        created_by,
        created_at
        """;

    private final JdbcTemplate jdbcTemplate;
    private final EvalJdbcJsonMapper jsonMapper;
    private final EvalJdbcRowMappers rowMappers;

    EvalCaseJdbcDao(JdbcTemplate jdbcTemplate, EvalJdbcJsonMapper jsonMapper, EvalJdbcRowMappers rowMappers) {
        this.jdbcTemplate = jdbcTemplate;
        this.jsonMapper = jsonMapper;
        this.rowMappers = rowMappers;
    }

    EvalCase saveCase(EvalCase evalCase) {
        Instant now = Instant.now();
        Instant createdAt = evalCase.createdAt() == null ? now : evalCase.createdAt();
        Instant updatedAt = evalCase.updatedAt() == null ? createdAt : evalCase.updatedAt();
        try {
            jdbcTemplate.update(
                """
                    INSERT INTO eval_cases (
                        id,
                        dataset_id,
                        case_key,
                        revision,
                        case_type,
                        expected_mode,
                        severity,
                        question,
                        knowledge_scope_jsonb,
                        retrieval_filters_jsonb,
                        gold_facts_jsonb,
                        gold_answers_jsonb,
                        gold_evidence_jsonb,
                        accepted_answers_jsonb,
                        gold_evidence_locators_jsonb,
                        required_doc_groups_jsonb,
                        forbidden_document_refs_jsonb,
                        tags_jsonb,
                        origin_jsonb,
                        review_status,
                        active,
                        created_at,
                        updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?, ?, ?)
                    ON CONFLICT (id) DO UPDATE
                    SET case_key = EXCLUDED.case_key,
                        revision = EXCLUDED.revision,
                        case_type = EXCLUDED.case_type,
                        expected_mode = EXCLUDED.expected_mode,
                        severity = EXCLUDED.severity,
                        question = EXCLUDED.question,
                        knowledge_scope_jsonb = EXCLUDED.knowledge_scope_jsonb,
                        retrieval_filters_jsonb = EXCLUDED.retrieval_filters_jsonb,
                        gold_facts_jsonb = EXCLUDED.gold_facts_jsonb,
                        gold_answers_jsonb = EXCLUDED.gold_answers_jsonb,
                        gold_evidence_jsonb = EXCLUDED.gold_evidence_jsonb,
                        accepted_answers_jsonb = EXCLUDED.accepted_answers_jsonb,
                        gold_evidence_locators_jsonb = EXCLUDED.gold_evidence_locators_jsonb,
                        required_doc_groups_jsonb = EXCLUDED.required_doc_groups_jsonb,
                        forbidden_document_refs_jsonb = EXCLUDED.forbidden_document_refs_jsonb,
                        tags_jsonb = EXCLUDED.tags_jsonb,
                        origin_jsonb = EXCLUDED.origin_jsonb,
                        review_status = EXCLUDED.review_status,
                        active = EXCLUDED.active,
                        updated_at = EXCLUDED.updated_at
                    """,
                uuid(evalCase.id()),
                uuid(evalCase.datasetId()),
                evalCase.caseKey(),
                evalCase.revision(),
                evalCase.caseType().name(),
                evalCase.expectedMode() == null ? null : evalCase.expectedMode().name(),
                evalCase.severity().name(),
                evalCase.question(),
                jsonMapper.write(evalCase.knowledgeScope()),
                jsonMapper.write(evalCase.retrievalFilters()),
                jsonMapper.write(evalCase.goldFacts()),
                jsonMapper.write(evalCase.goldAnswers()),
                jsonMapper.write(evalCase.goldEvidence()),
                jsonMapper.write(evalCase.acceptedAnswers()),
                jsonMapper.write(evalCase.goldEvidenceLocators()),
                jsonMapper.write(evalCase.requiredDocGroups()),
                jsonMapper.write(evalCase.forbiddenDocumentRefs()),
                jsonMapper.write(evalCase.tags()),
                jsonMapper.write(evalCase.origin()),
                evalCase.reviewStatus().name(),
                evalCase.active(),
                Timestamp.from(createdAt),
                Timestamp.from(updatedAt)
            );
            return findCaseById(evalCase.id()).orElse(evalCase);
        } catch (DataAccessException exception) {
            throw storageFailure("eval_case.storage_write_failed", "Unable to persist eval case", exception);
        }
    }

    EvalCaseReview saveReview(EvalCaseReview review) {
        Instant now = Instant.now();
        Instant createdAt = review.createdAt() == null ? now : review.createdAt();
        Instant updatedAt = review.updatedAt() == null ? createdAt : review.updatedAt();
        try {
            jdbcTemplate.update(
                """
                    INSERT INTO eval_case_reviews (
                        id,
                        case_id,
                        case_revision,
                        status,
                        reviewer,
                        note,
                        metadata_jsonb,
                        created_at,
                        updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)
                    ON CONFLICT (id) DO UPDATE
                    SET case_revision = EXCLUDED.case_revision,
                        status = EXCLUDED.status,
                        reviewer = EXCLUDED.reviewer,
                        note = EXCLUDED.note,
                        metadata_jsonb = EXCLUDED.metadata_jsonb,
                        updated_at = EXCLUDED.updated_at
                    """,
                uuid(review.id()),
                uuid(review.caseId()),
                review.caseRevision(),
                review.status().name(),
                review.reviewer(),
                review.note(),
                jsonMapper.write(review.metadata()),
                Timestamp.from(createdAt),
                Timestamp.from(updatedAt)
            );
            return findReviewById(review.id()).orElse(review);
        } catch (DataAccessException exception) {
            throw storageFailure("eval_case_review.storage_write_failed", "Unable to persist eval case review", exception);
        }
    }

    EvalCaseRevision saveCaseRevision(EvalCaseRevision revision) {
        Instant now = Instant.now();
        Instant createdAt = revision.createdAt() == null ? now : revision.createdAt();
        EvalCase evalCase = revision.caseSnapshot();
        try {
            jdbcTemplate.update(
                """
                    INSERT INTO eval_case_revisions (
                        id,
                        case_id,
                        dataset_id,
                        case_key,
                        revision,
                        case_type,
                        expected_mode,
                        severity,
                        question,
                        knowledge_scope_jsonb,
                        retrieval_filters_jsonb,
                        gold_facts_jsonb,
                        accepted_answers_jsonb,
                        gold_evidence_locators_jsonb,
                        required_doc_groups_jsonb,
                        forbidden_document_refs_jsonb,
                        tags_jsonb,
                        origin_jsonb,
                        review_status,
                        active,
                        content_hash,
                        created_by,
                        created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?)
                    ON CONFLICT (case_id, revision) DO UPDATE
                    SET review_status = EXCLUDED.review_status,
                        active = EXCLUDED.active,
                        content_hash = EXCLUDED.content_hash
                    """,
                uuid(revision.id()),
                uuid(evalCase.id()),
                uuid(evalCase.datasetId()),
                evalCase.caseKey(),
                evalCase.revision(),
                evalCase.caseType().name(),
                evalCase.expectedMode() == null ? null : evalCase.expectedMode().name(),
                evalCase.severity().name(),
                evalCase.question(),
                jsonMapper.write(evalCase.knowledgeScope()),
                jsonMapper.write(evalCase.retrievalFilters()),
                jsonMapper.write(evalCase.goldFacts()),
                jsonMapper.write(evalCase.acceptedAnswers()),
                jsonMapper.write(evalCase.goldEvidenceLocators()),
                jsonMapper.write(evalCase.requiredDocGroups()),
                jsonMapper.write(evalCase.forbiddenDocumentRefs()),
                jsonMapper.write(evalCase.tags()),
                jsonMapper.write(evalCase.origin()),
                evalCase.reviewStatus().name(),
                evalCase.active(),
                revision.contentHash(),
                revision.createdBy(),
                Timestamp.from(createdAt)
            );
            return findCaseRevisions(evalCase.id()).stream()
                .filter(candidate -> candidate.caseSnapshot().revision() == evalCase.revision())
                .findFirst()
                .orElse(revision);
        } catch (DataAccessException exception) {
            throw storageFailure("eval_case_revision.storage_write_failed", "Unable to persist eval case revision", exception);
        }
    }

    List<EvalCaseRevision> findCaseRevisions(String caseId) {
        try {
            return jdbcTemplate.query(
                """
                    SELECT %s
                    FROM eval_case_revisions
                    WHERE case_id = ?
                    ORDER BY revision DESC
                    """.formatted(REVISION_COLUMNS),
                rowMappers.revision(),
                uuid(caseId)
            );
        } catch (DataAccessException exception) {
            throw storageFailure("eval_case_revision.storage_read_failed", "Unable to load eval case revisions", exception);
        }
    }

    Optional<EvalCaseRevision> findCaseRevision(String caseId, int revision) {
        try {
            return jdbcTemplate.query(
                """
                    SELECT %s
                    FROM eval_case_revisions
                    WHERE case_id = ?
                      AND revision = ?
                    LIMIT 1
                    """.formatted(REVISION_COLUMNS),
                rowMappers.revision(),
                uuid(caseId),
                revision
            ).stream().findFirst();
        } catch (DataAccessException exception) {
            throw storageFailure("eval_case_revision.storage_read_failed", "Unable to load eval case revision", exception);
        }
    }

    List<EvalCaseRevision> findCaseRevisionsByRefs(List<EvalDatasetVersionCaseRef> refs) {
        if (refs == null || refs.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(", ", refs.stream().map(ref -> "(?, ?)").toList());
        List<Object> args = new ArrayList<>();
        for (EvalDatasetVersionCaseRef ref : refs) {
            args.add(uuid(ref.caseId()));
            args.add(ref.revision());
        }
        try {
            return jdbcTemplate.query(
                """
                    SELECT %s
                    FROM eval_case_revisions
                    WHERE (case_id, revision) IN (
                    """.formatted(REVISION_COLUMNS) + placeholders + """
                    )
                    """,
                rowMappers.revision(),
                args.toArray()
            );
        } catch (DataAccessException exception) {
            throw storageFailure("eval_case_revision.storage_read_failed", "Unable to load eval case revisions", exception);
        }
    }

    Optional<EvalCase> findCaseById(String id) {
        return jdbcTemplate.query(
            """
                SELECT %s
                FROM eval_cases
                WHERE id = ?
                LIMIT 1
                """.formatted(CASE_COLUMNS),
            rowMappers.evalCase(),
            uuid(id)
        ).stream().findFirst();
    }

    List<EvalCase> findCasesByDatasetId(String datasetId) {
        return jdbcTemplate.query(
            """
                SELECT %s
                FROM eval_cases
                WHERE dataset_id = ?
                ORDER BY case_key ASC, revision ASC
                """.formatted(CASE_COLUMNS),
            rowMappers.evalCase(),
            uuid(datasetId)
        );
    }

    List<EvalCaseReview> findReviewsByDatasetId(String datasetId) {
        return jdbcTemplate.query(
            """
                SELECT r.id,
                       r.case_id,
                       r.case_revision,
                       r.status,
                       r.reviewer,
                       r.note,
                       r.metadata_jsonb,
                       r.created_at,
                       r.updated_at
                FROM eval_case_reviews r
                JOIN eval_cases c ON c.id = r.case_id
                WHERE c.dataset_id = ?
                ORDER BY r.created_at DESC, r.id ASC
                """,
            rowMappers.review(),
            uuid(datasetId)
        );
    }

    private Optional<EvalCaseReview> findReviewById(String id) {
        return jdbcTemplate.query(
            """
                SELECT id,
                       case_id,
                       case_revision,
                       status,
                       reviewer,
                       note,
                       metadata_jsonb,
                       created_at,
                       updated_at
                FROM eval_case_reviews
                WHERE id = ?
                LIMIT 1
                """,
            rowMappers.review(),
            uuid(id)
        ).stream().findFirst();
    }

    private UUID uuid(String id) {
        return UUID.fromString(id);
    }

    private StorageException storageFailure(String code, String message, DataAccessException exception) {
        return new StorageException(ErrorType.STORAGE_FAILURE, code, message, exception);
    }
}
