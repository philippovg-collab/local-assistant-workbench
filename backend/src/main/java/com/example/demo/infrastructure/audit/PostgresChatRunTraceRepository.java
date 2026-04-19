package com.example.demo.infrastructure.audit;

import com.example.demo.api.ApiException;
import com.example.demo.model.AnswerMode;
import com.example.demo.model.ChatAuditRunDetail;
import com.example.demo.model.ChatAuditRunSummary;
import com.example.demo.model.ChatExecutionRequest;
import com.example.demo.model.ChatMode;
import com.example.demo.model.ChatRunEventTrace;
import com.example.demo.model.ChatRunMessage;
import com.example.demo.model.ChatRunOutputTrace;
import com.example.demo.model.ChatRunRequestSnapshot;
import com.example.demo.model.ChatRunTraceDetail;
import com.example.demo.model.ChatSource;
import com.example.demo.model.InstructionTraceEntry;
import com.example.demo.model.KnowledgeScopeResolved;
import com.example.demo.model.LlmCallTrace;
import com.example.demo.model.PromptPolicySnapshot;
import com.example.demo.model.RetrievalDebug;
import com.example.demo.model.RetrievalSummaryTrace;
import com.example.demo.model.RetrievalTrace;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresChatRunTraceRepository {

    private static final ObjectMapper JSON_MAPPER = JsonMapper.builder().findAndAddModules().build();
    private static final TypeReference<List<ChatRunMessage>> MESSAGES_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<InstructionTraceEntry>> INSTRUCTION_TRACE_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<ChatSource>> SOURCES_TYPE = new TypeReference<>() {
    };

    private static final RowMapper<HeaderRow> HEADER_ROW_MAPPER = (resultSet, rowNum) -> new HeaderRow(
        resultSet.getObject("id").toString(),
        ChatMode.valueOf(resultSet.getString("mode")),
        resultSet.getString("status"),
        resultSet.getString("requested_model"),
        resultSet.getString("resolved_model"),
        answerModeOf(resultSet.getString("requested_answer_mode")),
        answerModeOf(resultSet.getString("applied_answer_mode")),
        resultSet.getString("context_status"),
        toInstant(resultSet.getTimestamp("created_at")),
        toInstantOrNull(resultSet.getTimestamp("completed_at")),
        toInstantOrNull(resultSet.getTimestamp("failed_at")),
        resultSet.getObject("latency_ms_total", Long.class),
        resultSet.getString("failure_stage"),
        resultSet.getString("failure_code"),
        resultSet.getString("failure_message")
    );

    private final JdbcTemplate jdbcTemplate;

    public PostgresChatRunTraceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insertHeader(
        String runId,
        ChatMode mode,
        String requestedModel,
        AnswerMode requestedAnswerMode,
        Instant createdAt
    ) {
        write(() -> jdbcTemplate.update(
            """
                INSERT INTO chat_run_headers (
                    id,
                    mode,
                    status,
                    requested_model,
                    requested_answer_mode,
                    created_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
            UUID.fromString(runId),
            mode.name(),
            "RECEIVED",
            requestedModel,
            requestedAnswerMode == null ? null : requestedAnswerMode.value(),
            Timestamp.from(createdAt)
        ));
    }

    public void saveRequestSnapshot(
        String runId,
        ChatExecutionRequest request,
        ChatExecutionRequest normalizedRequest
    ) {
        write(() -> jdbcTemplate.update(
            """
                INSERT INTO chat_run_request_snapshots (
                    run_id,
                    request_jsonb,
                    normalized_request_jsonb,
                    prompt,
                    knowledge_scope_jsonb,
                    retrieval_filters_jsonb
                ) VALUES (?, ?::jsonb, ?::jsonb, ?, ?::jsonb, ?::jsonb)
                ON CONFLICT (run_id) DO UPDATE
                SET request_jsonb = EXCLUDED.request_jsonb,
                    normalized_request_jsonb = EXCLUDED.normalized_request_jsonb,
                    prompt = EXCLUDED.prompt,
                    knowledge_scope_jsonb = EXCLUDED.knowledge_scope_jsonb,
                    retrieval_filters_jsonb = EXCLUDED.retrieval_filters_jsonb
                """,
            UUID.fromString(runId),
            writeJson(request),
            writeJson(normalizedRequest),
            normalizedRequest.prompt(),
            writeJson(normalizedRequest.knowledgeScope()),
            writeJson(normalizedRequest.retrievalFilters())
        ));
    }

    public void savePromptSnapshot(String runId, PromptPolicySnapshot snapshot) {
        PromptPolicySnapshot safeSnapshot = snapshot == null
            ? new PromptPolicySnapshot(null, null, null, null, null, null, null, null, null, List.of(), null, List.of(), null, false)
            : snapshot;
        write(() -> jdbcTemplate.update(
            """
                INSERT INTO chat_run_prompt_snapshots (
                    run_id,
                    base_system_prompt,
                    system_instructions_text,
                    safety_instructions_text,
                    context_instructions_text,
                    user_instructions_text,
                    temporary_instruction_text,
                    answer_mode_block_text,
                    grounding_block_text,
                    grounding_rules_applied,
                    resolved_system_prompt,
                    messages_jsonb,
                    prompt_hash,
                    instruction_trace_jsonb,
                    knowledge_scope_resolved_jsonb,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?::jsonb, ?::jsonb, ?)
                ON CONFLICT (run_id) DO UPDATE
                SET base_system_prompt = EXCLUDED.base_system_prompt,
                    system_instructions_text = EXCLUDED.system_instructions_text,
                    safety_instructions_text = EXCLUDED.safety_instructions_text,
                    context_instructions_text = EXCLUDED.context_instructions_text,
                    user_instructions_text = EXCLUDED.user_instructions_text,
                    temporary_instruction_text = EXCLUDED.temporary_instruction_text,
                    answer_mode_block_text = EXCLUDED.answer_mode_block_text,
                    grounding_block_text = EXCLUDED.grounding_block_text,
                    grounding_rules_applied = EXCLUDED.grounding_rules_applied,
                    resolved_system_prompt = EXCLUDED.resolved_system_prompt,
                    prompt_hash = EXCLUDED.prompt_hash,
                    instruction_trace_jsonb = EXCLUDED.instruction_trace_jsonb,
                    knowledge_scope_resolved_jsonb = EXCLUDED.knowledge_scope_resolved_jsonb,
                    updated_at = EXCLUDED.updated_at
                """,
            UUID.fromString(runId),
            safeSnapshot.baseSystemPrompt(),
            safeSnapshot.systemInstructionsText(),
            safeSnapshot.safetyInstructionsText(),
            safeSnapshot.contextInstructionsText(),
            safeSnapshot.userInstructionsText(),
            safeSnapshot.temporaryInstructionText(),
            safeSnapshot.answerModeBlockText(),
            safeSnapshot.groundingBlockText(),
            safeSnapshot.groundingRulesApplied(),
            safeSnapshot.resolvedSystemPrompt(),
            writeJson(safeSnapshot.messages()),
            safeSnapshot.promptHash(),
            writeJson(safeSnapshot.instructionTrace()),
            writeJson(safeSnapshot.knowledgeScopeResolved()),
            Timestamp.from(Instant.now())
        ));
        markStatus(runId, "PROMPT_RESOLVED");
    }

    public void savePromptMessages(String runId, List<ChatRunMessage> messages) {
        write(() -> jdbcTemplate.update(
            """
                UPDATE chat_run_prompt_snapshots
                SET messages_jsonb = ?::jsonb,
                    updated_at = ?
                WHERE run_id = ?
                """,
            writeJson(messages == null ? List.of() : messages),
            Timestamp.from(Instant.now()),
            UUID.fromString(runId)
        ));
    }

    public void saveRetrievalSummary(
        String runId,
        String retrievalStatus,
        RetrievalTrace trace,
        RetrievalDebug debug
    ) {
        write(() -> jdbcTemplate.update(
            """
                INSERT INTO chat_run_retrieval_summaries (
                    run_id,
                    retrieval_status,
                    trace_jsonb,
                    debug_jsonb,
                    relevance_profile,
                    query_hints_jsonb,
                    manual_filters_jsonb,
                    effective_filters_jsonb,
                    rollout_flags_jsonb,
                    applied_capabilities_jsonb
                ) VALUES (?, ?, ?::jsonb, ?::jsonb, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb)
                ON CONFLICT (run_id) DO UPDATE
                SET retrieval_status = EXCLUDED.retrieval_status,
                    trace_jsonb = EXCLUDED.trace_jsonb,
                    debug_jsonb = EXCLUDED.debug_jsonb,
                    relevance_profile = EXCLUDED.relevance_profile,
                    query_hints_jsonb = EXCLUDED.query_hints_jsonb,
                    manual_filters_jsonb = EXCLUDED.manual_filters_jsonb,
                    effective_filters_jsonb = EXCLUDED.effective_filters_jsonb,
                    rollout_flags_jsonb = EXCLUDED.rollout_flags_jsonb,
                    applied_capabilities_jsonb = EXCLUDED.applied_capabilities_jsonb
                """,
            UUID.fromString(runId),
            retrievalStatus,
            writeJson(trace),
            writeJson(debug),
            debug == null ? null : debug.relevanceProfile(),
            writeJson(debug == null ? null : debug.queryHints()),
            writeJson(debug == null ? null : debug.manualFilters()),
            writeJson(debug == null ? null : debug.effectiveFilters()),
            writeJson(debug == null ? null : debug.activeRolloutFlags()),
            writeJson(debug == null ? List.of() : debug.appliedCapabilities())
        ));
        markStatus(runId, "RETRIEVAL_DONE");
    }

    public void insertLlmCall(String runId, LlmCallTrace call) {
        write(() -> jdbcTemplate.update(
            """
                INSERT INTO chat_run_llm_calls (
                    id,
                    run_id,
                    provider,
                    model,
                    request_messages_jsonb,
                    raw_response_text,
                    parsed_answer_text,
                    prompt_tokens,
                    completion_tokens,
                    total_tokens,
                    latency_ms,
                    retry_count,
                    timeout_seconds,
                    finish_reason,
                    error_code,
                    error_message,
                    created_at
                ) VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            UUID.fromString(call.id()),
            UUID.fromString(runId),
            call.provider(),
            call.model(),
            writeJson(call.requestMessages()),
            call.rawResponseText(),
            call.parsedAnswerText(),
            call.promptTokens(),
            call.completionTokens(),
            call.totalTokens(),
            call.latencyMs(),
            call.retryCount(),
            call.timeoutSeconds(),
            call.finishReason(),
            call.errorCode(),
            call.errorMessage(),
            Timestamp.from(call.createdAt())
        ));
        if (call.errorCode() == null) {
            markStatus(runId, "LLM_DONE");
        }
    }

    public void saveOutput(String runId, ChatRunOutputTrace output) {
        ChatRunOutputTrace safeOutput = output == null
            ? new ChatRunOutputTrace(null, null, List.of(), null, null, null)
            : output;
        write(() -> jdbcTemplate.update(
            """
                INSERT INTO chat_run_outputs (
                    run_id,
                    raw_model_answer,
                    final_user_answer,
                    sources_jsonb,
                    postprocess_jsonb,
                    abstained,
                    strict_sources_blocked_answer
                ) VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?, ?)
                ON CONFLICT (run_id) DO UPDATE
                SET raw_model_answer = EXCLUDED.raw_model_answer,
                    final_user_answer = EXCLUDED.final_user_answer,
                    sources_jsonb = EXCLUDED.sources_jsonb,
                    postprocess_jsonb = EXCLUDED.postprocess_jsonb,
                    abstained = EXCLUDED.abstained,
                    strict_sources_blocked_answer = EXCLUDED.strict_sources_blocked_answer
                """,
            UUID.fromString(runId),
            safeOutput.rawModelAnswer(),
            safeOutput.finalUserAnswer(),
            writeJson(safeOutput.sources()),
            writeJson(safeOutput.postprocess()),
            safeOutput.abstained(),
            safeOutput.strictSourcesBlockedAnswer()
        ));
        markStatus(runId, "POSTPROCESSED");
    }

    public void completeRun(
        String runId,
        String resolvedModel,
        AnswerMode appliedAnswerMode,
        String contextStatus,
        Instant completedAt,
        long latencyMsTotal
    ) {
        write(() -> jdbcTemplate.update(
            """
                UPDATE chat_run_headers
                SET status = 'COMPLETED',
                    resolved_model = ?,
                    applied_answer_mode = ?,
                    context_status = ?,
                    completed_at = ?,
                    latency_ms_total = ?
                WHERE id = ?
                """,
            resolvedModel,
            appliedAnswerMode == null ? null : appliedAnswerMode.value(),
            contextStatus,
            Timestamp.from(completedAt),
            latencyMsTotal,
            UUID.fromString(runId)
        ));
    }

    public void failRun(
        String runId,
        String failureStage,
        String failureCode,
        String failureMessage,
        Instant failedAt,
        long latencyMsTotal
    ) {
        write(() -> jdbcTemplate.update(
            """
                UPDATE chat_run_headers
                SET status = 'FAILED',
                    failed_at = ?,
                    latency_ms_total = ?,
                    failure_stage = ?,
                    failure_code = ?,
                    failure_message = ?
                WHERE id = ?
                """,
            Timestamp.from(failedAt),
            latencyMsTotal,
            failureStage,
            failureCode,
            failureMessage,
            UUID.fromString(runId)
        ));
    }

    public void insertEvent(String runId, String eventType, Object payload, Instant createdAt) {
        write(() -> jdbcTemplate.update(
            """
                INSERT INTO chat_run_events (
                    id,
                    run_id,
                    event_type,
                    event_payload_jsonb,
                    created_at
                ) VALUES (?, ?, ?, ?::jsonb, ?)
                """,
            UUID.randomUUID(),
            UUID.fromString(runId),
            eventType,
            writeJson(payload),
            Timestamp.from(createdAt)
        ));
    }

    public List<ChatAuditRunSummary> findRunSummaries(int limit) {
        return read(() -> jdbcTemplate.query(
            """
                SELECT
                    h.id,
                    h.mode,
                    h.status,
                    h.requested_model,
                    h.resolved_model,
                    h.requested_answer_mode,
                    h.applied_answer_mode,
                    h.context_status,
                    h.created_at,
                    h.completed_at,
                    h.failed_at,
                    h.latency_ms_total,
                    h.failure_stage,
                    h.failure_code,
                    h.failure_message,
                    r.prompt,
                    o.final_user_answer
                FROM chat_run_headers h
                LEFT JOIN chat_run_request_snapshots r ON r.run_id = h.id
                LEFT JOIN chat_run_outputs o ON o.run_id = h.id
                ORDER BY h.created_at DESC
                LIMIT ?
                """,
            (resultSet, rowNum) -> {
                HeaderRow header = HEADER_ROW_MAPPER.mapRow(resultSet, rowNum);
                return new ChatAuditRunSummary(
                    header.id(),
                    header.mode(),
                    firstNonBlank(header.resolvedModel(), header.requestedModel(), ""),
                    header.appliedAnswerMode() == null ? header.requestedAnswerMode() : header.appliedAnswerMode(),
                    clip(resultSet.getString("prompt"), 120),
                    clip(resultSet.getString("final_user_answer"), 160),
                    header.createdAt(),
                    header.status(),
                    header.failureStage(),
                    header.failureCode(),
                    header.failedAt(),
                    header.latencyMsTotal()
                );
            },
            limit
        ));
    }

    public Optional<ChatAuditRunDetail> findAuditRunDetail(String runId) {
        return findTrace(runId).map(trace -> {
            PromptPolicySnapshot promptSnapshot = trace.promptSnapshot();
            ChatRunRequestSnapshot requestSnapshot = trace.requestSnapshot();
            ChatRunOutputTrace output = trace.output();
            RetrievalSummaryTrace retrieval = trace.retrievalSummary();
            return new ChatAuditRunDetail(
                trace.id(),
                trace.mode(),
                firstNonBlank(trace.resolvedModel(), trace.requestedModel(), ""),
                requestSnapshot == null ? "" : requestSnapshot.prompt(),
                output == null ? "" : nullToEmpty(output.finalUserAnswer()),
                trace.contextStatus(),
                trace.appliedAnswerMode() == null ? trace.requestedAnswerMode() : trace.appliedAnswerMode(),
                trace.createdAt(),
                promptSnapshot == null ? List.of() : promptSnapshot.instructionTrace(),
                promptSnapshot == null ? KnowledgeScopeResolved.empty() : promptSnapshot.knowledgeScopeResolved(),
                retrieval == null || retrieval.trace() == null ? new RetrievalTrace(0, 0, 0, 0, 0, 0, 0, 0, 0) : retrieval.trace(),
                output == null ? List.of() : output.sources(),
                trace.status(),
                trace.failureStage(),
                trace.failureCode(),
                trace.failureMessage(),
                trace.completedAt(),
                trace.failedAt(),
                trace.latencyMsTotal()
            );
        });
    }

    public Optional<ChatRunTraceDetail> findTrace(String runId) {
        String normalizedRunId = UUID.fromString(runId).toString();
        Optional<HeaderRow> header = findHeader(normalizedRunId);
        if (header.isEmpty()) {
            return Optional.empty();
        }
        HeaderRow row = header.get();
        return Optional.of(new ChatRunTraceDetail(
            row.id(),
            row.mode(),
            row.status(),
            row.requestedModel(),
            row.resolvedModel(),
            row.requestedAnswerMode(),
            row.appliedAnswerMode(),
            row.contextStatus(),
            row.createdAt(),
            row.completedAt(),
            row.failedAt(),
            row.latencyMsTotal(),
            row.failureStage(),
            row.failureCode(),
            row.failureMessage(),
            findRequestSnapshot(normalizedRunId).orElse(null),
            findPromptSnapshot(normalizedRunId).orElse(null),
            findRetrievalSummary(normalizedRunId).orElse(null),
            findLlmCalls(normalizedRunId),
            findOutput(normalizedRunId).orElse(null),
            findEvents(normalizedRunId)
        ));
    }

    public boolean hasModernRuns() {
        Integer count = read(() -> jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM chat_run_headers",
            Integer.class
        ));
        return count != null && count > 0;
    }

    private Optional<HeaderRow> findHeader(String runId) {
        return queryOptional(
            """
                SELECT id, mode, status, requested_model, resolved_model, requested_answer_mode,
                       applied_answer_mode, context_status, created_at, completed_at, failed_at,
                       latency_ms_total, failure_stage, failure_code, failure_message
                FROM chat_run_headers
                WHERE id = ?
                LIMIT 1
                """,
            HEADER_ROW_MAPPER,
            UUID.fromString(runId)
        );
    }

    private Optional<ChatRunRequestSnapshot> findRequestSnapshot(String runId) {
        return queryOptional(
            """
                SELECT request_jsonb, normalized_request_jsonb, prompt, knowledge_scope_jsonb, retrieval_filters_jsonb
                FROM chat_run_request_snapshots
                WHERE run_id = ?
                LIMIT 1
                """,
            (resultSet, rowNum) -> new ChatRunRequestSnapshot(
                readTree(resultSet.getString("request_jsonb")),
                readTree(resultSet.getString("normalized_request_jsonb")),
                resultSet.getString("prompt"),
                readTree(resultSet.getString("knowledge_scope_jsonb")),
                readTree(resultSet.getString("retrieval_filters_jsonb"))
            ),
            UUID.fromString(runId)
        );
    }

    private Optional<PromptPolicySnapshot> findPromptSnapshot(String runId) {
        return queryOptional(
            """
                SELECT base_system_prompt, system_instructions_text, safety_instructions_text,
                       context_instructions_text, user_instructions_text, temporary_instruction_text,
                       answer_mode_block_text, grounding_block_text, grounding_rules_applied,
                       resolved_system_prompt, messages_jsonb, prompt_hash, instruction_trace_jsonb,
                       knowledge_scope_resolved_jsonb
                FROM chat_run_prompt_snapshots
                WHERE run_id = ?
                LIMIT 1
                """,
            (resultSet, rowNum) -> new PromptPolicySnapshot(
                resultSet.getString("base_system_prompt"),
                resultSet.getString("system_instructions_text"),
                resultSet.getString("safety_instructions_text"),
                resultSet.getString("context_instructions_text"),
                resultSet.getString("user_instructions_text"),
                resultSet.getString("temporary_instruction_text"),
                resultSet.getString("answer_mode_block_text"),
                resultSet.getString("grounding_block_text"),
                resultSet.getString("resolved_system_prompt"),
                readJson(resultSet.getString("messages_jsonb"), MESSAGES_TYPE),
                resultSet.getString("prompt_hash"),
                readJson(resultSet.getString("instruction_trace_jsonb"), INSTRUCTION_TRACE_TYPE),
                readJson(resultSet.getString("knowledge_scope_resolved_jsonb"), KnowledgeScopeResolved.class),
                resultSet.getBoolean("grounding_rules_applied")
            ),
            UUID.fromString(runId)
        );
    }

    private Optional<RetrievalSummaryTrace> findRetrievalSummary(String runId) {
        return queryOptional(
            """
                SELECT retrieval_status, trace_jsonb, debug_jsonb, lexical_provider, relevance_profile,
                       embedding_model, chunk_profile, query_hints_jsonb, manual_filters_jsonb,
                       effective_filters_jsonb, rollout_flags_jsonb, applied_capabilities_jsonb
                FROM chat_run_retrieval_summaries
                WHERE run_id = ?
                LIMIT 1
                """,
            (resultSet, rowNum) -> new RetrievalSummaryTrace(
                resultSet.getString("retrieval_status"),
                readJson(resultSet.getString("trace_jsonb"), RetrievalTrace.class),
                readJson(resultSet.getString("debug_jsonb"), RetrievalDebug.class),
                resultSet.getString("lexical_provider"),
                resultSet.getString("relevance_profile"),
                resultSet.getString("embedding_model"),
                resultSet.getString("chunk_profile"),
                readTree(resultSet.getString("query_hints_jsonb")),
                readTree(resultSet.getString("manual_filters_jsonb")),
                readTree(resultSet.getString("effective_filters_jsonb")),
                readTree(resultSet.getString("rollout_flags_jsonb")),
                readTree(resultSet.getString("applied_capabilities_jsonb"))
            ),
            UUID.fromString(runId)
        );
    }

    private List<LlmCallTrace> findLlmCalls(String runId) {
        return read(() -> jdbcTemplate.query(
            """
                SELECT id, provider, model, request_messages_jsonb, raw_response_text, parsed_answer_text,
                       prompt_tokens, completion_tokens, total_tokens, latency_ms, retry_count,
                       timeout_seconds, finish_reason, error_code, error_message, created_at
                FROM chat_run_llm_calls
                WHERE run_id = ?
                ORDER BY created_at ASC
                """,
            (resultSet, rowNum) -> new LlmCallTrace(
                resultSet.getObject("id").toString(),
                resultSet.getString("provider"),
                resultSet.getString("model"),
                readJson(resultSet.getString("request_messages_jsonb"), MESSAGES_TYPE),
                resultSet.getString("raw_response_text"),
                resultSet.getString("parsed_answer_text"),
                resultSet.getObject("prompt_tokens", Integer.class),
                resultSet.getObject("completion_tokens", Integer.class),
                resultSet.getObject("total_tokens", Integer.class),
                resultSet.getObject("latency_ms", Long.class),
                resultSet.getInt("retry_count"),
                resultSet.getObject("timeout_seconds", Integer.class),
                resultSet.getString("finish_reason"),
                resultSet.getString("error_code"),
                resultSet.getString("error_message"),
                toInstant(resultSet.getTimestamp("created_at"))
            ),
            UUID.fromString(runId)
        ));
    }

    private Optional<ChatRunOutputTrace> findOutput(String runId) {
        return queryOptional(
            """
                SELECT raw_model_answer, final_user_answer, sources_jsonb, postprocess_jsonb,
                       abstained, strict_sources_blocked_answer
                FROM chat_run_outputs
                WHERE run_id = ?
                LIMIT 1
                """,
            (resultSet, rowNum) -> new ChatRunOutputTrace(
                resultSet.getString("raw_model_answer"),
                resultSet.getString("final_user_answer"),
                readJson(resultSet.getString("sources_jsonb"), SOURCES_TYPE),
                readTree(resultSet.getString("postprocess_jsonb")),
                resultSet.getObject("abstained", Boolean.class),
                resultSet.getObject("strict_sources_blocked_answer", Boolean.class)
            ),
            UUID.fromString(runId)
        );
    }

    private List<ChatRunEventTrace> findEvents(String runId) {
        return read(() -> jdbcTemplate.query(
            """
                SELECT id, event_type, event_payload_jsonb, created_at
                FROM chat_run_events
                WHERE run_id = ?
                ORDER BY created_at ASC
                """,
            (resultSet, rowNum) -> new ChatRunEventTrace(
                resultSet.getObject("id").toString(),
                resultSet.getString("event_type"),
                readTree(resultSet.getString("event_payload_jsonb")),
                toInstant(resultSet.getTimestamp("created_at"))
            ),
            UUID.fromString(runId)
        ));
    }

    private void markStatus(String runId, String status) {
        write(() -> jdbcTemplate.update(
            """
                UPDATE chat_run_headers
                SET status = ?
                WHERE id = ?
                  AND status <> 'FAILED'
                  AND status <> 'COMPLETED'
                """,
            status,
            UUID.fromString(runId)
        ));
    }

    private <T> Optional<T> queryOptional(String sql, RowMapper<T> mapper, Object... args) {
        return read(() -> jdbcTemplate.query(sql, mapper, args).stream().findFirst());
    }

    private void write(WriteOperation operation) {
        try {
            operation.execute();
        } catch (DataAccessException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "chat_trace.storage_write_failed",
                "Unable to persist chat run trace in PostgreSQL",
                exception
            );
        }
    }

    private <T> T read(ReadOperation<T> operation) {
        try {
            return operation.execute();
        } catch (DataAccessException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "chat_trace.storage_read_failed",
                "Unable to load chat run trace from PostgreSQL",
                exception
            );
        }
    }

    private String writeJson(Object value) {
        try {
            return JSON_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "chat_trace.storage_encode_failed",
                "Unable to encode chat trace JSON",
                exception
            );
        }
    }

    private JsonNode readTree(String rawJson) {
        if (rawJson == null) {
            return null;
        }
        try {
            return JSON_MAPPER.readTree(rawJson);
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "chat_trace.storage_decode_failed",
                "Unable to decode chat trace JSON",
                exception
            );
        }
    }

    private <T> T readJson(String rawJson, Class<T> type) {
        if (rawJson == null) {
            return null;
        }
        try {
            return JSON_MAPPER.readValue(rawJson, type);
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "chat_trace.storage_decode_failed",
                "Unable to decode chat trace JSON",
                exception
            );
        }
    }

    private <T> T readJson(String rawJson, TypeReference<T> type) {
        if (rawJson == null) {
            return null;
        }
        try {
            return JSON_MAPPER.readValue(rawJson, type);
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "chat_trace.storage_decode_failed",
                "Unable to decode chat trace JSON",
                exception
            );
        }
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? Instant.now() : timestamp.toInstant();
    }

    private static Instant toInstantOrNull(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static AnswerMode answerModeOf(String rawValue) {
        return rawValue == null ? null : AnswerMode.fromValue(rawValue);
    }

    private String clip(String value, int limit) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= limit) {
            return normalized;
        }
        return normalized.substring(0, limit) + "...";
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record HeaderRow(
        String id,
        ChatMode mode,
        String status,
        String requestedModel,
        String resolvedModel,
        AnswerMode requestedAnswerMode,
        AnswerMode appliedAnswerMode,
        String contextStatus,
        Instant createdAt,
        Instant completedAt,
        Instant failedAt,
        Long latencyMsTotal,
        String failureStage,
        String failureCode,
        String failureMessage
    ) {
    }

    @FunctionalInterface
    private interface WriteOperation {
        void execute();
    }

    @FunctionalInterface
    private interface ReadOperation<T> {
        T execute();
    }
}
