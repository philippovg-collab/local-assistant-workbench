package com.example.demo.service.material.port;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.model.DocumentBlockType;
import com.example.demo.service.material.LexicalProviderType;
import com.example.demo.service.material.MaterialChunkSearchMatch;
import com.example.demo.service.material.MaterialSearchScope;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SearchProviderScopeDefaultsTest {

    @Test
    void lexicalDefaultTreatsNullLegacyIdsAsUnscopedAndEmptyIdsAsNoResults() {
        LexicalSearchProvider provider = new LexicalSearchProvider() {
            @Override
            public LexicalProviderType type() {
                return LexicalProviderType.POSTGRES;
            }

            @Override
            public List<MaterialChunkSearchMatch> search(String query, int limit) {
                return List.of(match("material-a"));
            }
        };

        assertEquals(1, provider.search("pricing", 5, (Set<String>) null).size());
        assertTrue(provider.search("pricing", 5, Set.of()).isEmpty());
    }

    @Test
    void semanticDefaultTreatsNullLegacyIdsAsUnscopedAndEmptyIdsAsNoResults() {
        SemanticSearchRepository repository = new SemanticSearchRepository() {
            @Override
            public List<MaterialChunkSearchMatch> searchSemantic(float[] queryEmbedding, int limit) {
                return List.of(match("material-a"));
            }
        };

        assertEquals(1, repository.searchSemantic(new float[] {1.0f}, 5, (Set<String>) null).size());
        assertTrue(repository.searchSemantic(new float[] {1.0f}, 5, Set.of()).isEmpty());
    }

    @Test
    void defaultsDoNotSilentlyExpandFilteredScope() {
        LexicalSearchProvider provider = new LexicalSearchProvider() {
            @Override
            public LexicalProviderType type() {
                return LexicalProviderType.POSTGRES;
            }

            @Override
            public List<MaterialChunkSearchMatch> search(String query, int limit) {
                return List.of(match("material-a"));
            }
        };

        assertThrows(UnsupportedOperationException.class, () ->
            provider.search("pricing", 5, MaterialSearchScope.filtered(null, null, null, null))
        );
    }

    private static MaterialChunkSearchMatch match(String materialId) {
        return new MaterialChunkSearchMatch(
            materialId,
            0,
            "Title",
            "content",
            null,
            "direct-text",
            false,
            DocumentBlockType.NARRATIVE,
            null,
            1.0d
        );
    }
}
