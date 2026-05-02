package com.example.demo.infrastructure.material;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.model.DocumentStatus;
import com.example.demo.model.DocumentType;
import com.example.demo.model.KnowledgeDocumentClass;
import com.example.demo.model.KnowledgeScope;
import com.example.demo.model.MaterialLanguageCode;
import com.example.demo.model.RetrievalFilters;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PostgresMaterialRetrievalScopeSqlBuilderTest {

    private final PostgresMaterialFilterSqlBuilder builder = new PostgresMaterialFilterSqlBuilder();

    @Test
    void emptyScopeProducesEmptySqlContract() {
        PostgresMaterialFilterSqlBuilder.RetrievalScopeSql sql = builder.buildRetrievalScopeSql(
            KnowledgeScope.empty(),
            null,
            null
        );

        assertEquals("", sql.sql());
        assertEquals(List.of(), sql.documentClasses());
        assertEquals(List.of(), sql.projectKeys());
        assertEquals(List.of(), sql.tags());
    }

    @Test
    void scopeFieldsAreNormalizedAndKeptInDedicatedBuckets() {
        KnowledgeScope scope = new KnowledgeScope(
            List.of("preset-a"),
            List.of("facet-a"),
            List.of(KnowledgeDocumentClass.REGULATIONS),
            List.of(DocumentType.POLICY),
            List.of(DocumentStatus.DRAFT),
            List.of("Project-A"),
            "DOC-77",
            List.of(MaterialLanguageCode.RU),
            List.of("Dispatch"),
            "North Workspace",
            LocalDate.parse("2026-01-01"),
            LocalDate.parse("2026-02-01"),
            LocalDate.parse("2026-12-01"),
            LocalDate.parse("2026-12-31"),
            false
        );
        Instant after = Instant.parse("2026-04-01T00:00:00Z");
        Instant before = Instant.parse("2026-05-01T00:00:00Z");

        PostgresMaterialFilterSqlBuilder.RetrievalScopeSql sql = builder.buildRetrievalScopeSql(scope, after, before);

        assertEquals(List.of(KnowledgeDocumentClass.REGULATIONS.name()), sql.documentClasses());
        assertEquals(List.of(DocumentType.POLICY.name()), sql.documentTypes());
        assertEquals(List.of(DocumentStatus.DRAFT.name()), sql.documentStatuses());
        assertEquals(List.of("project-a"), sql.projectKeys());
        assertEquals("doc-77", sql.documentNumber());
        assertEquals(List.of(MaterialLanguageCode.RU.name()), sql.languageCodes());
        assertEquals(List.of("dispatch"), sql.tags());
        assertEquals("north workspace", sql.workspaceKey());
        assertEquals(after, sql.uploadedAfterInclusive());
        assertEquals(before, sql.uploadedBeforeExclusive());
        assertTrue(sql.sql().contains("m.knowledge_document_class = ANY (?)"));
        assertTrue(sql.sql().contains("m.created_at >= ?"));
    }

    @Test
    void readyPredicateUsesDefaultActiveStatusAndCurrentPeriodOnlyWhenNotExplicit() {
        String defaultPredicate = builder.retrievalReadyPredicate("m", KnowledgeScope.empty(), RetrievalFilters.empty());
        RetrievalFilters explicitFilters = new RetrievalFilters(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            List.of(),
            null,
            List.of(),
            List.of(DocumentStatus.DRAFT),
            List.of(),
            List.of(),
            LocalDate.parse("2026-01-01"),
            null,
            null,
            null
        );
        String explicitPredicate = builder.retrievalReadyPredicate("m", KnowledgeScope.empty(), explicitFilters);

        assertTrue(defaultPredicate.contains("COALESCE(m.document_status, 'ACTIVE') = 'ACTIVE'"));
        assertTrue(defaultPredicate.contains("m.period_start <= CURRENT_DATE"));
        assertTrue(defaultPredicate.contains("m.period_end >= CURRENT_DATE"));
        assertFalse(explicitPredicate.contains("COALESCE(m.document_status, 'ACTIVE') = 'ACTIVE'"));
        assertFalse(explicitPredicate.contains("m.period_start <= CURRENT_DATE"));
        assertFalse(explicitPredicate.contains("m.period_end >= CURRENT_DATE"));
    }
}
