package com.example.demo.llmprovider;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class EmbeddingDimensionInspector {

    private static final Pattern VECTOR_TYPE = Pattern.compile("^vector\\((\\d+)\\)$");

    private final JdbcTemplate jdbcTemplate;

    public EmbeddingDimensionInspector(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Integer materialChunkEmbeddingDimension() {
        String vectorType = jdbcTemplate.query(
            """
                SELECT format_type(a.atttypid, a.atttypmod) AS vector_type
                FROM pg_attribute a
                JOIN pg_class c ON c.oid = a.attrelid
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = current_schema()
                  AND c.relname = 'material_chunks'
                  AND a.attname = 'embedding'
                  AND NOT a.attisdropped
                LIMIT 1
                """,
            resultSet -> resultSet.next() ? resultSet.getString("vector_type") : null
        );
        if (vectorType == null || vectorType.isBlank()) {
            return null;
        }
        Matcher matcher = VECTOR_TYPE.matcher(vectorType);
        return matcher.matches() ? Integer.parseInt(matcher.group(1)) : null;
    }
}
