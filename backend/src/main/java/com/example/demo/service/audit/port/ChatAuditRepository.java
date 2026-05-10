package com.example.demo.service.audit.port;

import com.example.demo.service.audit.StoredChatAuditRunRecord;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ChatAuditRepository {

    List<StoredChatAuditRunRecord> findAll(int limit);

    Optional<StoredChatAuditRunRecord> findById(String id);

    int deleteRunsOlderThan(Instant cutoff);
}
