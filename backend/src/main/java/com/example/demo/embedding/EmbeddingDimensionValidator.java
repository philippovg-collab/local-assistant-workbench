package com.example.demo.embedding;

import com.example.demo.config.EmbeddingProperties;
import com.example.demo.llmprovider.ActiveLlmProviderResolver;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class EmbeddingDimensionValidator {

    private static final Pattern VECTOR_TYPE = Pattern.compile("^vector\\((\\d+)\\)$");

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingProperties embeddingProperties;
    private final ActiveLlmProviderResolver activeProviderResolver;

    @Autowired
    public EmbeddingDimensionValidator(
        JdbcTemplate jdbcTemplate,
        EmbeddingProperties embeddingProperties,
        ActiveLlmProviderResolver activeProviderResolver
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingProperties = embeddingProperties;
        this.activeProviderResolver = activeProviderResolver;
    }

    public EmbeddingDimensionValidator(JdbcTemplate jdbcTemplate, EmbeddingProperties embeddingProperties) {
        this(jdbcTemplate, embeddingProperties, null);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void validateMaterialChunkEmbeddingDimension() {
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
            return;
        }

        Matcher matcher = VECTOR_TYPE.matcher(vectorType);
        if (!matcher.matches()) {
            throw new IllegalStateException(
                "Unsupported material_chunks.embedding type '" + vectorType + "'. Expected pgvector type vector(n)."
            );
        }

        int databaseDimension = Integer.parseInt(matcher.group(1));
        int expectedDimension = Math.max(1, expectedDimension());
        if (databaseDimension != expectedDimension) {
            throw new IllegalStateException(
                "Embedding dimension mismatch: database material_chunks.embedding is vector(" + databaseDimension
                    + ") but app.embeddings.expected-dimension is " + expectedDimension
                    + ". Reconfigure the embedding model or migrate the pgvector column before startup."
            );
        }
    }

    private int expectedDimension() {
        Integer activeDimension = activeProviderResolver == null
            ? null
            : activeProviderResolver.resolveEmbeddingProvider().expectedEmbeddingDimension();
        return activeDimension == null ? embeddingProperties.getExpectedDimension() : activeDimension;
    }
}
