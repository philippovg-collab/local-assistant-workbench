package com.example.demo.service.material;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.model.KnowledgeDocumentClass;
import com.example.demo.model.KnowledgeScope;
import com.example.demo.model.RetrievalFilters;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MaterialSearchScopeTest {

    @Test
    void normalizesLegacyMaterialIds() {
        assertEquals(MaterialSearchScope.Mode.UNSCOPED, MaterialSearchScope.fromLegacyMaterialIds(null).mode());
        assertEquals(MaterialSearchScope.Mode.NO_RESULTS, MaterialSearchScope.fromLegacyMaterialIds(Set.of()).mode());

        MaterialSearchScope scope = MaterialSearchScope.fromLegacyMaterialIds(Set.of(" material-a "));

        assertEquals(MaterialSearchScope.Mode.MATERIAL_IDS, scope.mode());
        assertEquals(Set.of("material-a"), scope.materialIds());
    }

    @Test
    void emptyRetrievalCriteriaStayUnscoped() {
        MaterialSearchScope scope = MaterialSearchScope.fromRetrievalCriteria(
            KnowledgeScope.empty(),
            RetrievalFilters.empty(),
            null,
            null
        );

        assertEquals(MaterialSearchScope.Mode.UNSCOPED, scope.mode());
    }

    @Test
    void nonEmptyRetrievalCriteriaBecomeFilteredScope() {
        Instant uploadedAfter = Instant.parse("2026-04-17T00:00:00Z");
        Instant uploadedBefore = Instant.parse("2026-04-18T00:00:00Z");

        MaterialSearchScope scope = MaterialSearchScope.fromRetrievalCriteria(
            new KnowledgeScope(List.of(), List.of(KnowledgeDocumentClass.CONTRACTS), List.of(), "north-upgrade", false),
            RetrievalFilters.empty(),
            uploadedAfter,
            uploadedBefore
        );

        assertEquals(MaterialSearchScope.Mode.FILTERED, scope.mode());
        assertEquals(List.of(KnowledgeDocumentClass.CONTRACTS), scope.knowledgeScope().documentClasses());
        assertEquals("north-upgrade", scope.knowledgeScope().workspaceKey());
        assertEquals(uploadedAfter, scope.uploadedAfterInclusive());
        assertEquals(uploadedBefore, scope.uploadedBeforeExclusive());
        assertTrue(scope.materialIds().isEmpty());
    }
}
