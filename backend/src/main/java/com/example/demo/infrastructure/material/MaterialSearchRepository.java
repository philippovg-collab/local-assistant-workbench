package com.example.demo.infrastructure.material;

import java.util.List;

public interface MaterialSearchRepository {

    List<MaterialChunkSearchMatch> searchSemantic(float[] queryEmbedding, int limit);

    List<MaterialChunkSearchMatch> searchLexical(String query, int limit);
}
