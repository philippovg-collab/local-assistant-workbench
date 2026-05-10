package com.example.demo.service.rag.port;

import com.example.demo.service.rag.StoredRagProjectSummary;
import java.util.List;
import java.util.Optional;

public interface RagProjectReadRepository {

    List<StoredRagProjectSummary> listSummariesWithCounts(boolean activeOnly);

    Optional<StoredRagProjectSummary> findSummaryByKey(String key);
}
