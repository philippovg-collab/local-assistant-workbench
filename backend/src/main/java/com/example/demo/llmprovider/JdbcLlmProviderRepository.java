package com.example.demo.llmprovider;

import com.example.demo.error.ApplicationException;
import com.example.demo.error.ErrorType;
import com.example.demo.error.StorageException;
import com.example.demo.model.LlmProviderPurpose;
import com.example.demo.model.LlmProviderStatus;
import com.example.demo.model.LlmProviderType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class JdbcLlmProviderRepository implements LlmProviderRepository {

    private static final String COLUMNS = """
        id,
        name,
        provider_type,
        purpose,
        base_url,
        api_key_ciphertext,
        auth_header_name,
        auth_scheme,
        chat_completions_path,
        models_path,
        embeddings_path,
        default_model,
        embedding_model,
        temperature,
        timeout_seconds,
        expected_embedding_dimension,
        active_chat,
        active_embedding,
        status,
        last_probe_at,
        last_successful_probe_at,
        last_error_code,
        last_error_message,
        created_at,
        updated_at
        """;

    private static final RowMapper<LlmProviderConfig> ROW_MAPPER = JdbcLlmProviderRepository::mapRow;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public JdbcLlmProviderRepository(JdbcTemplate jdbcTemplate, org.springframework.transaction.PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public List<LlmProviderConfig> findAll() {
        try {
            return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM llm_provider_configs ORDER BY updated_at DESC, name ASC",
                ROW_MAPPER
            );
        } catch (DataAccessException exception) {
            throw storage("llm_provider.storage_read_failed", "Unable to read LLM providers from PostgreSQL", exception);
        }
    }

    @Override
    public Optional<LlmProviderConfig> findById(UUID id) {
        try {
            List<LlmProviderConfig> rows = jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM llm_provider_configs WHERE id = ? LIMIT 1",
                ROW_MAPPER,
                id
            );
            return rows.stream().findFirst();
        } catch (DataAccessException exception) {
            throw storage("llm_provider.storage_read_failed", "Unable to read LLM provider from PostgreSQL", exception);
        }
    }

    @Override
    public Optional<LlmProviderConfig> findActiveChat() {
        return findActive("active_chat");
    }

    @Override
    public Optional<LlmProviderConfig> findActiveEmbedding() {
        return findActive("active_embedding");
    }

    @Override
    public LlmProviderConfig insert(LlmProviderConfig provider) {
        try {
            jdbcTemplate.update(
                """
                    INSERT INTO llm_provider_configs (
                        id,
                        name,
                        provider_type,
                        purpose,
                        base_url,
                        api_key_ciphertext,
                        auth_header_name,
                        auth_scheme,
                        chat_completions_path,
                        models_path,
                        embeddings_path,
                        default_model,
                        embedding_model,
                        temperature,
                        timeout_seconds,
                        expected_embedding_dimension,
                        active_chat,
                        active_embedding,
                        status,
                        last_probe_at,
                        last_successful_probe_at,
                        last_error_code,
                        last_error_message,
                        created_at,
                        updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                provider.id(),
                provider.name(),
                provider.providerType().name(),
                provider.purpose().name(),
                provider.baseUrl(),
                provider.apiKeyCiphertext(),
                provider.authHeaderName(),
                provider.authScheme(),
                provider.chatCompletionsPath(),
                provider.modelsPath(),
                provider.embeddingsPath(),
                provider.defaultModel(),
                provider.embeddingModel(),
                provider.temperature(),
                provider.timeoutSeconds(),
                provider.expectedEmbeddingDimension(),
                provider.activeChat(),
                provider.activeEmbedding(),
                provider.status().name(),
                toTimestamp(provider.lastProbeAt()),
                toTimestamp(provider.lastSuccessfulProbeAt()),
                provider.lastErrorCode(),
                provider.lastErrorMessage(),
                Timestamp.from(provider.createdAt()),
                Timestamp.from(provider.updatedAt())
            );
            return findById(provider.id()).orElse(provider);
        } catch (DataAccessException exception) {
            throw storage("llm_provider.storage_write_failed", "Unable to create LLM provider in PostgreSQL", exception);
        }
    }

    @Override
    public LlmProviderConfig update(LlmProviderConfig provider) {
        try {
            jdbcTemplate.update(
                """
                    UPDATE llm_provider_configs
                    SET name = ?,
                        provider_type = ?,
                        purpose = ?,
                        base_url = ?,
                        api_key_ciphertext = ?,
                        auth_header_name = ?,
                        auth_scheme = ?,
                        chat_completions_path = ?,
                        models_path = ?,
                        embeddings_path = ?,
                        default_model = ?,
                        embedding_model = ?,
                        temperature = ?,
                        timeout_seconds = ?,
                        expected_embedding_dimension = ?,
                        active_chat = ?,
                        active_embedding = ?,
                        status = ?,
                        last_probe_at = ?,
                        last_successful_probe_at = ?,
                        last_error_code = ?,
                        last_error_message = ?,
                        updated_at = ?
                    WHERE id = ?
                    """,
                provider.name(),
                provider.providerType().name(),
                provider.purpose().name(),
                provider.baseUrl(),
                provider.apiKeyCiphertext(),
                provider.authHeaderName(),
                provider.authScheme(),
                provider.chatCompletionsPath(),
                provider.modelsPath(),
                provider.embeddingsPath(),
                provider.defaultModel(),
                provider.embeddingModel(),
                provider.temperature(),
                provider.timeoutSeconds(),
                provider.expectedEmbeddingDimension(),
                provider.activeChat(),
                provider.activeEmbedding(),
                provider.status().name(),
                toTimestamp(provider.lastProbeAt()),
                toTimestamp(provider.lastSuccessfulProbeAt()),
                provider.lastErrorCode(),
                provider.lastErrorMessage(),
                Timestamp.from(provider.updatedAt()),
                provider.id()
            );
            return findById(provider.id()).orElse(provider);
        } catch (DataIntegrityViolationException exception) {
            throw activationConflict(exception);
        } catch (DataAccessException exception) {
            throw storage("llm_provider.storage_write_failed", "Unable to update LLM provider in PostgreSQL", exception);
        }
    }

    @Override
    public boolean deleteInactiveById(UUID id) {
        try {
            return jdbcTemplate.update(
                """
                    DELETE FROM llm_provider_configs
                    WHERE id = ?
                      AND active_chat = false
                      AND active_embedding = false
                    """,
                id
            ) > 0;
        } catch (DataAccessException exception) {
            throw storage("llm_provider.storage_write_failed", "Unable to delete LLM provider from PostgreSQL", exception);
        }
    }

    @Override
    public LlmProviderConfig activate(UUID id, LlmProviderPurpose purpose, Instant updatedAt) {
        try {
            return transactionTemplate.execute(status -> {
                if (purpose == LlmProviderPurpose.CHAT) {
                    jdbcTemplate.update(
                        "UPDATE llm_provider_configs SET active_chat = false, updated_at = ? WHERE active_chat = true",
                        Timestamp.from(updatedAt)
                    );
                    jdbcTemplate.update(
                        "UPDATE llm_provider_configs SET active_chat = true, updated_at = ? WHERE id = ?",
                        Timestamp.from(updatedAt),
                        id
                    );
                } else if (purpose == LlmProviderPurpose.EMBEDDING) {
                    jdbcTemplate.update(
                        "UPDATE llm_provider_configs SET active_embedding = false, updated_at = ? WHERE active_embedding = true",
                        Timestamp.from(updatedAt)
                    );
                    jdbcTemplate.update(
                        "UPDATE llm_provider_configs SET active_embedding = true, updated_at = ? WHERE id = ?",
                        Timestamp.from(updatedAt),
                        id
                    );
                } else {
                    throw new IllegalArgumentException("Activation purpose must be CHAT or EMBEDDING");
                }
                return findById(id).orElseThrow(() -> storage(
                    "llm_provider.not_found",
                    "LLM provider '" + id + "' does not exist",
                    null
                ));
            });
        } catch (DataIntegrityViolationException exception) {
            throw activationConflict(exception);
        } catch (DataAccessException exception) {
            throw storage("llm_provider.storage_write_failed", "Unable to activate LLM provider in PostgreSQL", exception);
        }
    }

    @Override
    public void activateFallback(LlmProviderPurpose purpose, Instant updatedAt) {
        try {
            if (purpose == LlmProviderPurpose.CHAT) {
                jdbcTemplate.update(
                    "UPDATE llm_provider_configs SET active_chat = false, updated_at = ? WHERE active_chat = true",
                    Timestamp.from(updatedAt)
                );
            } else if (purpose == LlmProviderPurpose.EMBEDDING) {
                jdbcTemplate.update(
                    "UPDATE llm_provider_configs SET active_embedding = false, updated_at = ? WHERE active_embedding = true",
                    Timestamp.from(updatedAt)
                );
            } else {
                throw new IllegalArgumentException("Activation purpose must be CHAT or EMBEDDING");
            }
        } catch (DataAccessException exception) {
            throw storage("llm_provider.storage_write_failed", "Unable to activate LLM provider fallback in PostgreSQL", exception);
        }
    }

    @Override
    public LlmProviderConfig updateProbeResult(
        UUID id,
        LlmProviderStatus status,
        Instant checkedAt,
        Instant lastSuccessfulProbeAt,
        String errorCode,
        String errorMessage
    ) {
        try {
            jdbcTemplate.update(
                """
                    UPDATE llm_provider_configs
                    SET status = ?,
                        last_probe_at = ?,
                        last_successful_probe_at = COALESCE(?, last_successful_probe_at),
                        last_error_code = ?,
                        last_error_message = ?,
                        updated_at = ?
                    WHERE id = ?
                    """,
                status.name(),
                Timestamp.from(checkedAt),
                toTimestamp(lastSuccessfulProbeAt),
                errorCode,
                errorMessage,
                Timestamp.from(checkedAt),
                id
            );
            return findById(id).orElseThrow(() -> storage(
                "llm_provider.not_found",
                "LLM provider '" + id + "' does not exist",
                null
            ));
        } catch (DataAccessException exception) {
            throw storage("llm_provider.storage_write_failed", "Unable to update LLM provider probe status in PostgreSQL", exception);
        }
    }

    private Optional<LlmProviderConfig> findActive(String columnName) {
        try {
            List<LlmProviderConfig> rows = jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM llm_provider_configs WHERE " + columnName + " = true LIMIT 1",
                ROW_MAPPER
            );
            return rows.stream().findFirst();
        } catch (DataAccessException exception) {
            throw storage("llm_provider.storage_read_failed", "Unable to read active LLM provider from PostgreSQL", exception);
        }
    }

    private static LlmProviderConfig mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new LlmProviderConfig(
            resultSet.getObject("id", UUID.class),
            resultSet.getString("name"),
            LlmProviderType.valueOf(resultSet.getString("provider_type")),
            LlmProviderPurpose.valueOf(resultSet.getString("purpose")),
            resultSet.getString("base_url"),
            resultSet.getString("api_key_ciphertext"),
            resultSet.getString("auth_header_name"),
            resultSet.getString("auth_scheme"),
            resultSet.getString("chat_completions_path"),
            resultSet.getString("models_path"),
            resultSet.getString("embeddings_path"),
            resultSet.getString("default_model"),
            resultSet.getString("embedding_model"),
            resultSet.getDouble("temperature"),
            resultSet.getInt("timeout_seconds"),
            getInteger(resultSet, "expected_embedding_dimension"),
            resultSet.getBoolean("active_chat"),
            resultSet.getBoolean("active_embedding"),
            LlmProviderStatus.valueOf(resultSet.getString("status")),
            toInstant(resultSet.getTimestamp("last_probe_at")),
            toInstant(resultSet.getTimestamp("last_successful_probe_at")),
            resultSet.getString("last_error_code"),
            resultSet.getString("last_error_message"),
            toInstant(resultSet.getTimestamp("created_at")),
            toInstant(resultSet.getTimestamp("updated_at"))
        );
    }

    private static Integer getInteger(ResultSet resultSet, String columnName) throws SQLException {
        int value = resultSet.getInt(columnName);
        return resultSet.wasNull() ? null : value;
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static StorageException storage(String code, String message, Throwable cause) {
        return new StorageException(ErrorType.STORAGE_FAILURE, code, message, cause);
    }

    private static ApplicationException activationConflict(Throwable cause) {
        return new ApplicationException(
            ErrorType.CONFLICT,
            "llm_provider.activation_conflict",
            "Concurrent LLM provider activation changed the active provider; retry the request",
            cause
        );
    }
}
