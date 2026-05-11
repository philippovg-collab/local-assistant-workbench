package com.example.demo.infrastructure.eval;

import com.example.demo.model.EvidenceLocator;
import com.example.demo.model.eval.EvalCase;
import com.example.demo.model.eval.EvalCaseOrigin;
import com.example.demo.model.eval.EvalCaseReview;
import com.example.demo.model.eval.EvalCaseRevision;
import com.example.demo.model.eval.EvalCaseSeverity;
import com.example.demo.model.eval.EvalCaseType;
import com.example.demo.model.eval.EvalDataset;
import com.example.demo.model.eval.EvalDatasetKind;
import com.example.demo.model.eval.EvalDatasetSummary;
import com.example.demo.model.eval.EvalDatasetVersion;
import com.example.demo.model.eval.EvalExpectedMode;
import com.example.demo.model.eval.EvalLifecycleStatus;
import com.example.demo.model.eval.EvalReviewStatus;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.RowMapper;

final class EvalJdbcRowMappers {

    private final EvalJdbcJsonMapper jsonMapper;

    EvalJdbcRowMappers(EvalJdbcJsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    RowMapper<EvalDataset> dataset() {
        return (resultSet, rowNum) -> new EvalDataset(
            resultSet.getObject("id").toString(),
            resultSet.getString("dataset_key"),
            EvalDatasetKind.valueOf(resultSet.getString("kind")),
            resultSet.getString("version"),
            EvalLifecycleStatus.valueOf(resultSet.getString("status")),
            resultSet.getString("name"),
            resultSet.getString("description"),
            jsonMapper.readStringList(resultSet.getString("tags_jsonb"), "eval_dataset.json_read_failed", "Unable to read eval dataset tags"),
            toInstant(resultSet.getTimestamp("created_at")),
            toInstant(resultSet.getTimestamp("updated_at"))
        );
    }

    RowMapper<EvalDatasetSummary> summary() {
        return (resultSet, rowNum) -> new EvalDatasetSummary(
            resultSet.getObject("id").toString(),
            resultSet.getString("dataset_key"),
            EvalDatasetKind.valueOf(resultSet.getString("kind")),
            resultSet.getString("version"),
            EvalLifecycleStatus.valueOf(resultSet.getString("status")),
            resultSet.getString("name"),
            resultSet.getString("description"),
            jsonMapper.readStringList(resultSet.getString("tags_jsonb"), "eval_dataset.json_read_failed", "Unable to read eval dataset tags"),
            resultSet.getInt("case_count"),
            toInstant(resultSet.getTimestamp("created_at")),
            toInstant(resultSet.getTimestamp("updated_at"))
        );
    }

    RowMapper<EvalCase> evalCase() {
        return (resultSet, rowNum) -> new EvalCase(
            resultSet.getObject("id").toString(),
            resultSet.getObject("dataset_id").toString(),
            resultSet.getString("case_key"),
            resultSet.getInt("revision"),
            EvalCaseType.valueOf(resultSet.getString("case_type")),
            nullableExpectedMode(resultSet.getString("expected_mode")),
            EvalCaseSeverity.valueOf(resultSet.getString("severity")),
            resultSet.getString("question"),
            jsonMapper.readMap(resultSet.getString("knowledge_scope_jsonb"), "eval_case.json_read_failed", "Unable to read eval case knowledge scope"),
            jsonMapper.readMap(resultSet.getString("retrieval_filters_jsonb"), "eval_case.json_read_failed", "Unable to read eval case retrieval filters"),
            jsonMapper.readGoldFacts(resultSet.getString("gold_facts_jsonb"), "eval_case.json_read_failed"),
            jsonMapper.readStringList(resultSet.getString("accepted_answers_jsonb"), "eval_case.json_read_failed", "Unable to read eval case accepted answers"),
            jsonMapper.readList(resultSet.getString("gold_evidence_locators_jsonb"), EvidenceLocator.class, "eval_case.json_read_failed", "Unable to read eval case gold evidence locators"),
            jsonMapper.read(resultSet.getString("required_doc_groups_jsonb"), EvalJdbcJsonMapper.LOCATOR_GROUPS, "eval_case.json_read_failed", "Unable to read eval case required doc groups"),
            jsonMapper.readStringList(resultSet.getString("forbidden_document_refs_jsonb"), "eval_case.json_read_failed", "Unable to read eval case forbidden document refs"),
            jsonMapper.readStringList(resultSet.getString("tags_jsonb"), "eval_case.json_read_failed", "Unable to read eval case tags"),
            jsonMapper.read(resultSet.getString("origin_jsonb"), EvalCaseOrigin.class, "eval_case.json_read_failed", "Unable to read eval case origin"),
            EvalReviewStatus.valueOf(resultSet.getString("review_status")),
            resultSet.getBoolean("active"),
            toInstant(resultSet.getTimestamp("created_at")),
            toInstant(resultSet.getTimestamp("updated_at"))
        );
    }

    RowMapper<EvalCaseReview> review() {
        return (resultSet, rowNum) -> new EvalCaseReview(
            resultSet.getObject("id").toString(),
            resultSet.getObject("case_id").toString(),
            nullableInteger(resultSet.getObject("case_revision")),
            EvalReviewStatus.valueOf(resultSet.getString("status")),
            resultSet.getString("reviewer"),
            resultSet.getString("note"),
            jsonMapper.readMap(resultSet.getString("metadata_jsonb"), "eval_case_review.json_read_failed", "Unable to read eval case review metadata"),
            toInstant(resultSet.getTimestamp("created_at")),
            toInstant(resultSet.getTimestamp("updated_at"))
        );
    }

    RowMapper<EvalCaseRevision> revision() {
        return (resultSet, rowNum) -> {
            EvalCase snapshot = new EvalCase(
                resultSet.getObject("case_id").toString(),
                resultSet.getObject("dataset_id").toString(),
                resultSet.getString("case_key"),
                resultSet.getInt("revision"),
                EvalCaseType.valueOf(resultSet.getString("case_type")),
                nullableExpectedMode(resultSet.getString("expected_mode")),
                EvalCaseSeverity.valueOf(resultSet.getString("severity")),
                resultSet.getString("question"),
                jsonMapper.readMap(resultSet.getString("knowledge_scope_jsonb"), "eval_case_revision.json_read_failed", "Unable to read eval case revision knowledge scope"),
                jsonMapper.readMap(resultSet.getString("retrieval_filters_jsonb"), "eval_case_revision.json_read_failed", "Unable to read eval case revision retrieval filters"),
                jsonMapper.readGoldFacts(resultSet.getString("gold_facts_jsonb"), "eval_case_revision.json_read_failed"),
                jsonMapper.readStringList(resultSet.getString("accepted_answers_jsonb"), "eval_case_revision.json_read_failed", "Unable to read eval case revision accepted answers"),
                jsonMapper.readList(resultSet.getString("gold_evidence_locators_jsonb"), EvidenceLocator.class, "eval_case_revision.json_read_failed", "Unable to read eval case revision gold evidence locators"),
                jsonMapper.read(resultSet.getString("required_doc_groups_jsonb"), EvalJdbcJsonMapper.LOCATOR_GROUPS, "eval_case_revision.json_read_failed", "Unable to read eval case revision required doc groups"),
                jsonMapper.readStringList(resultSet.getString("forbidden_document_refs_jsonb"), "eval_case_revision.json_read_failed", "Unable to read eval case revision forbidden document refs"),
                jsonMapper.readStringList(resultSet.getString("tags_jsonb"), "eval_case_revision.json_read_failed", "Unable to read eval case revision tags"),
                jsonMapper.read(resultSet.getString("origin_jsonb"), EvalCaseOrigin.class, "eval_case_revision.json_read_failed", "Unable to read eval case revision origin"),
                EvalReviewStatus.valueOf(resultSet.getString("review_status")),
                resultSet.getBoolean("active"),
                toInstant(resultSet.getTimestamp("created_at")),
                toInstant(resultSet.getTimestamp("created_at"))
            );
            return new EvalCaseRevision(
                resultSet.getObject("id").toString(),
                snapshot,
                resultSet.getString("content_hash"),
                resultSet.getString("created_by"),
                toInstant(resultSet.getTimestamp("created_at"))
            );
        };
    }

    RowMapper<EvalDatasetVersion> datasetVersion() {
        return (resultSet, rowNum) -> new EvalDatasetVersion(
            resultSet.getObject("id").toString(),
            resultSet.getObject("dataset_id").toString(),
            resultSet.getString("version"),
            resultSet.getString("dataset_hash"),
            resultSet.getInt("case_count"),
            jsonMapper.read(resultSet.getString("case_revision_refs_jsonb"), EvalJdbcJsonMapper.CASE_REVISION_REFS, "eval_dataset_version.json_read_failed", "Unable to read eval dataset version case refs"),
            resultSet.getString("created_by"),
            resultSet.getString("note"),
            toInstant(resultSet.getTimestamp("created_at"))
        );
    }

    private EvalExpectedMode nullableExpectedMode(String value) {
        return value == null || value.isBlank() ? null : EvalExpectedMode.valueOf(value);
    }

    private Integer nullableInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.valueOf(String.valueOf(value));
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
