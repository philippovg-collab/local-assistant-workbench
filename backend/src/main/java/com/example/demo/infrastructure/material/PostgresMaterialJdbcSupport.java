package com.example.demo.infrastructure.material;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

abstract class PostgresMaterialJdbcSupport {

    protected final JdbcTemplate jdbcTemplate;
    protected final TransactionTemplate transactionTemplate;
    protected final PostgresMaterialFilterSqlBuilder filterSqlBuilder;
    protected final PostgresMaterialTagDao tagDao;
    protected final PostgresMaterialChunkDao chunkDao;
    protected final PostgresMaterialRecordDao recordDao;
    protected final PostgresMaterialRetrievalSearchDao retrievalSearchDao;

    PostgresMaterialJdbcSupport(
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.filterSqlBuilder = new PostgresMaterialFilterSqlBuilder();
        this.tagDao = new PostgresMaterialTagDao(jdbcTemplate);
        this.chunkDao = new PostgresMaterialChunkDao(jdbcTemplate);
        this.recordDao = new PostgresMaterialRecordDao(jdbcTemplate, tagDao);
        this.retrievalSearchDao = new PostgresMaterialRetrievalSearchDao(jdbcTemplate, filterSqlBuilder);
    }

    static String lowerCase(String value) {
        return value == null ? null : value.toLowerCase(java.util.Locale.ROOT);
    }

    static void bindTextArray(PreparedStatement preparedStatement, int parameterIndex, List<String> values)
        throws SQLException {
        if (values == null || values.isEmpty()) {
            preparedStatement.setNull(parameterIndex, Types.ARRAY);
            return;
        }

        preparedStatement.setArray(
            parameterIndex,
            preparedStatement.getConnection().createArrayOf("text", values.toArray(String[]::new))
        );
    }

    static void bindUuidArray(PreparedStatement preparedStatement, int parameterIndex, UUID[] values) throws SQLException {
        if (values == null) {
            preparedStatement.setNull(parameterIndex, Types.ARRAY);
            return;
        }

        preparedStatement.setArray(parameterIndex, preparedStatement.getConnection().createArrayOf("uuid", values));
    }

    static UUID[] toUuidArray(Set<String> allowedMaterialIds) {
        if (allowedMaterialIds == null) {
            return null;
        }
        if (allowedMaterialIds.isEmpty()) {
            return new UUID[0];
        }
        return allowedMaterialIds.stream().map(UUID::fromString).toArray(UUID[]::new);
    }

    static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? Instant.now() : timestamp.toInstant();
    }

    static Instant toInstantOrNull(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
