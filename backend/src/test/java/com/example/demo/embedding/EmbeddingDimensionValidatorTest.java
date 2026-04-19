package com.example.demo.embedding;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.example.demo.config.EmbeddingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

class EmbeddingDimensionValidatorTest {

    @Test
    void passesWhenConfiguredDimensionMatchesPgvectorColumn() {
        JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<ResultSetExtractor<String>>any()))
            .thenReturn("vector(768)");
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setExpectedDimension(768);
        EmbeddingDimensionValidator validator = new EmbeddingDimensionValidator(jdbcTemplate, properties);

        assertDoesNotThrow(validator::validateMaterialChunkEmbeddingDimension);
    }

    @Test
    void failsStartupWhenConfiguredDimensionDiffersFromPgvectorColumn() {
        JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<ResultSetExtractor<String>>any()))
            .thenReturn("vector(1536)");
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setExpectedDimension(768);
        EmbeddingDimensionValidator validator = new EmbeddingDimensionValidator(jdbcTemplate, properties);

        assertThrows(IllegalStateException.class, validator::validateMaterialChunkEmbeddingDimension);
    }
}
