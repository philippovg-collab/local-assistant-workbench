package com.example.demo.infrastructure.material;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.model.DocumentStatus;
import com.example.demo.model.DocumentType;
import com.example.demo.model.MaterialLanguageCode;
import com.example.demo.model.RetrievalFilters;
import com.example.demo.model.SourceTrustLevel;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PostgresMaterialFilterSqlBuilderTest {

    private final PostgresMaterialFilterSqlBuilder builder = new PostgresMaterialFilterSqlBuilder();

    @Test
    void emptyFiltersProduceEmptySqlContract() {
        PostgresMaterialFilterSqlBuilder.SearchFilterSql sql = builder.buildSearchFilterSql(RetrievalFilters.empty());

        assertEquals("", sql.sql());
        assertEquals(List.of(), sql.documentTypes());
        assertEquals(List.of(), sql.projectKeys());
        assertEquals(List.of(), sql.tags());
    }

    @Test
    void freeTextProjectMatchesProjectNameOrProjectKey() {
        RetrievalFilters filters = new RetrievalFilters(
            null,
            null,
            null,
            null,
            "North Upgrade",
            null,
            null,
            null,
            List.of(),
            null
        );

        PostgresMaterialFilterSqlBuilder.SearchFilterSql sql = builder.buildSearchFilterSql(filters);

        assertTrue(sql.sql().contains("LOWER(m.project_name) = ? OR m.project_key = ?"));
        assertEquals("north upgrade", sql.project());
    }

    @Test
    void structuredFilterFieldsRemainSeparateAndNormalized() {
        RetrievalFilters filters = new RetrievalFilters(
            "DOC-42",
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            "Grid Ops",
            "North Upgrade",
            "KazEnergy",
            "ACTIVE",
            "ru",
            List.of("Policy", "Operations"),
            SourceTrustLevel.MEDIUM,
            List.of(DocumentType.POLICY),
            List.of(DocumentStatus.ACTIVE),
            List.of("Project-A"),
            List.of(MaterialLanguageCode.RU),
            LocalDate.parse("2026-01-01"),
            LocalDate.parse("2026-02-01"),
            LocalDate.parse("2026-12-01"),
            LocalDate.parse("2026-12-31")
        );

        PostgresMaterialFilterSqlBuilder.SearchFilterSql sql = builder.buildSearchFilterSql(filters);

        assertEquals("doc-42", sql.documentNumber());
        assertEquals(List.of(DocumentType.POLICY.name()), sql.documentTypes());
        assertEquals(List.of(DocumentStatus.ACTIVE.name()), sql.documentStatuses());
        assertEquals(List.of("project-a"), sql.projectKeys());
        assertEquals(List.of(MaterialLanguageCode.RU.name()), sql.languageCodes());
        assertEquals(List.of("policy", "operations"), sql.tags());
        assertEquals(List.of(SourceTrustLevel.HIGH.name(), SourceTrustLevel.MEDIUM.name()), sql.sourceTrustLevels());
        assertEquals(LocalDate.parse("2026-04-01"), sql.documentDateFrom());
        assertEquals(LocalDate.parse("2026-04-30"), sql.documentDateTo());
        assertEquals(LocalDate.parse("2026-01-01"), sql.periodStartFrom());
        assertEquals(LocalDate.parse("2026-02-01"), sql.periodStartTo());
        assertEquals(LocalDate.parse("2026-12-01"), sql.periodEndFrom());
        assertEquals(LocalDate.parse("2026-12-31"), sql.periodEndTo());
        assertTrue(sql.sql().contains("m.document_date >= ?"));
        assertTrue(sql.sql().contains("m.period_start >= ?"));
        assertTrue(sql.sql().contains("m.period_end <= ?"));
        assertTrue(sql.sql().contains("LOWER(m.business_status) = ?"));
        assertTrue(sql.sql().contains("m.project_key = ANY (?)"));
        assertTrue(sql.sql().contains("m.language_code = ANY (?)"));
        assertTrue(sql.sql().contains("m.source_trust = ANY (?)"));
        assertTrue(sql.sql().contains("m.language_code = ?"));
    }

    @Test
    void invalidLegacyLanguageStillProducesNoMatchPredicate() {
        RetrievalFilters filters = new RetrievalFilters(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "esperanto",
            List.of(),
            null
        );

        PostgresMaterialFilterSqlBuilder.SearchFilterSql sql = builder.buildSearchFilterSql(filters);

        assertEquals("__INVALID_LANGUAGE__", sql.language());
        assertTrue(sql.sql().contains("m.language_code = ?"));
    }
}
