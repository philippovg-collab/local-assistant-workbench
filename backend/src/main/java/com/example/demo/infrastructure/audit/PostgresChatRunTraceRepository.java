package com.example.demo.infrastructure.audit;

import static com.example.demo.infrastructure.audit.PostgresChatRunTraceValueMapper.answerModeOf;
import static com.example.demo.infrastructure.audit.PostgresChatRunTraceValueMapper.clip;
import static com.example.demo.infrastructure.audit.PostgresChatRunTraceValueMapper.firstNonBlank;
import static com.example.demo.infrastructure.audit.PostgresChatRunTraceValueMapper.normalizeOptionalWorkspaceKey;
import static com.example.demo.infrastructure.audit.PostgresChatRunTraceValueMapper.nullToEmpty;
import static com.example.demo.infrastructure.audit.PostgresChatRunTraceValueMapper.readInstructionTrace;
import static com.example.demo.infrastructure.audit.PostgresChatRunTraceValueMapper.readJson;
import static com.example.demo.infrastructure.audit.PostgresChatRunTraceValueMapper.readMessages;
import static com.example.demo.infrastructure.audit.PostgresChatRunTraceValueMapper.readSources;
import static com.example.demo.infrastructure.audit.PostgresChatRunTraceValueMapper.readTree;
import static com.example.demo.infrastructure.audit.PostgresChatRunTraceValueMapper.toInstant;
import static com.example.demo.infrastructure.audit.PostgresChatRunTraceValueMapper.toInstantOrNull;
import static com.example.demo.infrastructure.audit.PostgresChatRunTraceValueMapper.writeJson;

import com.example.demo.error.ErrorType;
import com.example.demo.error.StorageException;
import com.example.demo.model.AnswerMode;
import com.example.demo.model.ChatAuditRunDetail;
import com.example.demo.model.ChatAuditRunSummary;
import com.example.demo.model.ChatExecutionResponse;
import com.example.demo.model.ChatExecutionRequest;
import com.example.demo.model.ChatMode;
import com.example.demo.model.ChatRunEventTrace;
import com.example.demo.model.ChatRunMessage;
import com.example.demo.model.ChatRunOutputTrace;
import com.example.demo.model.ChatRunRequestSnapshot;
import com.example.demo.model.ChatRunTraceDetail;
import com.example.demo.model.ChatSource;
import com.example.demo.model.KnowledgeScopeResolved;
import com.example.demo.model.LlmCallTrace;
import com.example.demo.model.PromptPolicySnapshot;
import com.example.demo.model.RetrievalDebug;
import com.example.demo.model.RetrievalSummaryTrace;
import com.example.demo.model.RetrievalTrace;
import com.example.demo.service.audit.ChatRunHeaderStatus;
import com.example.demo.service.audit.ChatRunLeaseToken;
import com.example.demo.service.audit.port.ChatRunTraceRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class PostgresChatRunTraceRepository implements ChatRunTraceRepository {

    private static final RowMapper<PostgresChatRunHeaderRow> HEADER_ROW_MAPPER = (resultSet, rowNum) -> new PostgresChatRunHeaderRow(
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
    private final TransactionTemplate transactionTemplate;

    @Autowired
    public PostgresChatRunTraceRepository(
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public PostgresChatRunTraceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = null;
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

    public boolean saveRequestSnapshot(
        String runId,
        ChatExecutionRequest request,
        ChatExecutionRequest normalizedRequest
    ) {
        return saveRequestSnapshot(runId, request, normalizedRequest, null);
    }

    public boolean saveRequestSnapshot(
        String runId,
        ChatExecutionRequest request,
        ChatExecutionRequest normalizedRequest,
        ChatRunLeaseToken leaseToken
    ) {
        int[] updatedCount = {0};
        write(() -> updatedCount[0] = jdbcTemplate.update(
            mutableRunCte(leaseToken) + """
                INSERT INTO chat_run_request_snapshots (
                    run_id,
                    request_jsonb,
                    normalized_request_jsonb,
                    prompt,
                    knowledge_scope_jsonb,
                    retrieval_filters_jsonb
                )
                SELECT mutable_run.id, ?::jsonb, ?::jsonb, ?, ?::jsonb, ?::jsonb
                FROM mutable_run
                ON CONFLICT (run_id) DO UPDATE
                SET request_jsonb = EXCLUDED.request_jsonb,
                    normalized_request_jsonb = EXCLUDED.normalized_request_jsonb,
                    prompt = EXCLUDED.prompt,
                    knowledge_scope_jsonb = EXCLUDED.knowledge_scope_jsonb,
                    retrieval_filters_jsonb = EXCLUDED.retrieval_filters_jsonb
                """,
            mutableRunArgs(
                runId,
                leaseToken,
                writeJson(request),
                writeJson(normalizedRequest),
                normalizedRequest.prompt(),
                writeJson(normalizedRequest.knowledgeScope()),
                writeJson(normalizedRequest.retrievalFilters())
            )
        ));
        return updatedCount[0] > 0;
    }

    public boolean savePromptSnapshot(String runId, PromptPolicySnapshot snapshot) {
        return savePromptSnapshot(runId, snapshot, null);
    }

    public boolean savePromptSnapshot(String runId, PromptPolicySnapshot snapshot, ChatRunLeaseToken leaseToken) {
        PromptPolicySnapshot safeSnapshot = snapshot == null
            ? new PromptPolicySnapshot(null, null, null, null, null, null, null, null, null, List.of(), null, List.of(), null, false)
            : snapshot;
        int[] updatedCount = {0};
        write(() -> {
            updatedCount[0] = jdbcTemplate.update(
                mutableRunCte(leaseToken) + """
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
                    )
                    SELECT mutable_run.id, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?::jsonb, ?::jsonb, ?
                    FROM mutable_run
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
                mutableRunArgs(
                    runId,
                    leaseToken,
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
                )
            );
        });
        return updatedCount[0] > 0;
    }

    public boolean savePromptMessages(String runId, List<ChatRunMessage> messages) {
        return savePromptMessages(runId, messages, null);
    }

    public boolean savePromptMessages(String runId, List<ChatRunMessage> messages, ChatRunLeaseToken leaseToken) {
        int[] updatedCount = {0};
        write(() -> updatedCount[0] = jdbcTemplate.update(
            mutableRunCte(leaseToken) + """
                UPDATE chat_run_prompt_snapshots
                SET messages_jsonb = ?::jsonb,
                    updated_at = ?
                WHERE run_id = (SELECT id FROM mutable_run)
                """,
            mutableRunArgs(
                runId,
                leaseToken,
                writeJson(messages == null ? List.of() : messages),
                Timestamp.from(Instant.now())
            )
        ));
        return updatedCount[0] > 0;
    }

    public boolean saveRetrievalSummary(
        String runId,
        String retrievalStatus,
        RetrievalTrace trace,
        RetrievalDebug debug
    ) {
        return saveRetrievalSummary(runId, retrievalStatus, trace, debug, null);
    }

    public boolean saveRetrievalSummary(
        String runId,
        String retrievalStatus,
        RetrievalTrace trace,
        RetrievalDebug debug,
        ChatRunLeaseToken leaseToken
    ) {
        int[] updatedCount = {0};
        write(() -> {
            updatedCount[0] = jdbcTemplate.update(
                mutableRunCte(leaseToken) + """
                    INSERT INTO chat_run_retrieval_summaries (
                        run_id,
                        retrieval_status,
                        trace_jsonb,
                        debug_jsonb,
                        lexical_provider,
                        relevance_profile,
                        embedding_model,
                        chunk_profile,
                        query_hints_jsonb,
                        manual_filters_jsonb,
                        effective_filters_jsonb,
                        rollout_flags_jsonb,
                        applied_capabilities_jsonb
                    )
                    SELECT mutable_run.id, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb
                    FROM mutable_run
                    ON CONFLICT (run_id) DO UPDATE
                    SET retrieval_status = EXCLUDED.retrieval_status,
                        trace_jsonb = EXCLUDED.trace_jsonb,
                        debug_jsonb = EXCLUDED.debug_jsonb,
                        lexical_provider = EXCLUDED.lexical_provider,
                        relevance_profile = EXCLUDED.relevance_profile,
                        embedding_model = EXCLUDED.embedding_model,
                        chunk_profile = EXCLUDED.chunk_profile,
                        query_hints_jsonb = EXCLUDED.query_hints_jsonb,
                        manual_filters_jsonb = EXCLUDED.manual_filters_jsonb,
                        effective_filters_jsonb = EXCLUDED.effective_filters_jsonb,
                        rollout_flags_jsonb = EXCLUDED.rollout_flags_jsonb,
                        applied_capabilities_jsonb = EXCLUDED.applied_capabilities_jsonb
                    """,
                mutableRunArgs(
                    runId,
                    leaseToken,
                    retrievalStatus,
                    writeJson(trace),
                    writeJson(debug),
                    debug == null ? null : debug.lexicalProvider(),
                    debug == null ? null : debug.relevanceProfile(),
                    debug == null ? null : debug.embeddingModel(),
                    debug == null ? null : debug.chunkProfile(),
                    writeJson(debug == null ? null : debug.queryHints()),
                    writeJson(debug == null ? null : debug.manualFilters()),
                    writeJson(debug == null ? null : debug.effectiveFilters()),
                    writeJson(debug == null ? null : debug.activeRolloutFlags()),
                    writeJson(debug == null ? List.of() : debug.appliedCapabilities())
                )
            );
        });
        return updatedCount[0] > 0;
    }

    public boolean insertLlmCall(String runId, LlmCallTrace call) {
        return insertLlmCall(runId, call, null);
    }

    public boolean insertLlmCall(String runId, LlmCallTrace call, ChatRunLeaseToken leaseToken) {
        int[] updatedCount = {0};
        write(() -> {
            updatedCount[0] = jdbcTemplate.update(
                mutableRunCte(leaseToken) + """
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
                    )
                    SELECT ?, mutable_run.id, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
                    FROM mutable_run
                    """,
                mutableRunArgs(
                    runId,
                    leaseToken,
                    UUID.fromString(call.id()),
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
                )
            );
        });
        return updatedCount[0] > 0;
    }

    public boolean saveOutput(String runId, ChatRunOutputTrace output) {
        return saveOutput(runId, output, null);
    }

    public boolean saveOutput(String runId, ChatRunOutputTrace output, ChatRunLeaseToken leaseToken) {
        ChatRunOutputTrace safeOutput = output == null
            ? new ChatRunOutputTrace(null, null, List.of(), null, null, null)
            : output;
        int[] updatedCount = {0};
        write(() -> {
            updatedCount[0] = jdbcTemplate.update(
                mutableRunCte(leaseToken) + """
                    INSERT INTO chat_run_outputs (
                        run_id,
                        raw_model_answer,
                        final_user_answer,
                        sources_jsonb,
                        postprocess_jsonb,
                        abstained,
                        strict_sources_blocked_answer
                    )
                    SELECT mutable_run.id, ?, ?, ?::jsonb, ?::jsonb, ?, ?
                    FROM mutable_run
                    ON CONFLICT (run_id) DO UPDATE
                    SET raw_model_answer = EXCLUDED.raw_model_answer,
                        final_user_answer = EXCLUDED.final_user_answer,
                        sources_jsonb = EXCLUDED.sources_jsonb,
                        postprocess_jsonb = EXCLUDED.postprocess_jsonb,
                        abstained = EXCLUDED.abstained,
                        strict_sources_blocked_answer = EXCLUDED.strict_sources_blocked_answer
                    """,
                mutableRunArgs(
                    runId,
                    leaseToken,
                    safeOutput.rawModelAnswer(),
                    safeOutput.finalUserAnswer(),
                    writeJson(safeOutput.sources()),
                    writeJson(safeOutput.postprocess()),
                    safeOutput.abstained(),
                    safeOutput.strictSourcesBlockedAnswer()
                )
            );
        });
        return updatedCount[0] > 0;
    }

    public boolean transitionStage(String runId, String status, ChatRunLeaseToken leaseToken) {
        if (!matchesRun(runId, leaseToken)) {
            return false;
        }
        int[] updatedCount = {0};
        write(() -> updatedCount[0] = updateHeaderStatus(runId, status, leaseToken));
        return updatedCount[0] > 0;
    }

    public boolean completeRun(
        String runId,
        String resolvedModel,
        AnswerMode appliedAnswerMode,
        String contextStatus,
        Instant completedAt,
        long latencyMsTotal
    ) {
        return completeRun(runId, resolvedModel, appliedAnswerMode, contextStatus, completedAt, latencyMsTotal, null);
    }

    public boolean completeRun(
        String runId,
        String resolvedModel,
        AnswerMode appliedAnswerMode,
        String contextStatus,
        Instant completedAt,
        long latencyMsTotal,
        ChatRunLeaseToken leaseToken
    ) {
        if (!matchesRun(runId, leaseToken)) {
            return false;
        }
        int[] updatedCount = {0};
        write(() -> updatedCount[0] = jdbcTemplate.update(
            completeRunSql(leaseToken),
            completeRunArgs(
                runId,
                resolvedModel,
                appliedAnswerMode,
                contextStatus,
                completedAt,
                latencyMsTotal,
                leaseToken
            )
        ));
        return updatedCount[0] > 0;
    }

    public boolean completeRunWithResult(
        String runId,
        String resolvedModel,
        AnswerMode appliedAnswerMode,
        String contextStatus,
        Instant completedAt,
        long latencyMsTotal,
        ChatExecutionResponse response
    ) {
        return completeRunWithResult(
            runId,
            resolvedModel,
            appliedAnswerMode,
            contextStatus,
            completedAt,
            latencyMsTotal,
            response,
            null
        );
    }

    public boolean completeRunWithResult(
        String runId,
        String resolvedModel,
        AnswerMode appliedAnswerMode,
        String contextStatus,
        Instant completedAt,
        long latencyMsTotal,
        ChatExecutionResponse response,
        ChatRunLeaseToken leaseToken
    ) {
        if (!matchesRun(runId, leaseToken)) {
            return false;
        }
        Instant effectiveCompletedAt = completedAt == null ? Instant.now() : completedAt;
        int[] updatedCount = {0};
        writeTransaction(() -> {
            UUID runUuid = UUID.fromString(runId);
            updatedCount[0] = jdbcTemplate.update(
                completeRunSql(leaseToken),
                completeRunArgs(
                    runId,
                    resolvedModel,
                    appliedAnswerMode,
                    contextStatus,
                    effectiveCompletedAt,
                    latencyMsTotal,
                    leaseToken
                )
            );
            if (updatedCount[0] > 0) {
                insertResultIfAbsent(runUuid, response, effectiveCompletedAt, "LIVE_EXECUTION");
                insertEvent(runUuid, "COMPLETED", Map.of(), effectiveCompletedAt);
            }
        });
        return updatedCount[0] > 0;
    }

    public Optional<ChatExecutionResponse> findResult(String runId) {
        return queryOptional(
            """
                SELECT response_jsonb
                FROM chat_run_results
                WHERE run_id = ?
                LIMIT 1
                """,
            (resultSet, rowNum) -> readJson(resultSet.getString("response_jsonb"), ChatExecutionResponse.class, "chat_run_results.response_jsonb"),
            UUID.fromString(runId)
        );
    }

    public Optional<ChatRunHeaderStatus> findHeaderStatus(String runId) {
        return queryOptional(
            """
                SELECT id, status, created_at, completed_at, failed_at,
                       latency_ms_total, failure_stage, failure_code, failure_message
                FROM chat_run_headers
                WHERE id = ?
                LIMIT 1
                """,
            (resultSet, rowNum) -> new ChatRunHeaderStatus(
                resultSet.getObject("id").toString(),
                resultSet.getString("status"),
                toInstant(resultSet.getTimestamp("created_at")),
                toInstantOrNull(resultSet.getTimestamp("completed_at")),
                toInstantOrNull(resultSet.getTimestamp("failed_at")),
                resultSet.getObject("latency_ms_total", Long.class),
                resultSet.getString("failure_stage"),
                resultSet.getString("failure_code"),
                resultSet.getString("failure_message")
            ),
            UUID.fromString(runId)
        );
    }

    public boolean insertResultIfAbsent(
        String runId,
        ChatExecutionResponse response,
        Instant completedAt,
        String source
    ) {
        Instant effectiveCompletedAt = completedAt == null ? Instant.now() : completedAt;
        int[] insertedCount = {0};
        write(() -> insertedCount[0] = insertResultIfAbsent(
            UUID.fromString(runId),
            response,
            effectiveCompletedAt,
            source
        ));
        return insertedCount[0] > 0;
    }

    public boolean failRun(
        String runId,
        String failureStage,
        String failureCode,
        String failureMessage,
        Instant failedAt,
        long latencyMsTotal
    ) {
        return failRun(runId, failureStage, failureCode, failureMessage, failedAt, latencyMsTotal, null);
    }

    public boolean failRun(
        String runId,
        String failureStage,
        String failureCode,
        String failureMessage,
        Instant failedAt,
        long latencyMsTotal,
        ChatRunLeaseToken leaseToken
    ) {
        if (!matchesRun(runId, leaseToken)) {
            return false;
        }
        int[] updatedCount = {0};
        write(() -> updatedCount[0] = jdbcTemplate.update(
            failRunSql(leaseToken),
            failRunArgs(
                runId,
                failureStage,
                failureCode,
                failureMessage,
                failedAt,
                latencyMsTotal,
                leaseToken
            )
        ));
        return updatedCount[0] > 0;
    }

    public boolean cancelRun(String runId, Instant cancelledAt, long latencyMsTotal) {
        int[] updatedCount = {0};
        write(() -> updatedCount[0] = jdbcTemplate.update(
            """
                UPDATE chat_run_headers
                SET status = 'CANCELLED',
                    failed_at = ?,
                    latency_ms_total = ?,
                    failure_stage = 'CANCEL',
                    failure_code = 'chat_run.cancelled',
                    failure_message = 'Chat run was cancelled.'
                WHERE id = ?
                  AND status <> 'COMPLETED'
                  AND status <> 'FAILED'
                  AND status <> 'CANCELLED'
                """,
            Timestamp.from(cancelledAt),
            latencyMsTotal,
            UUID.fromString(runId)
        ));
        return updatedCount[0] > 0;
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

    public boolean insertEventIfRunMutable(String runId, String eventType, Object payload, Instant createdAt) {
        return insertEventIfRunMutable(runId, eventType, payload, createdAt, null);
    }

    public boolean insertEventIfRunMutable(
        String runId,
        String eventType,
        Object payload,
        Instant createdAt,
        ChatRunLeaseToken leaseToken
    ) {
        int[] insertedCount = {0};
        write(() -> insertedCount[0] = jdbcTemplate.update(
            mutableRunCte(leaseToken) + """
                INSERT INTO chat_run_events (
                    id,
                    run_id,
                    event_type,
                    event_payload_jsonb,
                    created_at
                )
                SELECT ?, mutable_run.id, ?, ?::jsonb, ?
                FROM mutable_run
                """,
            mutableRunArgs(
                runId,
                leaseToken,
                UUID.randomUUID(),
                eventType,
                writeJson(payload),
                Timestamp.from(createdAt)
            )
        ));
        return insertedCount[0] > 0;
    }

    public List<ChatAuditRunSummary> findRunSummaries(int limit) {
        return findRunSummaries(limit, null);
    }

    public List<ChatAuditRunSummary> findRunSummaries(int limit, String workspaceKey) {
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
                    o.final_user_answer,
                    p.knowledge_scope_resolved_jsonb ->> 'workspaceKey' AS workspace_key
                FROM chat_run_headers h
                LEFT JOIN chat_run_request_snapshots r ON r.run_id = h.id
                LEFT JOIN chat_run_prompt_snapshots p ON p.run_id = h.id
                LEFT JOIN chat_run_outputs o ON o.run_id = h.id
                WHERE (CAST(? AS text) IS NULL OR LOWER(COALESCE(p.knowledge_scope_resolved_jsonb ->> 'workspaceKey', '')) = ?)
                ORDER BY h.created_at DESC
                LIMIT ?
                """,
            (resultSet, rowNum) -> {
                PostgresChatRunHeaderRow header = HEADER_ROW_MAPPER.mapRow(resultSet, rowNum);
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
                    header.latencyMsTotal(),
                    resultSet.getString("workspace_key")
                );
            },
            normalizeOptionalWorkspaceKey(workspaceKey),
            normalizeOptionalWorkspaceKey(workspaceKey),
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
        Optional<PostgresChatRunHeaderRow> header = findHeader(normalizedRunId);
        if (header.isEmpty()) {
            return Optional.empty();
        }
        PostgresChatRunHeaderRow row = header.get();
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

    public int deleteRunsOlderThan(Instant cutoff) {
        int[] deletedCount = {0};
        write(() -> deletedCount[0] = jdbcTemplate.update(
            "DELETE FROM chat_run_headers WHERE created_at < ?",
            Timestamp.from(cutoff)
        ));
        return deletedCount[0];
    }

    private Optional<PostgresChatRunHeaderRow> findHeader(String runId) {
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
                readTree(resultSet.getString("request_jsonb"), "chat_run_request_snapshots.request_jsonb"),
                readTree(resultSet.getString("normalized_request_jsonb"), "chat_run_request_snapshots.normalized_request_jsonb"),
                resultSet.getString("prompt"),
                readTree(resultSet.getString("knowledge_scope_jsonb"), "chat_run_request_snapshots.knowledge_scope_jsonb"),
                readTree(resultSet.getString("retrieval_filters_jsonb"), "chat_run_request_snapshots.retrieval_filters_jsonb")
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
                readMessages(resultSet.getString("messages_jsonb"), "chat_run_prompt_snapshots.messages_jsonb"),
                resultSet.getString("prompt_hash"),
                readInstructionTrace(resultSet.getString("instruction_trace_jsonb"), "chat_run_prompt_snapshots.instruction_trace_jsonb"),
                readJson(resultSet.getString("knowledge_scope_resolved_jsonb"), KnowledgeScopeResolved.class, "chat_run_prompt_snapshots.knowledge_scope_resolved_jsonb"),
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
                readJson(resultSet.getString("trace_jsonb"), RetrievalTrace.class, "chat_run_retrieval_summaries.trace_jsonb"),
                readJson(resultSet.getString("debug_jsonb"), RetrievalDebug.class, "chat_run_retrieval_summaries.debug_jsonb"),
                resultSet.getString("lexical_provider"),
                resultSet.getString("relevance_profile"),
                resultSet.getString("embedding_model"),
                resultSet.getString("chunk_profile"),
                readTree(resultSet.getString("query_hints_jsonb"), "chat_run_retrieval_summaries.query_hints_jsonb"),
                readTree(resultSet.getString("manual_filters_jsonb"), "chat_run_retrieval_summaries.manual_filters_jsonb"),
                readTree(resultSet.getString("effective_filters_jsonb"), "chat_run_retrieval_summaries.effective_filters_jsonb"),
                readTree(resultSet.getString("rollout_flags_jsonb"), "chat_run_retrieval_summaries.rollout_flags_jsonb"),
                readTree(resultSet.getString("applied_capabilities_jsonb"), "chat_run_retrieval_summaries.applied_capabilities_jsonb")
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
                readMessages(resultSet.getString("request_messages_jsonb"), "chat_run_llm_calls.request_messages_jsonb"),
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
                readSources(resultSet.getString("sources_jsonb"), "chat_run_outputs.sources_jsonb"),
                readTree(resultSet.getString("postprocess_jsonb"), "chat_run_outputs.postprocess_jsonb"),
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
                readTree(resultSet.getString("event_payload_jsonb"), "chat_run_events.event_payload_jsonb"),
                toInstant(resultSet.getTimestamp("created_at"))
            ),
            UUID.fromString(runId)
        ));
    }

    private int updateHeaderStatus(String runId, String status, ChatRunLeaseToken leaseToken) {
        return jdbcTemplate.update(
            stageSql(leaseToken),
            stageArgs(runId, status, leaseToken)
        );
    }

    private String mutableRunCte(ChatRunLeaseToken leaseToken) {
        if (leaseToken == null) {
            return """
                WITH mutable_run AS (
                    SELECT id
                    FROM chat_run_headers
                    WHERE id = ?
                      AND status <> 'FAILED'
                      AND status <> 'COMPLETED'
                      AND status <> 'CANCELLED'
                    FOR UPDATE
                )
                """;
        }
        return """
            WITH mutable_run AS (
                SELECT h.id
                FROM chat_run_headers h
                WHERE h.id = ?
                  AND h.status <> 'FAILED'
                  AND h.status <> 'COMPLETED'
                  AND h.status <> 'CANCELLED'
                  AND EXISTS (
                      SELECT 1
                      FROM chat_run_queue q
                      WHERE q.run_id = h.id
                        AND q.delivery_state = 'IN_PROGRESS'
                        AND q.lease_owner = ?
                        AND q.attempt_count = ?
                  )
                FOR UPDATE
            )
            """;
    }

    private Object[] mutableRunArgs(String runId, ChatRunLeaseToken leaseToken, Object... args) {
        int prefixLength = leaseToken == null ? 1 : 3;
        Object[] values = new Object[prefixLength + args.length];
        values[0] = UUID.fromString(runId);
        if (leaseToken != null) {
            values[1] = leaseToken.leaseOwner();
            values[2] = leaseToken.attemptCount();
        }
        System.arraycopy(args, 0, values, prefixLength, args.length);
        return values;
    }

    private String stageSql(ChatRunLeaseToken leaseToken) {
        if (leaseToken == null) {
            return """
                UPDATE chat_run_headers
                SET status = ?
                WHERE id = ?
                  AND status <> 'FAILED'
                  AND status <> 'COMPLETED'
                  AND status <> 'CANCELLED'
                """;
        }
        return """
            UPDATE chat_run_headers
            SET status = ?
            WHERE id = ?
              AND status <> 'FAILED'
              AND status <> 'COMPLETED'
              AND status <> 'CANCELLED'
              AND EXISTS (
                  SELECT 1
                  FROM chat_run_queue q
                  WHERE q.run_id = chat_run_headers.id
                    AND q.delivery_state = 'IN_PROGRESS'
                    AND q.lease_owner = ?
                    AND q.attempt_count = ?
              )
            """;
    }

    private Object[] stageArgs(String runId, String status, ChatRunLeaseToken leaseToken) {
        if (leaseToken == null) {
            return new Object[] {status, UUID.fromString(runId)};
        }
        return new Object[] {
            status,
            UUID.fromString(runId),
            leaseToken.leaseOwner(),
            leaseToken.attemptCount()
        };
    }

    private String completeRunSql(ChatRunLeaseToken leaseToken) {
        if (leaseToken == null) {
            return """
                UPDATE chat_run_headers
                SET status = 'COMPLETED',
                    resolved_model = ?,
                    applied_answer_mode = ?,
                    context_status = ?,
                    completed_at = ?,
                    latency_ms_total = ?
                WHERE id = ?
                  AND status <> 'FAILED'
                  AND status <> 'COMPLETED'
                  AND status <> 'CANCELLED'
                """;
        }
        return """
            UPDATE chat_run_headers
            SET status = 'COMPLETED',
                resolved_model = ?,
                applied_answer_mode = ?,
                context_status = ?,
                completed_at = ?,
                latency_ms_total = ?
            WHERE id = ?
              AND status <> 'FAILED'
              AND status <> 'COMPLETED'
              AND status <> 'CANCELLED'
              AND EXISTS (
                  SELECT 1
                  FROM chat_run_queue q
                  WHERE q.run_id = chat_run_headers.id
                    AND q.delivery_state = 'IN_PROGRESS'
                    AND q.lease_owner = ?
                    AND q.attempt_count = ?
              )
            """;
    }

    private Object[] completeRunArgs(
        String runId,
        String resolvedModel,
        AnswerMode appliedAnswerMode,
        String contextStatus,
        Instant completedAt,
        long latencyMsTotal,
        ChatRunLeaseToken leaseToken
    ) {
        Instant effectiveCompletedAt = completedAt == null ? Instant.now() : completedAt;
        Object[] baseArgs = {
            resolvedModel,
            appliedAnswerMode == null ? null : appliedAnswerMode.value(),
            contextStatus,
            Timestamp.from(effectiveCompletedAt),
            latencyMsTotal,
            UUID.fromString(runId)
        };
        if (leaseToken == null) {
            return baseArgs;
        }
        return new Object[] {
            baseArgs[0],
            baseArgs[1],
            baseArgs[2],
            baseArgs[3],
            baseArgs[4],
            baseArgs[5],
            leaseToken.leaseOwner(),
            leaseToken.attemptCount()
        };
    }

    private String failRunSql(ChatRunLeaseToken leaseToken) {
        if (leaseToken == null) {
            return """
                UPDATE chat_run_headers
                SET status = 'FAILED',
                    failed_at = ?,
                    latency_ms_total = ?,
                    failure_stage = ?,
                    failure_code = ?,
                    failure_message = ?
                WHERE id = ?
                  AND status <> 'COMPLETED'
                  AND status <> 'FAILED'
                  AND status <> 'CANCELLED'
                """;
        }
        return """
            UPDATE chat_run_headers
            SET status = 'FAILED',
                failed_at = ?,
                latency_ms_total = ?,
                failure_stage = ?,
                failure_code = ?,
                failure_message = ?
            WHERE id = ?
              AND status <> 'COMPLETED'
              AND status <> 'FAILED'
              AND status <> 'CANCELLED'
              AND EXISTS (
                  SELECT 1
                  FROM chat_run_queue q
                  WHERE q.run_id = chat_run_headers.id
                    AND q.delivery_state = 'IN_PROGRESS'
                    AND q.lease_owner = ?
                    AND q.attempt_count = ?
              )
            """;
    }

    private Object[] failRunArgs(
        String runId,
        String failureStage,
        String failureCode,
        String failureMessage,
        Instant failedAt,
        long latencyMsTotal,
        ChatRunLeaseToken leaseToken
    ) {
        Instant effectiveFailedAt = failedAt == null ? Instant.now() : failedAt;
        Object[] baseArgs = {
            Timestamp.from(effectiveFailedAt),
            latencyMsTotal,
            failureStage,
            failureCode,
            failureMessage,
            UUID.fromString(runId)
        };
        if (leaseToken == null) {
            return baseArgs;
        }
        return new Object[] {
            baseArgs[0],
            baseArgs[1],
            baseArgs[2],
            baseArgs[3],
            baseArgs[4],
            baseArgs[5],
            leaseToken.leaseOwner(),
            leaseToken.attemptCount()
        };
    }

    private boolean matchesRun(String runId, ChatRunLeaseToken leaseToken) {
        return leaseToken == null
            || (runId != null
                && runId.equals(leaseToken.runId())
                && leaseToken.leaseOwner() != null
                && !leaseToken.leaseOwner().isBlank());
    }

    private int insertResultIfAbsent(
        UUID runId,
        ChatExecutionResponse response,
        Instant completedAt,
        String source
    ) {
        return jdbcTemplate.update(
            """
                INSERT INTO chat_run_results (
                    run_id,
                    response_jsonb,
                    response_schema_version,
                    source,
                    completed_at
                ) VALUES (?, ?::jsonb, 'chat_execution_response.v1', ?, ?)
                ON CONFLICT (run_id) DO NOTHING
                """,
            runId,
            writeJson(response),
            source,
            Timestamp.from(completedAt)
        );
    }

    private void insertEvent(UUID runId, String eventType, Object payload, Instant createdAt) {
        jdbcTemplate.update(
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
            runId,
            eventType,
            writeJson(payload),
            Timestamp.from(createdAt)
        );
    }

    private <T> Optional<T> queryOptional(String sql, RowMapper<T> mapper, Object... args) {
        return read(() -> jdbcTemplate.query(sql, mapper, args).stream().findFirst());
    }

    private void writeTransaction(WriteOperation operation) {
        try {
            if (transactionTemplate == null) {
                operation.execute();
                return;
            }
            transactionTemplate.execute(status -> {
                operation.execute();
                return null;
            });
        } catch (DataAccessException exception) {
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                "chat_trace.storage_write_failed",
                "Unable to persist chat run trace in PostgreSQL",
                exception
            );
        }
    }

    private void write(WriteOperation operation) {
        try {
            operation.execute();
        } catch (DataAccessException exception) {
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
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
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                "chat_trace.storage_read_failed",
                "Unable to load chat run trace from PostgreSQL",
                exception
            );
        }
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
